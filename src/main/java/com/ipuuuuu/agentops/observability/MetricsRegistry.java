package com.ipuuuuu.agentops.observability;

import java.util.concurrent.atomic.AtomicLong;

public final class MetricsRegistry {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong toolExecutions = new AtomicLong();
    private final AtomicLong toolReplays = new AtomicLong();

    public void request() { requests.incrementAndGet(); }
    public void success() { successes.incrementAndGet(); }
    public void failure() { failures.incrementAndGet(); }
    public void fallback() { fallbacks.incrementAndGet(); }
    public void toolExecution() { toolExecutions.incrementAndGet(); }
    public void toolReplay() { toolReplays.incrementAndGet(); }

    public String json() {
        return "{\"requests\":" + requests.get()
                + ",\"successes\":" + successes.get()
                + ",\"failures\":" + failures.get()
                + ",\"fallbacks\":" + fallbacks.get()
                + ",\"tool_executions\":" + toolExecutions.get()
                + ",\"tool_replays\":" + toolReplays.get() + "}";
    }
}
