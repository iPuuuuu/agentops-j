package com.ipuuuuu.agentops.model;

import com.ipuuuuu.agentops.observability.MetricsRegistry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Configuration-driven ordered provider router with bounded retries and a small circuit breaker. */
public final class ModelRouter {
    private final MetricsRegistry metrics;
    private final Map<String, ModelProvider> providers;
    private final Map<String, List<String>> routes;
    private final int maxRetries;
    private final long backoffMillis;
    private final int breakerThreshold;
    private final Map<String, Breaker> breakers = new ConcurrentHashMap<>();

    /** Compatibility constructor: preserves the original balanced/fast simulation. */
    public ModelRouter(MetricsRegistry metrics) {
        this(metrics, Map.of("primary-provider", new DeterministicModelProvider("primary-provider", true),
                "fallback-provider", new DeterministicModelProvider("fallback-provider"),
                "fast-provider", new DeterministicModelProvider("fast-provider")),
                Map.of("fast", List.of("fast-provider"), "balanced", List.of("primary-provider", "fallback-provider")), 0, 0, 2);
    }
    public ModelRouter(MetricsRegistry metrics, Map<String, ModelProvider> providers, Map<String, List<String>> routes,
                       int maxRetries, long backoffMillis, int breakerThreshold) {
        this.metrics = metrics; this.providers = Map.copyOf(providers); this.routes = copyRoutes(routes);
        this.maxRetries = Math.max(0, maxRetries); this.backoffMillis = Math.max(0, backoffMillis); this.breakerThreshold = Math.max(1, breakerThreshold);
    }
    public static ModelRouter fromEnvironment(MetricsRegistry metrics) {
        return new ModelRouter(metrics);
    }
    public ModelResponse route(String requestedModel) { return route(new ModelRequest(requestedModel, "")); }
    public ModelResponse route(ModelRequest request) {
        metrics.request();
        long startedAt = System.nanoTime();
        List<String> names = routes.get(request.model());
        if (names == null || names.isEmpty()) {
            metrics.recordError("UNKNOWN_MODEL_ROUTE");
            metrics.failure();
            throw new IllegalArgumentException("unknown model route: " + request.model());
        }
        int totalAttempts = 0;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i); ModelProvider provider = providers.get(name); if (provider == null) continue;
            Breaker breaker = breakers.computeIfAbsent(name, k -> new Breaker());
            if (!breaker.allow()) continue;
            for (int retry = 0; retry <= maxRetries; retry++) {
                totalAttempts++;
                try {
                    ModelResponse result = provider.complete(request);
                    breaker.success(); metrics.success();
                    long latencyMs = elapsedMillis(startedAt);
                    long inputTokens = estimateTokens(request.prompt());
                    long outputTokens = estimateTokens(result.content());
                    metrics.recordModel(result.provider(), latencyMs, inputTokens, outputTokens,
                            estimateCostMicros(inputTokens, outputTokens));
                    if (i > 0) metrics.recordFallbackProvider(result.provider());
                    return new ModelResponse(result.provider(), result.content(), totalAttempts, i > 0);
                } catch (ProviderException error) {
                    metrics.recordError(error.kind().name());
                    breaker.failure();
                    if (!error.retryable() || retry == maxRetries) break;
                    sleep(retry);
                } catch (RuntimeException error) {
                    metrics.recordError("RUNTIME");
                    breaker.failure(); break;
                }
            }
            if (i + 1 < names.size()) metrics.fallback();
        }
        metrics.recordError("NO_MODEL_PROVIDER_AVAILABLE");
        metrics.failure(); throw new IllegalStateException("no model provider available");
    }
    private void sleep(int retry) { if (backoffMillis == 0) return; try { Thread.sleep(backoffMillis * (1L << Math.min(retry, 10))); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private static Map<String, List<String>> copyRoutes(Map<String, List<String>> input) { Map<String,List<String>> out = new HashMap<>(); input.forEach((k,v) -> out.put(k, List.copyOf(v))); return Map.copyOf(out); }
    private static long elapsedMillis(long startedAt) { return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L); }
    private static long estimateTokens(String text) {
        if (text == null) return 0L;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return 0L;
        return trimmed.split("\\s+").length;
    }
    private static long estimateCostMicros(long inputTokens, long outputTokens) { return Math.max(0L, inputTokens + outputTokens) * 10L; }
    private final class Breaker {
        int failures; long openUntil;
        synchronized boolean allow() { return System.currentTimeMillis() >= openUntil; }
        synchronized void success() { failures = 0; openUntil = 0; }
        synchronized void failure() { if (++failures >= breakerThreshold) openUntil = System.currentTimeMillis() + 1000; }
    }
}
