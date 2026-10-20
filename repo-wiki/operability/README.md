# Production Operability, Observability & Logging Wiki

This section covers production operability, observability architectures, structured logging pipelines, and telemetry mechanisms in `crud-kotlin-service`.

---

## Guides in This Section

| Topic | Description | Link |
| :--- | :--- | :--- |
| **Logging Libraries & Stack Architecture** | Complete guide to the 5-layer logging stack: SLF4J facade, Logback Classic engine, Logstash Logback Encoder, `kotlinx-coroutines-slf4j` MDC coroutine context bridge, and Janino XML evaluator. | [logging-libraries-and-stack-architecture.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/operability/logging-libraries-and-stack-architecture.md) |
| **Structured JSON Logging & LogstashEncoder Under the Hood** | In-depth breakdown of how Logback transforms `ILoggingEvent` to single-line JSON via Jackson streaming `JsonGenerator`, buffer recycling, `ShortenedThrowableConverter`, and correlation with FluentBit/Loki/Elasticsearch. | [json-logging-and-logstash-encoder.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/operability/json-logging-and-logstash-encoder.md) |
