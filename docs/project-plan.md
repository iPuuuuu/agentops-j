# AgentOps-J Project Plan

Updated: 2026-10-17

## 1. Product Goal

AgentOps-J will become a production-oriented Java runtime for teams that need to
route model calls, execute tools safely, evaluate AI behavior, and operate the
result with familiar JVM infrastructure.

The project succeeds when a Spring Boot service can adopt AgentOps-J as an
OpenAI-compatible gateway and tool runtime without coupling its application
code to a model vendor SDK.

## 2. Final Implementation Target

The final supported system provides:

- deterministic model routing across multiple OpenAI-compatible providers;
- classified retry, timeout, fallback, and circuit-breaker behavior;
- atomic and recoverable tool execution with idempotency and audit evidence;
- versioned prompts with staged activation, evaluation gates, and rollback;
- tenant authentication, policy, quota, rate-limit, and budget enforcement;
- traces, metrics, and logs that correlate model and tool calls without
  recording secrets or user content by default;
- offline and release-time evaluation using versioned datasets;
- a documented Docker deployment and integration examples for Spring AI,
  LangChain4j, and MCP clients.

### Completion Criteria

AgentOps-J is considered production-ready for its documented scope only when:

1. two independently configured providers pass routing and failure tests;
2. concurrent duplicate tool calls produce exactly one side effect across
   multiple service instances;
3. restart and dependency-outage tests demonstrate recovery behavior;
4. every external request has a trace ID and bounded timeout;
5. tenant boundaries, quotas, redaction, and administrative access have
   automated security tests;
6. a 100-or-more-case evaluation dataset gates releases;
7. P50, P95, P99, success, fallback, and estimated cost metrics are published;
8. CI builds the service, runs unit and integration tests, and checks the
   container image;
9. the README and operational runbook describe only verified capabilities.

## 3. Explicit Non-goals

- Building a general-purpose chat UI.
- Replacing application-specific authorization or business workflows.
- Persisting full prompts and responses by default.
- Supporting every model protocol before the OpenAI-compatible path is stable.
- Introducing Kafka, Kubernetes, or a control-plane UI without a measured need.

## 4. Current Baseline

The repository already includes a Spring Boot HTTP runtime, configurable
OpenAI-compatible providers, deterministic fallback, in-memory and Redis-backed
idempotency seams, an in-memory task queue, prompt versions, tenant quota
primitives, OTLP export code, tests, Docker assets, and a synthetic evaluator.

The main gap is no longer feature presence. The next stage must prove behavior
under concurrency, dependency failure, restarts, multi-tenancy, and real
provider traffic. Some older progress documents still describe already-wired
components as missing and must be kept synchronized with executable evidence.

## 5. Target Architecture

```text
Application / SDK
        |
        v
Authenticated HTTP API
        |
        +--> Tenant policy, quota, and request validation
        |
        +--> Model router --> Provider adapters
        |        |              | retry / timeout / breaker
        |        +--------------+ fallback decision events
        |
        +--> Tool runtime --> Idempotency store --> Tool adapter
        |
        +--> Prompt registry --> Evaluation and release gate
        |
        +--> OpenTelemetry / Prometheus / audit events
```

Control-plane state and request-path state must remain separated. Provider keys
are injected at runtime, tenant secrets are never returned through APIs, and
observability defaults to metadata-only collection.

## 6. Delivery Roadmap

### Milestone A: Baseline Truth and Contracts

Goal: make the current MVP internally consistent and safe to extend.

- Reconcile README, progress, architecture, and roadmap statements with code.
- Define stable request, response, error, trace, and idempotency contracts.
- Validate model messages, generation options, and tool arguments with explicit
  size and shape limits.
- Standardize error categories and HTTP status mappings.
- Add configuration validation for providers, Redis, timeouts, and production
  fail-closed mode.
- Publish an upgrade and compatibility policy for the `0.x` series.

Exit gate:

- Contract tests cover valid, invalid, oversized, and unsupported requests.
- Production mode cannot start with simulated providers enabled.
- Documentation contains no known capability/status contradictions.

### Milestone B: Reliable Provider Runtime

Goal: prove predictable behavior against real and faulty providers.

- Separate retryable transport failures from authentication, validation,
  capacity, and model errors.
- Implement bounded exponential backoff with jitter and request deadlines.
- Complete per-provider circuit breakers and half-open recovery behavior.
- Preserve supported system, user, assistant, and tool messages and generation
  parameters through the gateway.
- Add provider health, route decision, retry, fallback, and timeout metrics.
- Run a two-provider integration matrix using sanitized fixtures.

Exit gate:

- Failure-injection tests cover timeout, disconnect, malformed JSON, 401, 429,
  and 5xx behavior.
- No request can exceed its configured end-to-end deadline.
- Fallback never bypasses tenant policy or production fail-closed settings.

### Milestone C: Durable Tool Execution

Goal: guarantee one accepted business effect for duplicate requests.

- Define idempotency states, ownership, expiry, result size, and replay rules.
- Verify Redis atomic claim/complete/fail transitions under concurrency.
- Add recovery for abandoned executions and dependency outages.
- Validate tool inputs against registered schemas before execution.
- Add tool allowlists, timeouts, execution limits, and audit events.
- Test multiple gateway instances against one Redis deployment.

Exit gate:

- A concurrent duplicate test proves one side effect and deterministic replay.
- Restart tests preserve completed results and recover stale in-flight records.
- Cross-tenant keys cannot collide or expose results.

### Milestone D: Observability and Evaluation

Goal: make quality, reliability, and cost measurable.

- Complete OpenTelemetry spans for gateway, provider, model, and tool stages.
- Export Prometheus metrics and provide a small Grafana dashboard pack.
- Define redaction, sampling, retention, and access-control defaults.
- Version the synthetic dataset and evaluator output schema.
- Add regression thresholds for success, fallback, latency, and quality.
- Store raw benchmark evidence as CI artifacts rather than hand-edited claims.

Exit gate:

- One trace follows a request through provider and tool calls.
- Redaction tests prove secrets and message content are absent by default.
- Evaluation regressions fail CI with an actionable report.

### Milestone E: Prompt and Tenant Governance

Goal: operate changes safely across tenants.

- Persist prompt versions and immutable audit history in PostgreSQL.
- Add draft, evaluate, activate, canary, rollback, and retire states.
- Implement API-key authentication and scoped administrative operations.
- Enforce tenant quotas, rate limits, and budget policies atomically.
- Add approval hooks for high-impact tools and prompt releases.

Exit gate:

- Prompt rollback is tested without service restart.
- Tenant isolation and quota race tests pass against real persistence.
- Administrative changes are attributable and auditable.

### Milestone F: Distribution and Ecosystem

Goal: ship a reproducible release that other projects can adopt.

- Publish a versioned container and Maven artifacts.
- Add migration, configuration, backup, restore, and incident runbooks.
- Provide Spring AI, LangChain4j, and MCP integration examples.
- Add contribution, security-reporting, and compatibility documentation.
- Complete a release candidate deployment with the Doctor-Wang integration.

Exit gate:

- A clean environment can deploy from documented commands.
- The release candidate passes smoke, load, recovery, and security checks.
- Published examples use no private credentials or customer data.

## 7. Verification Strategy

Every milestone must include:

- unit tests for state transitions and boundary behavior;
- HTTP contract tests for public endpoints;
- Testcontainers integration tests for Redis and PostgreSQL where applicable;
- deterministic provider and fault-injection tests;
- concurrency and restart tests for shared state;
- `mvn test`, packaging, container smoke, and `git diff --check` in CI;
- a short evidence record containing commands, versions, and results.

Performance claims require a reproducible dataset, machine profile, warm-up
procedure, and raw output. Security claims require negative tests, not only
configuration documentation.

## 8. Risks and Controls

| Risk | Control |
| --- | --- |
| Scope expands into a full platform too early | Require milestone exit gates before adding infrastructure |
| Retry or fallback amplifies cost and latency | Enforce request deadlines, budgets, and bounded attempts |
| Idempotency fails across instances | Use atomic shared-state tests and explicit stale-owner recovery |
| Traces leak prompts or credentials | Default to metadata only and test redaction |
| Documentation overstates readiness | Link claims to current automated evidence |
| Provider-specific behavior leaks into core APIs | Keep adapters behind stable provider contracts |

## 9. Recommended Execution Order

1. Milestone A establishes trustworthy contracts and documentation.
2. Milestones B and C complete the critical request path.
3. Milestone D makes regressions visible before governance expands.
4. Milestone E adds persistent organizational controls.
5. Milestone F packages only the capabilities proven by earlier gates.

Kafka-backed tasks, a web console, and Kubernetes deployment remain deferred
until the request-path runtime has passed the reliability and observability
gates above.
