# Debugging & Monitoring Kotlin Coroutines: Tracing, Observability & Leak Detection

This guide explains how to debug and monitor asynchronous Kotlin Coroutines in production Spring Boot applications, how **OpenTelemetry / Micrometer (`TracingConfig`)** preserves trace context across suspension points, and how to detect, diagnose, and prevent **coroutine memory leaks**.

---

## 1. Why Debugging Coroutines is Fundamentally Different

In traditional Spring MVC (Servlet model), request execution is **thread-bound**:
```
Thread A: [Read Request] -> [Query DB] -> [Call External API] -> [Write Response]
```
* Every log line has the thread name `[http-nio-8080-exec-1]`.
* Diagnostics rely on `Thread.currentThread()`, `Thread.getAllStackTraces()`, and `ThreadLocal` variables (SLF4J MDC, Spring SecurityContext).

With **Kotlin Coroutines and Non-Blocking I/O**, execution is **thread-multiplexed**:
```
Thread 1: [Async Job 1 starts] -> [awaitSingleOrNull() suspends]
Thread 2:                                                      -> [Resumes Job 1] -> [awaitAll() suspends]
Thread 1:                                                                                                 -> [Resumes Job 1]
```
* **Loss of Thread Stack Traces:** Standard JVM thread dumps (`jstack`, `kill -3`) only show worker threads parked in `ForkJoinPool` or `Dispatchers.IO`. They do **not** reveal where suspended coroutines are waiting.
* **`ThreadLocal` Context Loss & Bleeding:** When a coroutine suspends on Thread 1 and resumes on Thread 2, standard `ThreadLocal` values (MDC Trace IDs, security tokens) do not travel with it automatically, or worse, pollute Thread 1 when it serves another request.
* **Invisible Memory Leaks:** A suspended coroutine is an object on the JVM heap. If it hangs waiting for a network socket without a timeout, it holds all objects referenced in its closure indefinitely.

---

## 2. How `TracingConfig` & Distributed Tracing Help

In our codebase, tracing is configured in [`TracingConfig.kt`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/src/main/kotlin/com/example/demo/infrastructure/config/TracingConfig.kt):

```kotlin
@Configuration
class TracingConfig(
    @param:Value("\${management.otlp.tracing.endpoint:http://localhost:4318/v1/traces}")
    private val otlpEndpoint: String,
) {
    @Bean
    @ConditionalOnProperty(prefix = "management.otlp.tracing", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun otlpHttpSpanExporter(): OtlpHttpSpanExporter =
        OtlpHttpSpanExporter
            .builder()
            .setEndpoint(otlpEndpoint)
            .build()
}
```

Together with `io.micrometer:micrometer-tracing-bridge-otel` and `kotlinx-coroutines-slf4j`, this configuration provides end-to-end observability across asynchronous boundaries.

### The Problem: `ThreadLocal` Bleeding
When an HTTP request arrives, Micrometer Tracing extracts the trace context (`traceId`, `spanId`) and stores it in `ThreadLocal`.
If a coroutine switches threads:
1. **Lost Context:** Thread 2 logs have no `traceId` (`[traceId=, spanId=]`), fragmenting log aggregation in Datadog/Grafana.
2. **Context Bleeding (Zombie Traces):** Thread 1 retains the old request's `traceId`. When Thread 1 picks up a completely different request, it emits logs with the previous request's trace ID!

### The Solution: Coroutine Context Propagation
To preserve tracing across coroutine suspension points, Kotlin provides `ThreadContextElement`. Specifically:
1. **`MDCContext()` (`kotlinx-coroutines-slf4j`):**
   Captures the SLF4J MDC map at coroutine launch time. Whenever the coroutine suspends, it cleans up the current thread's MDC; whenever it resumes on a new thread, it restores the MDC map onto that thread.
2. **Micrometer `asContextElement()`:**
   Attaches the active Micrometer/OpenTelemetry `Observation` or `Span` to the `CoroutineContext`.

```kotlin
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.slf4j.MDCContext

// Launching coroutines with preserved distributed tracing
async(limitedDispatcher + MDCContext() + tracer.asContextElement()) {
    // Both SLF4J logs and OpenTelemetry spans seamlessly travel across threads
    logger.info("Executing external call for refId={}", refId)
    externalClientPort.fetchEnrichmentDataAsync(refId)
}
```

### Visualizing Fan-Out in OpenTelemetry (Waterfall Spans)
With `OtlpHttpSpanExporter`, when `batchSync` runs 100 calls, OpenTelemetry and Jaeger/Tempo render a **parent-child waterfall diagram**:

```
[POST /api/v1/resources/batch-sync] ======================================= (420ms)
   |-- [Coroutine Fan-Out Root Scope] =================================== (410ms)
         |-- [HTTP GET /api/v1/enrichment/ref-1]   ======= (85ms)
         |-- [HTTP GET /api/v1/enrichment/ref-2]   ========= (110ms)
         |-- [HTTP GET /api/v1/enrichment/ref-3]   ======= (90ms)
         |-- ...
         |-- [HTTP GET /api/v1/enrichment/ref-100] ======== (95ms)
   |-- [JPA Batch saveAll (SQL INSERT)] =================== (10ms)
```

**Diagnostic Benefits:**
1. **Tail Latency Bottlenecks:** Instantly identifies if 99 calls took 50ms but 1 call took 4,500ms due to downstream queuing.
2. **Concurrency Verification:** Proves whether calls actually ran in parallel or accidentally ran sequentially.
3. **Network vs. Database Breakdown:** Distinguishes time spent waiting on external HTTP sockets vs. PostgreSQL connection acquisition and SQL execution.

---

## 3. Essential Tools for Debugging Coroutines

### Tool 1: JVM Debug Option (`-Dkotlinx.coroutines.debug`)
Add the following VM option in local development and staging:
```bash
-Dkotlinx.coroutines.debug
```
Or include the runtime dependency:
```kotlin
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug")
```

#### What It Does:
1. **Augments Thread Names:**
   Whenever a thread runs a coroutine, Kotlin modifies the thread name to include the coroutine ID and name:
   ```
   [ForkJoinPool.commonPool-worker-1 @batch-sync-ref-42#107] INFO c.e.d.a.s.ResourceApplicationService - Fetching ref-42
   ```
2. **Coroutine Stack Traces:**
   When an exception is thrown, the stack trace contains the **coroutine creation stack trace** in addition to the standard Java thread trace, answering *"Where was this coroutine launched?"*.

### Tool 2: `CoroutineName` for Human-Readable Logging
Inside `batchSync`, assign names to child coroutines:

```kotlin
async(limitedDispatcher + CoroutineName("sync-ref-$refId")) {
    logger.info("Starting enrichment call")
    // Log output automatically includes: [@sync-ref-ref-42#88]
}
```

### Tool 3: IntelliJ IDEA Coroutines Debugger
1. Run the Spring Boot application in Debug mode.
2. Open the **Debug** window and select the **Coroutines** tab.
3. The panel displays:
   * **Active Coroutines Tree:** Grouped by `Job` and `Dispatcher`.
   * **State:** `RUNNING`, `SUSPENDED`, or `CREATED`.
   * **Suspension Point:** Exact line of code where the coroutine is paused (e.g. `awaitSingleOrNull()` line 59).
   * **CoroutineContext:** Dispatcher, CoroutineName, ExceptionHandler.

---

## 4. How to Detect & Diagnose Coroutine Memory Leaks

### What is a "Coroutine Leak"?
A coroutine is a heap-allocated state machine (`DispatchedContinuation` / `JobSupport`). It captures in its closure:
* Parameters, local variables, and domain entities.
* Injected service, repository, and client instances.

If a coroutine is **suspended and never resumes or cancels**:
1. The coroutine object remains permanently referenced in memory.
2. The garbage collector (GC) cannot reclaim any object in its closure.
3. Over time, repeated leaked coroutines consume Old Gen heap space until the JVM crashes with `java.lang.OutOfMemoryError: Java heap space`.

```
Leaked Coroutine Continuation (Heap)
  └── holds Reference to 'command' (BatchSyncCommand)
  └── holds Reference to 'externalClientPort' (ExternalWebClientAdapter)
  └── holds Reference to Netty ByteBuf & SSL Buffers
```

---

### The Top 4 Coroutine Leak Anti-Patterns & Fixes

#### Anti-Pattern 1: Unbounded Scopes (`GlobalScope.launch`)
```kotlin
// DANGEROUS: Leaks if external call hangs; never bound to request lifecycle
GlobalScope.launch {
    externalClient.call()
}
```
* **Why it leaks:** `GlobalScope` has no parent job. If the operation hangs, it stays alive for the entire lifetime of the JVM.
* **Fix:** Always use **structured concurrency** via `coroutineScope { ... }` or inject a managed `CoroutineScope` tied to the Spring Bean lifecycle.

#### Anti-Pattern 2: Missing Socket / HTTP Timeouts
```kotlin
// DANGEROUS: If external server keeps TCP socket open without sending bytes, coroutine suspends FOREVER
externalApiWebClient.get()
    .uri(...)
    .retrieve()
    .bodyToMono(...)
    .awaitSingleOrNull() // HANGS INDEFINITELY
```
* **Why it leaks:** If the remote server stops responding or drops packets without sending a FIN/RST packet, the Netty channel never fires a completion event. The coroutine is suspended forever in Old Gen heap.
* **Fix:** Always enforce explicit reactive timeouts:
  ```kotlin
  .bodyToMono(ExternalApiResponse::class.java)
  .timeout(Duration.ofSeconds(5)) // Circuit breaker: throws TimeoutException after 5s
  .awaitSingleOrNull()
  ```

#### Anti-Pattern 3: Holding Database Connections Across Suspension
```kotlin
// DANGEROUS: Holding a JDBC connection across non-blocking network suspension
@Transactional
suspend fun badSync() {
    val entity = repository.findById(id) // Leases HikariCP connection
    val data = client.fetchDataAsync()    // SUSPENDS for 2000ms while HOLDING the DB connection!
    repository.save(entity)
}
```
* **Why it leaks:** Starves the HikariCP connection pool, causing other threads to throw `SQLTransientConnectionException: Connection is not available, request timed out after 30000ms`.
* **Fix:** Execute network calls outside of `@Transactional`, then batch save in a short-lived transaction.

#### Anti-Pattern 4: Un-awaited `async` Jobs
```kotlin
val deferred = async { doWork() }
// If deferred.await() is never called and doWork throws an exception,
// the exception remains unhandled and the job may hold references until scope closes.
```
* **Fix:** Always pair `async` with `awaitAll()` or use `launch` for fire-and-forget background tasks.

---

## 5. Detecting Leaks in Production

### Method 1: Programmatic Coroutine Dumps via Spring Boot Actuator
We can expose a custom Actuator endpoint that dumps all active and suspended coroutines in memory using `DebugProbes`:

```kotlin
package com.example.demo.infrastructure.adapter.`in`.rest

import kotlinx.coroutines.debug.DebugProbes
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.stereotype.Component

@Component
@Endpoint(id = "coroutines")
class CoroutinesActuatorEndpoint {

    init {
        // Must be installed once at startup
        DebugProbes.install()
    }

    @ReadOperation
    fun dumpCoroutines(): String {
        val out = java.io.ByteArrayOutputStream()
        java.io.PrintStream(out).use { printStream ->
            DebugProbes.dumpCoroutines(printStream)
        }
        return out.toString(Charsets.UTF_8)
    }
}
```

When calling `GET /actuator/coroutines`:
```
Coroutine "sync-ref-ref-42#88":BlockingCoroutine{Active}@5c3b9b47, state: SUSPENDED
    at kotlinx.coroutines.reactor.MonoKt.awaitSingleOrNull(Mono.kt:142)
    at com.example.demo.infrastructure.adapter.out.client.ExternalWebClientAdapter.fetchEnrichmentDataAsync(ExternalWebClientAdapter.kt:59)
    at com.example.demo.application.service.ResourceApplicationService$batchSync$2$deferredResources$1$1.invokeSuspend(ResourceApplicationService.kt:107)
```
**Diagnostic Signal:** If you see coroutines with `state: SUSPENDED` created hours ago, you have a confirmed leak at the reported line of code!

---

### Method 2: JVM Heap Dump Analysis (MAT / JProfiler)
If JVM memory grows steadily without dropping after Major GC:
1. Capture a heap dump:
   ```bash
   jcmd <PID> GC.heap_dump /tmp/heap_dump.hprof
   ```
2. Open `/tmp/heap_dump.hprof` in **Eclipse Memory Analyzer Tool (MAT)** or **IntelliJ Profiler**.
3. Inspect Dominator Tree and filter by class:
   * `kotlinx.coroutines.DispatchedContinuation`
   * `kotlinx.coroutines.JobSupport`
   * `reactor.netty.channel.ChannelOperations`
4. If hundreds of thousands of `DispatchedContinuation` instances are retained:
   * Right-click $\rightarrow$ **Path to GC Roots** $\rightarrow$ **Exclude weak/soft references**.
   * Trace the retaining reference to see which service or static map is keeping the coroutine alive.

---

### Method 3: Production Metrics to Monitor in Prometheus / Grafana

| Metric Name | Threshold / Signal | Root Cause |
| :--- | :--- | :--- |
| `jvm.memory.used{area="heap"}` | Steadily increasing saw-tooth baseline that never resets after Full GC. | Suspended coroutine continuations retaining heap objects. |
| `hikaricp.connections.pending` | Greater than 0 for sustained periods. | Thread holding DB connection while suspended in non-blocking I/O. |
| `reactor.netty.connection.provider.active.connections` | Continuously near max limit (e.g. 500). | HTTP requests missing `.timeout(...)` and hanging open indefinitely. |
| `executor.active` (Dispatchers.IO) | High active worker thread count even during idle traffic. | Coroutines executing blocking operations on CPU threads without yielding. |

---

## 6. Senior Engineer Interview Summary: "How do you debug coroutines in production?"

> *"In production microservices, debugging coroutines centers on three core pillars:*
> 1. *Distributed Tracing Context Propagation:* Because coroutines suspend and resume across different worker threads, standard `ThreadLocal` MDC trace IDs get lost or bleed into other requests. We solve this by attaching `MDCContext()` and Micrometer's `asContextElement()` to our coroutine contexts, ensuring OpenTelemetry spans render seamless waterfall charts across asynchronous child boundaries.
> 2. *Coroutine Dumps & Probes:* Standard thread dumps only show parked threads. We leverage `-Dkotlinx.coroutines.debug` and `DebugProbes` exposed via a custom Actuator endpoint (`/actuator/coroutines`). This allows us to inspect all suspended coroutines, their exact suspension line (e.g., waiting on WebClient `awaitSingleOrNull`), and their parent-child hierarchy in real time.
> 3. *Leak Prevention & Heap Diagnostics:* Coroutine leaks happen when continuations stay suspended indefinitely on hanging sockets without timeouts. We prevent this by enforcing strict 5-second timeouts on all reactive calls (`.timeout()`) and isolating network calls outside `@Transactional` boundaries. If memory grows, we analyze heap dumps in Eclipse MAT targeting `DispatchedContinuation` instances to trace uncollected closures back to their GC root.*"

---

## 7. Real-Time Coroutine Suspension & Thread-Switching Logs

In [`ExternalWebClientAdapter.kt`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/src/main/kotlin/com/example/demo/infrastructure/adapter/out/client/ExternalWebClientAdapter.kt#L45-L69) and [`ResourceApplicationService.kt`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/src/main/kotlin/com/example/demo/application/service/ResourceApplicationService.kt#L103-L125), we record thread names before suspension and after resumption:

```kotlin
// Before Suspension:
val threadBefore = Thread.currentThread().name
logger.info("[COROUTINE-SUSPEND] refId='{}' preparing non-blocking HTTP call on thread=[{}]", refId, threadBefore)

val response = webClient.get()...awaitSingleOrNull()

// After Resumption:
val threadAfter = Thread.currentThread().name
logger.info(
    "[COROUTINE-RESUME] refId='{}' resumed after I/O on thread=[{}] (threadSwitched={})",
    refId,
    threadAfter,
    threadBefore != threadAfter,
)
```

### Actual Production Log Output
When running 100 concurrent calls, the console logs visually prove non-blocking thread multiplexing:

```
INFO [DefaultDispatcher-worker-1 @sync-ref-1#3] : [COROUTINE-SUSPEND] refId='ref-1' preparing non-blocking HTTP call on thread=[DefaultDispatcher-worker-1 @sync-ref-1#3]
INFO [DefaultDispatcher-worker-3 @sync-ref-2#4] : [COROUTINE-SUSPEND] refId='ref-2' preparing non-blocking HTTP call on thread=[DefaultDispatcher-worker-3 @sync-ref-2#4]
INFO [Test worker @coroutine#2]                  : [GATHER-SUSPEND] All 100 async jobs dispatched. batchSync suspending on thread=[Test worker @coroutine#2] waiting for awaitAll()...

... 60ms simulated network latency via Netty ...

INFO [DefaultDispatcher-worker-11 @sync-ref-1#3]: [COROUTINE-RESUME] refId='ref-1' resumed after I/O on thread=[DefaultDispatcher-worker-11 @sync-ref-1#3] (threadSwitched=true)
INFO [DefaultDispatcher-worker-12 @sync-ref-2#4]: [COROUTINE-RESUME] refId='ref-2' resumed after I/O on thread=[DefaultDispatcher-worker-12 @sync-ref-2#4] (threadSwitched=true)
INFO [DefaultDispatcher-worker-15 @coroutine#2] : [GATHER-RESUME] awaitAll() finished. batchSync resumed on thread=[DefaultDispatcher-worker-15 @coroutine#2]. Saving 100 entities to database...
```

**Key Takeaways from the Logs:**
1. **Thread Yielding:** Worker 1 initiated `ref-1` and immediately suspended. While `ref-1` waited on the socket, Worker 1 was freed to do other work.
2. **Thread Switching (`threadSwitched=true`):** When Netty received the HTTP response 60ms later, the coroutine resumed on `DefaultDispatcher-worker-11`, not `worker-1`!
3. **Coroutine Identity Preserved:** Note the `@sync-ref-1#3` suffix generated by `CoroutineName("sync-$refId")` and `-Dkotlinx.coroutines.debug`, proving that the coroutine's identity was maintained across different physical threads.

---

## 8. WireMock Latency: Integration Tests vs. Real Local API Calls

WireMock can be configured to inject network latency in two distinct environments:

### 1. In Integration Tests ([`BatchSyncWireMockIntegrationTest.kt`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/src/test/kotlin/com/example/demo/infrastructure/adapter/in/rest/BatchSyncWireMockIntegrationTest.kt))
In automated tests, WireMock runs embedded in-memory. Latency is configured programmatically via `.withFixedDelay(...)`:

```kotlin
wireMockServer.stubFor(
    get(urlEqualTo("/api/v1/enrichment/$refId"))
        .willReturn(
            aResponse()
                .withStatus(200)
                .withFixedDelay(60) // 60ms simulated network delay
                .withBody(...)
        )
)
```
> [!IMPORTANT]
> When testing high-concurrency delays (100 parallel calls), configure WireMock with `.asynchronousResponseEnabled(true)` and `.asynchronousResponseThreads(50)`. This enables Servlet 3.0 asynchronous handling in WireMock's Jetty engine, preventing thread exhaustion or `Connection reset` errors.

### 2. In Real Local Development ([`.dev/wiremock/mappings/enrichment.json`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/.dev/wiremock/mappings/enrichment.json))
When running the application locally (`./gradlew bootRun` or `docker compose up`), Spring Boot connects to the real WireMock container on port 8081:
```yaml
external:
  client:
    base-url: http://localhost:8081
```

To delay real API calls, configure `"fixedDelayMilliseconds"` directly in the WireMock stub mapping file at [`.dev/wiremock/mappings/enrichment.json`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/.dev/wiremock/mappings/enrichment.json):

```json
{
  "request": {
    "method": "GET",
    "urlPattern": "/api/v1/enrichment/.*"
  },
  "response": {
    "status": 200,
    "fixedDelayMilliseconds": 100,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "referenceId": "external-sample-ref",
      "category": "finance",
      "riskScore": 75,
      "verified": true,
      "tags": ["partner", "fast-track"]
    }
  }
}
```

Now, whenever you run `docker compose up mock-external-api` and make a real REST request from Postman, curl, or Swagger UI to `POST /api/v1/resources/batch-sync`:
* WireMock on `localhost:8081` will delay every single response by **100ms**.
* Your console will log `[COROUTINE-SUSPEND]` and `[COROUTINE-RESUME]` with `threadSwitched=true`.
* All 100 calls complete in parallel in ~500ms rather than 10 seconds.

