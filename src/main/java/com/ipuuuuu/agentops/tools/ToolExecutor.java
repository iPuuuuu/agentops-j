package com.ipuuuuu.agentops.tools;

import com.ipuuuuu.agentops.observability.MetricsRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ToolExecutor {
    private final Map<String, String> completed = new ConcurrentHashMap<>();
    private final MetricsRegistry metrics;

    public ToolExecutor(MetricsRegistry metrics) { this.metrics = metrics; }

    public String execute(String tenant, String tool, String businessRequest) {
        String key = tenant + ":" + tool + ":" + businessRequest;
        String existing = completed.get(key);
        if (existing != null) {
            metrics.toolReplay();
            return existing + ",\"replayed\":true";
        }
        metrics.toolExecution();
        String result = "{\"tool\":\"" + tool + "\",\"status\":\"SUCCEEDED\",\"idempotency_key\":\"" + key + "\"";
        completed.putIfAbsent(key, result);
        return result + ",\"replayed\":false";
    }
}
