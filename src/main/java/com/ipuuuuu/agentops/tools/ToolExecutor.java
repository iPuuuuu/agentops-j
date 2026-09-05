package com.ipuuuuu.agentops.tools;

import com.ipuuuuu.agentops.core.Json;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Executes registered tools with validation and atomic idempotency semantics. */
public final class ToolExecutor {
    private final IdempotencyStore idempotencyStore;
    private final ToolRegistry registry;
    private final MetricsRegistry metrics;

    public ToolExecutor(MetricsRegistry metrics) {
        this(metrics, new InMemoryIdempotencyStore(), ToolRegistry.defaultRegistry());
    }

    public ToolExecutor(MetricsRegistry metrics, IdempotencyStore idempotencyStore, ToolRegistry registry) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public String execute(String tenant, String tool, String businessRequest) {
        return execute(tenant, tool, businessRequest, Map.of());
    }

    public String execute(String tenant, String tool, String businessRequest, Map<String, ?> parameters) {
        registry.validate(tenant, tool);
        if (businessRequest == null || businessRequest.isBlank()) {
            throw new IllegalArgumentException("business request is required");
        }
        Map<String, ?> safeParameters = parameters == null ? Map.of() : parameters;
        ToolRegistry.ToolDefinition definition = registry.find(tool);
        for (String required : definition.requiredParameters()) {
            if (!safeParameters.containsKey(required) || safeParameters.get(required) == null) {
                throw new IllegalArgumentException("missing required parameter: " + required);
            }
        }
        String key = tenant + ":" + tool + ":" + businessRequest;
        // The pre-read handles the ordinary replay path without invoking the store's
        // mapping function. Concurrent callers may all observe a miss, so the
        // atomic computeIfAbsent result determines which caller actually created it.
        String existing = idempotencyStore.get(key);
        if (existing != null) {
            metrics.toolReplay();
            return withReplayFlag(existing, true);
        }
        AtomicBoolean created = new AtomicBoolean();
        String result = idempotencyStore.computeIfAbsent(key, () -> {
            created.set(true);
            metrics.toolExecution();
            return "{\"tool\":\"" + Json.escape(tool) + "\",\"status\":\"SUCCEEDED\",\"idempotency_key\":\""
                    + Json.escape(key) + "\"}";
        });
        boolean replayed = !created.get();
        if (replayed) metrics.toolReplay();
        return withReplayFlag(result, replayed);
    }

    private static String withReplayFlag(String result, boolean replayed) {
        if (result == null || result.isBlank()) throw new IllegalStateException("tool result is empty");
        if (!result.endsWith("}")) throw new IllegalStateException("tool result is not valid JSON");
        return result.substring(0, result.length() - 1) + ",\"replayed\":" + replayed + "}";
    }
}
