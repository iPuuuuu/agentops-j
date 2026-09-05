package com.ipuuuuu.agentops.tenant;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, process-local quota manager with an exact rolling-window counter. */
public final class InMemoryQuotaManager implements QuotaManager {
    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public InMemoryQuotaManager() { this(Clock.systemUTC()); }
    public InMemoryQuotaManager(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    @Override public void register(TenantPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        states.put(policy.tenantId(), new State(policy));
    }

    @Override public void remove(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return;
        states.remove(tenantId);
    }

    @Override public TenantPolicy policy(String tenantId) {
        return state(tenantId).policy;
    }

    @Override public void acquire(String tenantId, long tokenCost) {
        if (tokenCost < 0) throw new TenantQuotaException(tenantId,
                TenantQuotaException.Reason.INVALID_TOKEN_COST, "tokenCost must not be negative");
        State state = state(tenantId);
        synchronized (state) {
            long now = clock.millis();
            state.prune(now);
            TenantPolicy p = state.policy;
            if (state.inFlight >= p.maxConcurrent()) {
                throw denied(tenantId, TenantQuotaException.Reason.CONCURRENCY_LIMIT,
                        "tenant concurrency limit exceeded", 0);
            }
            if (state.requests >= p.requestQuota()) {
                throw denied(tenantId, TenantQuotaException.Reason.REQUEST_QUOTA_EXCEEDED,
                        "tenant request quota exceeded", state.retryAfter(now, p.window()));
            }
            if (tokenCost > p.tokenQuota() - state.tokens) {
                throw denied(tenantId, TenantQuotaException.Reason.TOKEN_QUOTA_EXCEEDED,
                        "tenant token quota exceeded", state.retryAfter(now, p.window()));
            }
            if (state.rateEvents.size() >= p.rateLimit()) {
                throw denied(tenantId, TenantQuotaException.Reason.RATE_LIMIT_EXCEEDED,
                        "tenant rate limit exceeded", state.retryAfter(now, p.window()));
            }
            state.inFlight++;
            state.requests++;
            state.tokens += tokenCost;
            state.rateEvents.addLast(new Usage(now, tokenCost));
        }
    }

    @Override public void release(String tenantId) {
        State state = state(tenantId);
        synchronized (state) {
            if (state.inFlight > 0) state.inFlight--;
        }
    }

    @Override public Map<String, TenantPolicy> policies() {
        Map<String, TenantPolicy> copy = new LinkedHashMap<>();
        states.forEach((id, state) -> copy.put(id, state.policy));
        return Map.copyOf(copy);
    }

    public Snapshot snapshot(String tenantId) {
        State state = state(tenantId);
        synchronized (state) {
            state.prune(clock.millis());
            return new Snapshot(state.policy, state.requests, state.tokens, state.inFlight, state.rateEvents.size());
        }
    }

    private State state(String tenantId) {
        State state = tenantId == null ? null : states.get(tenantId);
        if (state == null) throw new TenantQuotaException(tenantId,
                TenantQuotaException.Reason.UNKNOWN_TENANT, "unknown tenant: " + tenantId);
        return state;
    }

    private static TenantQuotaException denied(String tenant, TenantQuotaException.Reason reason,
                                                String message, long retryAfter) {
        return new TenantQuotaException(tenant, reason, message, retryAfter);
    }

    public record Snapshot(TenantPolicy policy, long requests, long tokens, int inFlight, int rateEvents) {}

    private static final class State {
        private final TenantPolicy policy;
        private final Deque<Usage> rateEvents = new ArrayDeque<>();
        private long requests;
        private long tokens;
        private int inFlight;

        private State(TenantPolicy policy) { this.policy = policy; }

        private void prune(long now) {
            long cutoff = now - policy.window().toMillis();
            while (!rateEvents.isEmpty() && rateEvents.peekFirst().at() <= cutoff) {
                Usage usage = rateEvents.removeFirst();
                requests -= 1;
                tokens -= usage.tokens();
            }
        }

        private long retryAfter(long now, Duration window) {
            Usage oldest = rateEvents.peekFirst();
            if (oldest == null) return window.toMillis();
            return Math.max(1L, oldest.at() + window.toMillis() - now);
        }
    }

    private record Usage(long at, long tokens) {}
}

