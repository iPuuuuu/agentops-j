package com.ipuuuuu.agentops.observability;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** JDK-only counters, dimensions, token/cost accounting and bounded latency histograms. */
public final class MetricsRegistry {
    private static final long[] LATENCY_BUCKETS_MS = {1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000};

    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong toolExecutions = new AtomicLong();
    private final AtomicLong toolReplays = new AtomicLong();
    private final AtomicLong httpRequests = new AtomicLong();
    private final AtomicLong httpErrors = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong estimatedCostMicros = new AtomicLong();
    private final AtomicLong modelLatencyCount = new AtomicLong();
    private final AtomicLong modelLatencyTotalMs = new AtomicLong();
    private final AtomicLong httpLatencyCount = new AtomicLong();
    private final AtomicLong httpLatencyTotalMs = new AtomicLong();
    private final AtomicLongArray modelLatencyBuckets = new AtomicLongArray(LATENCY_BUCKETS_MS.length + 1);
    private final AtomicLongArray httpLatencyBuckets = new AtomicLongArray(LATENCY_BUCKETS_MS.length + 1);
    private final Map<String, AtomicLong> providerCalls = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorTypes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> fallbackProviders = new ConcurrentHashMap<>();

    public void request() { requests.incrementAndGet(); }
    public void success() { successes.incrementAndGet(); }
    public void failure() { failures.incrementAndGet(); }
    public void fallback() { fallbacks.incrementAndGet(); }
    public void toolExecution() { toolExecutions.incrementAndGet(); }
    public void toolReplay() { toolReplays.incrementAndGet(); }
    public void httpRequest() { httpRequests.incrementAndGet(); }
    public void httpError() { httpErrors.incrementAndGet(); }

    public void recordModel(String provider, long latencyMs, long input, long output, long costMicros) {
        increment(providerCalls, provider);
        inputTokens.addAndGet(Math.max(0, input));
        outputTokens.addAndGet(Math.max(0, output));
        estimatedCostMicros.addAndGet(Math.max(0, costMicros));
        recordHistogram(latencyMs, modelLatencyCount, modelLatencyTotalMs, modelLatencyBuckets);
    }

    public void recordError(String type) { increment(errorTypes, type); }
    public void recordFallbackProvider(String provider) { increment(fallbackProviders, provider); }
    public void httpLatency(long latencyMs) { recordHistogram(latencyMs, httpLatencyCount, httpLatencyTotalMs, httpLatencyBuckets); }

    public String json() {
        return "{"
                + "\"requests\":" + requests.get()
                + ",\"successes\":" + successes.get()
                + ",\"failures\":" + failures.get()
                + ",\"fallbacks\":" + fallbacks.get()
                + ",\"tool_executions\":" + toolExecutions.get()
                + ",\"tool_replays\":" + toolReplays.get()
                + ",\"http_requests\":" + httpRequests.get()
                + ",\"http_errors\":" + httpErrors.get()
                + ",\"input_tokens\":" + inputTokens.get()
                + ",\"output_tokens\":" + outputTokens.get()
                + ",\"estimated_cost_micros\":" + estimatedCostMicros.get()
                + ",\"provider_calls\":" + mapJson(providerCalls)
                + ",\"error_types\":" + mapJson(errorTypes)
                + ",\"fallback_providers\":" + mapJson(fallbackProviders)
                + ",\"model_latency_ms\":" + histogramJson(modelLatencyCount, modelLatencyTotalMs, modelLatencyBuckets)
                + ",\"http_latency_ms\":" + histogramJson(httpLatencyCount, httpLatencyTotalMs, httpLatencyBuckets)
                + "}";
    }

    public String prometheus() {
        StringBuilder out = new StringBuilder();
        appendCounter(out, "agentops_requests_total", requests.get());
        appendCounter(out, "agentops_successes_total", successes.get());
        appendCounter(out, "agentops_failures_total", failures.get());
        appendCounter(out, "agentops_fallbacks_total", fallbacks.get());
        appendCounter(out, "agentops_tool_executions_total", toolExecutions.get());
        appendCounter(out, "agentops_tool_replays_total", toolReplays.get());
        appendCounter(out, "agentops_http_requests_total", httpRequests.get());
        appendCounter(out, "agentops_http_errors_total", httpErrors.get());
        appendCounter(out, "agentops_input_tokens_total", inputTokens.get());
        appendCounter(out, "agentops_output_tokens_total", outputTokens.get());
        appendCounter(out, "agentops_estimated_cost_micros_total", estimatedCostMicros.get());
        appendLabeledCounters(out, "agentops_provider_calls_total", "provider", providerCalls);
        appendLabeledCounters(out, "agentops_error_total", "type", errorTypes);
        appendLabeledCounters(out, "agentops_fallback_provider_total", "provider", fallbackProviders);
        appendHistogram(out, "agentops_model_latency_ms", modelLatencyCount, modelLatencyTotalMs, modelLatencyBuckets);
        appendHistogram(out, "agentops_http_latency_ms", httpLatencyCount, httpLatencyTotalMs, httpLatencyBuckets);
        return out.toString();
    }

    private static void increment(Map<String, AtomicLong> values, String key) {
        values.computeIfAbsent(normalize(key), ignored -> new AtomicLong()).incrementAndGet();
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) return "unknown";
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private static void recordHistogram(long latencyMs, AtomicLong count, AtomicLong totalMs, AtomicLongArray buckets) {
        long value = Math.max(0, latencyMs);
        count.incrementAndGet();
        totalMs.addAndGet(value);
        buckets.incrementAndGet(bucketIndex(value));
    }

    private static int bucketIndex(long latencyMs) {
        int index = Arrays.binarySearch(LATENCY_BUCKETS_MS, latencyMs);
        return index >= 0 ? index : Math.min(LATENCY_BUCKETS_MS.length, -index - 1);
    }

    private static String mapJson(Map<String, AtomicLong> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, AtomicLong> entry : new java.util.TreeMap<>(values).entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('"').append(escape(entry.getKey())).append("\":").append(entry.getValue().get());
        }
        return out.append('}').toString();
    }

    private static String histogramJson(AtomicLong count, AtomicLong totalMs, AtomicLongArray buckets) {
        StringBuilder out = new StringBuilder("{");
        out.append("\"count\":").append(count.get());
        out.append(",\"sum\":").append(totalMs.get());
        out.append(",\"buckets\":{");
        long cumulative = 0L;
        for (int i = 0; i < LATENCY_BUCKETS_MS.length; i++) {
            cumulative += buckets.get(i);
            if (i > 0) out.append(',');
            out.append('"').append(LATENCY_BUCKETS_MS[i]).append("\":").append(cumulative);
        }
        cumulative += buckets.get(LATENCY_BUCKETS_MS.length);
        if (LATENCY_BUCKETS_MS.length > 0) out.append(',');
        out.append("\"+Inf\":").append(cumulative);
        return out.append("}}").toString();
    }

    private static void appendCounter(StringBuilder out, String metricName, long value) {
        out.append("# TYPE ").append(metricName).append(" counter\n");
        out.append(metricName).append(' ').append(value).append('\n');
    }

    private static void appendLabeledCounters(StringBuilder out, String metricName, String labelName,
                                              Map<String, AtomicLong> values) {
        out.append("# TYPE ").append(metricName).append(" counter\n");
        if (values.isEmpty()) {
            out.append(metricName).append(' ').append(0).append('\n');
            return;
        }
        for (Map.Entry<String, AtomicLong> entry : new java.util.TreeMap<>(values).entrySet()) {
            out.append(metricName).append('{')
                    .append(labelName).append("=\"").append(escapeLabel(entry.getKey())).append("\"} ")
                    .append(entry.getValue().get()).append('\n');
        }
    }

    private static void appendHistogram(StringBuilder out, String metricName, AtomicLong count, AtomicLong totalMs,
                                        AtomicLongArray buckets) {
        out.append("# TYPE ").append(metricName).append(" histogram\n");
        long cumulative = 0L;
        for (int i = 0; i < LATENCY_BUCKETS_MS.length; i++) {
            cumulative += buckets.get(i);
            out.append(metricName).append("_bucket{le=\"").append(LATENCY_BUCKETS_MS[i]).append("\"} ")
                    .append(cumulative).append('\n');
        }
        cumulative += buckets.get(LATENCY_BUCKETS_MS.length);
        out.append(metricName).append("_bucket{le=\"+Inf\"} ").append(cumulative).append('\n');
        out.append(metricName).append("_sum ").append(totalMs.get()).append('\n');
        out.append(metricName).append("_count ").append(count.get()).append('\n');
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeLabel(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
