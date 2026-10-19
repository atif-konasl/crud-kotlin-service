# Kotlin Language & JVM Internals Wiki

This section covers Kotlin-specific language features, JVM runtime mechanics, bytecode compilation patterns, and language idioms used across our Spring Boot microservice.

---

## Guides in This Section

| Topic | Description | Link |
| :--- | :--- | :--- |
| **Master Concurrency Patterns Guide** | In-depth architectural guide covering the 4 threading models (Servlet, Netty, Coroutines, Virtual Threads), Loom vs. Coroutines, Bulkhead, Scatter-Gather, Supervisor, Mutex, Thread Pinning traps, and DB locking. | [concurrency-patterns.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/concurrency-patterns.md) |
| **Interview Story: RFQ Coroutine Engine** | The complete 3-minute STAR interview story, elevator pitch, and follow-up defense scripts for discussing this architecture with senior interviewers. | [rfq-matching-interview-story.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/rfq-matching-interview-story.md) |
| **RFQ Coroutine Architecture & Triggers** | End-to-end trace of how coroutines are triggered in `rfq-service`: AWS SQS event listener (`@SqsListener`), REST marketplace controller (`POST /supplier/rfq/...`), and the 2-tier hierarchical fan-out tree. | [rfq-matching-coroutine-architecture.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/rfq-matching-coroutine-architecture.md) |
| **Coroutines in REST Microservices** | Real-world production coroutines across 11 Visable microservices: parallel HTTP fan-out (`async`/`awaitAll`), bulkhead isolation (`limitedParallelism`), non-blocking backoff (`delay`), `supervisorScope`, and MDC tracing propagation (`MDCContext`). | [coroutines-in-rest-services.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/coroutines-in-rest-services.md) |
| **Value Classes & `@JvmInline`** | Zero-cost abstractions, memory overhead of `data class`, heap vs. stack layout, Project Valhalla, name mangling, and boxing/unboxing rules. | [value-classes-and-jvm-inline.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/value-classes-and-jvm-inline.md) |
| **Domain Exceptions & Hierarchy** | Architectural rationale for `abstract class`, JVM unchecked exceptions (`RuntimeException`), Spring `@Transactional` automatic rollback, and `sealed class` tradeoffs. | [domain-exceptions-and-hierarchy.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/domain-exceptions-and-hierarchy.md) |
