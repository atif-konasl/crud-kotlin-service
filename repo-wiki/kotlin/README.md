# Kotlin Language & JVM Internals Wiki

This section covers Kotlin-specific language features, JVM runtime mechanics, bytecode compilation patterns, and language idioms used across our Spring Boot microservice.

---

## Guides in This Section

| Topic | Description | Link |
| :--- | :--- | :--- |
| **Value Classes & `@JvmInline`** | Zero-cost abstractions, memory overhead of `data class`, heap vs. stack layout, Project Valhalla, name mangling, and boxing/unboxing rules. | [value-classes-and-jvm-inline.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/value-classes-and-jvm-inline.md) |
| **Domain Exceptions & Hierarchy** | Architectural rationale for `abstract class`, JVM unchecked exceptions (`RuntimeException`), Spring `@Transactional` automatic rollback, and `sealed class` tradeoffs. | [domain-exceptions-and-hierarchy.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/kotlin/domain-exceptions-and-hierarchy.md) |
