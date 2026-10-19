# Production Operability, Observability & Logging Wiki

This section covers production operability, observability architectures, structured logging pipelines, and telemetry mechanisms in `crud-kotlin-service`.

---

## Guides in This Section

| Topic | Description | Link |
| :--- | :--- | :--- |
| **Structured JSON Logging & LogstashEncoder Under the Hood** | In-depth breakdown of how Logback transforms `ILoggingEvent` to single-line JSON via Jackson streaming `JsonGenerator`, buffer recycling, `ShortenedThrowableConverter`, and correlation with FluentBit/Loki/Elasticsearch. | [json-logging-and-logstash-encoder.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/operability/json-logging-and-logstash-encoder.md) |
