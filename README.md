# AgentOps-J

AgentOps-J is a Java 17 reference implementation for operating AI agents safely in production. It provides deterministic model routing, fallback handling, idempotent tool execution, and lightweight request tracing without requiring a model vendor SDK.

## MVP capabilities

- OpenAI-compatible `POST /v1/chat/completions` shape for a deterministic provider gateway
- Ordered model routing with retry and fallback events
- Tool execution idempotency keyed by tenant, tool, and business request
- In-memory trace and metric counters, exposed through HTTP
- JDK-only runtime so the core can compile and run with Java 17
- Docker image and Docker Compose local workflow

## Quick start

```bash
javac -d out $(find src/main/java -name '*.java')
java -cp out com.ipuuuuu.agentops.AgentOpsApplication
```

The service listens on `http://localhost:8080`.

```bash
curl http://localhost:8080/health
curl http://localhost:8080/metrics
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"balanced","messages":[{"role":"user","content":"hello"}]}'
```

The `balanced` route deliberately fails the primary provider once and then returns through the fallback provider. This makes fallback behavior locally demonstrable without provider credentials.

## Tool idempotency example

```bash
curl -X POST http://localhost:8080/v1/tools/execute \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":"demo","tool_name":"send_email","business_request_id":"order-1001","arguments":{"to":"user@example.com"}}'
```

Repeat the exact request. The response is served from the completed idempotency record and the side effect is not repeated.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Liveness response |
| `GET` | `/metrics` | Request, fallback and tool counters |
| `GET` | `/traces` | Recent request traces |
| `POST` | `/v1/chat/completions` | Model route simulation |
| `POST` | `/v1/tools/execute` | Idempotent tool execution |

## Architecture

```text
Client -> HTTP API -> ModelRouter -> ordered providers
                  -> ToolExecutor -> idempotency store
                  -> TraceStore / MetricsRegistry
```

See [architecture.md](docs/architecture.md), [failure-modes.md](docs/failure-modes.md), and [benchmark.md](docs/benchmark.md).

## Development

`pom.xml` is included as the migration point for a Spring Boot implementation. The initial MVP deliberately uses only the JDK HTTP server so it can be compiled and verified on a clean Java 17 workstation.

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -ea -cp out com.ipuuuuu.agentops.AgentOpsApplicationTest
```

## Roadmap

- [x] Model routing, retries and fallback
- [x] Idempotent tool calls
- [x] Trace and metrics primitives
- [ ] Redis-backed idempotency records
- [ ] Kafka task execution and dead-letter queues
- [ ] OpenTelemetry exporter
- [ ] PostgreSQL prompt registry
- [ ] Python evaluation service
- [ ] Prometheus / Grafana dashboards

## Security note

This repository uses simulated providers and must not contain vendor API keys, production traces, customer data, or employer data.

## License

Apache-2.0
