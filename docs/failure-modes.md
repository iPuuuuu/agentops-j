# Failure Modes

| Failure mode | MVP behavior | Production extension |
| --- | --- | --- |
| Retryable provider failure | Try the next provider and increment fallback metric | Resilience4j circuit breaker and exponential backoff |
| Invalid request | Return HTTP 400 | JSON schema validation |
| Duplicate tool invocation | Return previous result | Redis distributed lock and durable result store |
| Tool failure | Record failed result and return HTTP 500 | Kafka retry topic and dead-letter queue |
| Trace retention risk | Do not retain request content | PII redaction, sampling and retention controls |

The system must not retry a side-effecting tool without an idempotency record.
