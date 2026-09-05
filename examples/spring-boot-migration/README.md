# Spring Boot 3 Migration Entry Point

This directory is a dependency-free migration guide and configuration contract. The runnable MVP intentionally remains JDK-only; do not add Spring imports to `src/main` until Maven/CI dependency resolution is available.

## Suggested mapping

| JDK-only component | Spring Boot 3 target |
| --- | --- |
| `AgentOpsApplication` | `@SpringBootApplication` |
| `RequestHandler` | `@RestController` + `@ExceptionHandler` |
| `ModelRouter` | `@Service` |
| `ToolRegistry` / `ToolExecutor` | singleton beans |
| `InMemoryTaskQueue` | `KafkaTaskQueue` bean |
| `TraceExporter` | OpenTelemetry SDK / OTLP exporter bean |
| `MetricsRegistry` | Micrometer registry |

## Configuration sketch

```yaml
agentops:
  model:
    default-route: balanced
  task:
    backend: memory # kafka when adapter module is enabled
    topic: agentops.tasks
    retry-topic: agentops.tasks.retry
    dlq-topic: agentops.tasks.dlq
  tracing:
    exporter: memory # otlp when endpoint is configured
    endpoint: http://localhost:4318/v1/traces
```

The interface seams in the core are deliberately free of Spring, Kafka, and OpenTelemetry imports, so Java 17 compilation remains reproducible offline.
