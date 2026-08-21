package com.ipuuuuu.agentops.model;

import com.ipuuuuu.agentops.observability.MetricsRegistry;
import java.util.List;

public final class ModelRouter {
    private final MetricsRegistry metrics;

    public ModelRouter(MetricsRegistry metrics) { this.metrics = metrics; }

    public ModelResponse route(String requestedModel) {
        metrics.request();
        List<String> providers = "fast".equals(requestedModel)
                ? List.of("fast-provider")
                : List.of("primary-provider", "fallback-provider");
        int attempts = 0;
        for (String provider : providers) {
            attempts++;
            if (provider.equals("primary-provider") && "balanced".equals(requestedModel)) {
                metrics.fallback();
                continue;
            }
            metrics.success();
            boolean fallback = attempts > 1;
            return new ModelResponse(provider, "simulated response from " + provider, attempts, fallback);
        }
        metrics.failure();
        throw new IllegalStateException("no model provider available");
    }
}
