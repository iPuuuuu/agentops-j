# Architecture

## Request lifecycle

```text
HTTP request
  -> RequestHandler creates trace
  -> ModelRouter selects providers by priority
  -> Provider attempt succeeds or emits retryable failure
  -> ModelRouter records fallback when moving to a lower-priority provider
  -> response includes trace id and chosen provider
```

## Tool lifecycle

```text
CREATED -> RUNNING -> SUCCEEDED
                  -> FAILED
```

An idempotency key is constructed from `tenant_id`, `tool_name`, and `business_request_id`. A repeated request receives the previously completed result. This protects side-effecting operations such as sending messages, creating tickets, or mutating a business record.

## Design constraints

- The current store is in memory to keep the MVP executable with Java 17 only.
- A production deployment replaces `IdempotencyStore` with Redis/PostgreSQL and emits OpenTelemetry spans.
- Provider request content is intentionally not retained. Only operational metadata belongs in traces by default.
