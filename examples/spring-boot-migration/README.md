# Spring Boot 3 Migration Entry Point

The main application has now completed the Spring Boot 3 migration. This directory remains as a migration reference and configuration contract.

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
