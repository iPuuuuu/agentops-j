# Architecture

## Request lifecycle

```text
HTTP request
  -> Spring MVC controller creates trace
  -> ModelRouter selects providers by priority
  -> Provider attempt succeeds or emits retryable failure
  -> ModelRouter records fallback when moving to a lower-priority provider
  -> response includes trace id and chosen provider
```

## Provider configuration

`ModelRouter.fromEnvironment` keeps local route names (`balanced`, `fast`) separate from upstream model names. Each provider can be switched to `OpenAiCompatibleHttpProvider` by setting its endpoint environment variable. API keys are injected through environment variables, while missing endpoints retain the deterministic local providers for offline development.

## Tool lifecycle

```text
PENDING -> RUNNING -> SUCCEEDED
                   -> FAILED
                   -> REPLAYED
```

The registry validates tool names, required parameters, and tenant permissions. An idempotency key is constructed from `tenant_id`, `tool_name`, and `business_request_id`. The `IdempotencyStore` contract supports an in-memory implementation for local runs and a `RedisIdempotencyStore` command seam for atomic SETNX/GET wiring in production. A repeated request receives the previously completed result. This protects side-effecting operations such as sending messages, creating tickets, or mutating a business record.

When `AGENTOPS_REDIS_URL` is set, Spring Boot wires a Redis-backed implementation using atomic `SETNX` with a configurable `AGENTOPS_IDEMPOTENCY_TTL_SECONDS`. The default remains in memory for offline development.

## Async tasks and prompts

`InMemoryTaskQueue` and `TaskExecutor` provide a JDK-only task state machine with bounded retries, `DLQ` transition, and manual replay. `PromptRegistry` provides immutable prompt versions, activation, rollback, and audit events. These are intentionally storage-independent seams so Redis/Kafka/PostgreSQL adapters can be added without changing callers.

## Design constraints

- The default store is in memory to keep the MVP executable with Java 17 only.
- A production deployment wires `RedisIdempotencyStore` to a Redis client and emits OpenTelemetry spans.
- Provider request content is intentionally not retained. Only operational metadata belongs in traces by default.
