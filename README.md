# AgentOps-J

AgentOps-J is a Java 17 reference implementation for operating AI agents safely in production. It provides deterministic model routing, fallback handling, idempotent tool execution, and lightweight request tracing without requiring a model vendor SDK.

## MVP capabilities

- OpenAI-compatible `POST /v1/chat/completions` shape for a deterministic provider gateway
- Ordered model routing with retry and fallback events
- Tool execution idempotency keyed by tenant, tool, and business request
- In-memory trace and metric counters, exposed through HTTP and Prometheus text format
- JDK-only versioned prompt registry with activation, rollback, and audit history
- JDK-only asynchronous task queue with bounded retries and DLQ replay
- Pluggable idempotency store seam with in-memory and Redis-command adapters
- JDK-only runtime so the core can compile and run with Java 17
- Docker image and Docker Compose local workflow

## Quick start

```bash
mvn spring-boot:run
```

The service listens on `http://localhost:8080`.

```bash
curl http://localhost:8080/health
curl http://localhost:8080/metrics
curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"model":"balanced","messages":[{"role":"user","content":"hello"}]}'
```

The `balanced` route deliberately fails the primary provider once and then returns through the fallback provider. This makes fallback behavior locally demonstrable without provider credentials. To use an OpenAI-compatible service, configure an endpoint; that provider switches from the deterministic implementation to the HTTP adapter.

```bash
export AGENTOPS_PRIMARY_ENDPOINT='https://api.example.com/v1/chat/completions'
export AGENTOPS_PRIMARY_API_KEY='your-key'
export AGENTOPS_PRIMARY_MODEL='your-model'
export AGENTOPS_FALLBACK_ENDPOINT='https://backup.example.com/v1/chat/completions'
export AGENTOPS_FALLBACK_API_KEY='backup-key'
export AGENTOPS_FALLBACK_MODEL='backup-model'
mvn spring-boot:run
```

Supported runtime settings include `AGENTOPS_MAX_RETRIES`, `AGENTOPS_BACKOFF_MILLIS`, `AGENTOPS_BREAKER_THRESHOLD`, and `AGENTOPS_PROVIDER_TIMEOUT_MILLIS`. API keys are read only from environment variables and are never included in responses or metrics.

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

See [architecture.md](docs/architecture.md), [failure-modes.md](docs/failure-modes.md), [benchmark.md](docs/benchmark.md), and [evaluation.md](docs/evaluation.md).

## Offline evaluation

A public synthetic dataset with 100 JSONL samples and a dependency-free Python evaluator are included:

```bash
python3 eval/evaluate.py
```

The report includes success rate, fallback rate, P50/P95/P99 latency, token estimates, illustrative cost, and Tool success rate. See [evaluation.md](docs/evaluation.md) for schema, reproducibility, and limitations.

## Development

The HTTP layer runs on Spring Boot 3 with Java 17. Maven is used for dependency resolution, tests, and packaging.

```bash
mvn test
mvn spring-boot:run
```

To build and run the executable jar:

```bash
mvn package
java -jar target/agentops-j-0.1.0-SNAPSHOT.jar
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
