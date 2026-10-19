# Senior Engineer Interview Story: The RFQ Supplier Matching & Coroutine Fan-Out Engine

This guide provides the **exact narrative, business context, and STAR-framework story** to deliver during your technical interview ("Show & Tell", Architecture Deep Dive, or System Design).

---

## 1. The 30-Second Elevator Pitch (Quick Executive Summary)

> *"In our B2B marketplace at Visable (powering 'Wer liefert was' and EUROPAGES), when a buyer submits a Request for Quotation (RFQ), we match it with relevant suppliers.  
> The core technical challenge was that an ML recommendation event would return candidate suppliers, but to qualify and enrich each supplier, our service had to query multiple downstream microservices—supplier facts, product catalogs, and two different CRMs.  
> Done sequentially, this resulted in over **200 blocking HTTP calls taking 20+ seconds**, which was causing AWS SQS message visibility timeouts and thread exhaustion.  
> I helped re-architect this pipeline using **Kotlin Coroutines and structured concurrency**. By building a **2-tier hierarchical fan-out engine** constrained by **`Dispatchers.IO.limitedParallelism`**, we collapsed the end-to-end processing latency from **20 seconds down to ~400 milliseconds**—a 98% performance gain—while eliminating queue redeliveries and protecting downstream services."*

---

## 2. The 3-Minute STAR Interview Narrative

### S — Situation (The Business & Architectural Context)
"Visable operates two of Europe's largest B2B marketplaces: **Wer liefert was (WLW)** in DACH and **EUROPAGES** across Europe.  
Buyers visit our platform to submit high-value B2B purchase requests (RFQs)—for example, *'We need 10,000 custom CNC precision aluminum parts delivered to Munich.'*

To create liquidity in the marketplace, our **`rfq-service`** matches this buyer's request with the most capable suppliers.  
Behind the scenes, we have an asynchronous Machine Learning Recommendation Engine. When an RFQ is accepted, the ML model runs similarity scoring and publishes an event to AWS SNS/SQS saying: *'Recommendations are ready for RFQ #123 with candidate supplier IDs [A, B, C, ...]'*."

---

### T — Task (The Engineering Bottleneck)
"The raw recommendation payload only gives us basic supplier IDs and algorithm weights. Before we can present matches to the buyer, notify suppliers, or initiate conversations, our service must **enrich and qualify** the data:

1. **What is an 'Offer' in our domain?**  
   In B2B platforms like WLW and EUROPAGES, an 'Offer' is a **product catalog listing** published by a supplier (e.g., *'5-Axis CNC Precision Aluminum Milling'*, *'Industrial Fasteners'*). A single supplier profile has 10 to 50 published offers.
2. **Why do we need to fetch all of them?**
   - **Explain the Match:** We aggregate product names, keywords, and CPV (Common Procurement Vocabulary) taxonomy codes to tell the buyer *why* this supplier was recommended.
   - **Render the UI Recommendation Card:** In the buyer's portal, we display rich supplier cards showing their top matching products, certifications, and capabilities. Without the offers, the buyer would only see an empty company name.
   - **Multilingual Localization:** We resolve localized product titles on the fly—German (`de`) for WLW and English (`en`) for EUROPAGES.
   - **Live Filtering:** We filter out products that were recently deleted or unpublished by the supplier.
   - **Source of Truth:** In our microservice architecture, `supplier-offers-service` is the sole source of truth for product catalogs. Duplicating catalog tables in `rfq-service` would cause massive cache-drift and synchronization bugs.

For an average RFQ matched with **20 candidate suppliers**, each having **10 product offers**, that meant **over 200 downstream HTTP network calls** across `supplier-offers-service`, `supplier-facts-service`, `users-service`, and `SugarCRM`.

Because the legacy code executed these calls sequentially on standard blocking threads:
- **Latency was catastrophic:** At ~100ms per call, $200 \times 100\text{ms} = \mathbf{20\text{ seconds}}$.
- **The SQS Visibility Timeout Disaster:**  
  Our AWS SQS queue had a standard **15-second Visibility Timeout**. Because processing took 20 seconds, SQS assumed Worker 1 had crashed or run out of memory. SQS made the message visible again, causing Worker 2 (another pod) to pick up the exact same message.  
  This triggered **cascading duplicate processing**: suppliers received 3 to 5 duplicate email/SMS notifications, and messages were repeatedly poisoned until they landed in the Dead-Letter Queue (DLQ).
- **Resource Saturation:** Under peak RFQ traffic, our Tomcat thread pool became starved waiting on blocking I/O."

---

### A — Action (What I Implemented)
"To solve this without rewriting the entire service into reactive callback hell (like WebFlux Mono/Flux), we leveraged **Kotlin Coroutines and structured concurrency**:

1. **Two-Tier Hierarchical Coroutine Fan-Out:**
   - **Tier 1 (Supplier Level):** Inside `MatchingRecommendationService`, we wrapped the 20 candidate suppliers in a `coroutineScope` and launched an `async` job for each supplier.
   - **Tier 2 (Offer Level):** Inside each supplier's worker (`SupplierAndProductsDetailsFinder`), we extracted all product offers and launched a nested `async` fan-out to fetch offer details concurrently.

2. **Bulkhead Pattern via `limitedParallelism`:**
   - Launching 200 unbounded coroutines would have DDoSed downstream microservices (`supplier-offers-service`).
   - We implemented the Bulkhead pattern using `Dispatchers.IO.limitedParallelism(12)`. This guaranteed that no matter how many suppliers were being processed, our service never opened more than 12 concurrent HTTP connections to any single downstream service.

3. **Fault-Tolerant Side Effects with `supervisorScope`:**
   - In our matching pipeline, we have primary work (saving the match to PostgreSQL) and secondary work (dispatching webhook notifications).
   - We isolated them using `supervisorScope`. If an outbound notification failed, it did not cancel or abort the primary matching record in the database.

4. **Preserving Distributed Tracing (`MDCContext`):**
   - Because coroutines switch threads when they suspend, SLF4J MDC `ThreadLocal` trace IDs were getting wiped out.
   - We configured our coroutine scopes with `MDCContext()` from `kotlinx-coroutines-slf4j`, ensuring that Datadog and Grafana trace IDs seamlessly propagated across all asynchronous child coroutines."

---

### R — Result (The Business & Technical Impact)
"The results were transformative:
1. **98% Latency Reduction:** End-to-end recommendation enrichment dropped from **20 seconds down to ~400 milliseconds**.
2. **Eliminated Queue Failures:** SQS message processing was well within visibility timeouts, eliminating message redeliveries and duplicate notifications.
3. **Clean, Maintainable Code:** By using coroutines (`async`/`awaitAll`), the business logic remained sequential and readable, with standard `try/catch` and `runCatching` error handling, completely avoiding the readability drawbacks of reactive streams.
4. **Zero Extra Infrastructure Costs:** We achieved a 40x throughput boost on the exact same CPU and memory footprints in Kubernetes."

---

## 3. How to Handle Interviewer Follow-Up Questions

### Follow-Up 1: *"Why did you use Coroutines instead of a standard `ExecutorService` thread pool?"*
> **Answer:**  
> "A traditional Java thread pool allocates a native OS thread per task, each consuming ~1MB of memory for its call stack. Having 200 concurrent threads would consume hundreds of megabytes of RAM and cause severe CPU context-switching overhead in the Linux kernel.  
> In contrast, Kotlin Coroutines are user-space abstractions that cost only ~250 bytes of heap memory each. When an HTTP call is waiting for bytes over the socket, the coroutine **suspends** and releases its underlying worker thread to serve other requests. That allows us to multiplex hundreds of concurrent outbound operations across just a handful of threads."

---

### Follow-Up 2: *"Why not use Spring WebFlux with Project Reactor (`Mono`/`Flux`)?"*
> **Answer:**  
> "Our service uses Spring MVC, JPA/Hibernate, and relational PostgreSQL databases. A full WebFlux rewrite would have required replacing our entire persistence layer with R2DBC, creating huge migration risk.  
> Furthermore, Project Reactor code tends to suffer from callback complexity—chains of `.flatMap()`, `.zip()`, and tricky error handling that make business logic harder to debug and maintain.  
> Kotlin Coroutines gave us the best of both worlds: non-blocking, reactive-level performance under the hood, while preserving clean, sequential imperative syntax and standard `try/catch` error handling."

---

### Follow-Up 3: *"How did you prevent overwhelming the downstream microservices during a sudden traffic spike?"*
> **Answer:**  
> "We applied the Bulkhead pattern using `Dispatchers.IO.limitedParallelism(12)`.  
> By creating a limited dispatcher view on `Dispatchers.IO`, Kotlin guarantees that at most 12 threads will ever be used concurrently for calls to `supplier-offers-service`. Any additional requests queue up in memory as suspended coroutines rather than bombarding downstream services with hundreds of open TCP sockets."

---

### Follow-Up 4: *"What happens if one of the 10 offer API calls fails for a supplier? Does the entire RFQ match fail?"*
> **Answer:**  
> "No, we designed the fan-out with resilience. Inside the coroutine mapping, each individual offer call is wrapped in `runCatching` or null-safe mapping (`getOfferData(offer.id)?.let { ... }`).  
> If an individual offer endpoint fails or returns a 404, we log a warning, filter out the null result, and continue enriching the remaining offers. The supplier is still matched with whatever valid product data was retrieved."

---

### Follow-Up 5: *"Why does the service need to fetch all supplier offers live in the first place? Why not store them locally in `rfq-service` or read from a shared database?"*
> **Answer:**  
> "There are three primary architectural reasons:  
> 1. **Domain Boundary & Single Source of Truth:** In our microservice architecture, `supplier-offers-service` owns the complete product catalog lifecycle (supplier edits, CPV taxonomy tagging, unpublished state). Storing a local copy in `rfq-service` would require complex change-data-capture (CDC) pipelines and inevitably lead to stale product recommendations.  
> 2. **Real-Time Data Validity & Localization:** B2B suppliers frequently archive products or pause listings. Querying live guarantees we never recommend deleted or out-of-stock capabilities. It also allows us to resolve localized product attributes on the fly (German for WLW, English for EUROPAGES).  
> 3. **Coroutines Eliminated the Cache Compromise:** In distributed systems, teams often cache or duplicate data locally purely to avoid high network latency. Because our non-blocking coroutine fan-out reduced the 200-call network overhead from 20 seconds down to ~400ms, we achieved sub-second performance while maintaining strict microservice decoupling and real-time data consistency."

---

## 4. Cheat Sheet: Key Architecture Terms to Mention

| Concept | Why Mention It |
| :--- | :--- |
| **Two-Tier Coroutine Fan-Out** | Demonstrates ability to architect complex, nested parallel data retrieval. |
| **`Dispatchers.IO.limitedParallelism`** | Proves you design with **Bulkhead resilience** and don't blindly DDoS downstream systems. |
| **`supervisorScope`** | Shows deep understanding of structured concurrency and fault isolation. |
| **`MDCContext()`** | Demonstrates production-grade observability and distributed tracing expertise. |
| **SQS Visibility Timeout** | Bridges application code to real-world cloud infrastructure and message queues. |
| **Continuation Passing Style (CPS)** | Shows deep JVM understanding of how `suspend` avoids OS thread parking. |
