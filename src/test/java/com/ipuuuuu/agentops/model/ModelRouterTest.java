package com.ipuuuuu.agentops.model;

import com.ipuuuuu.agentops.observability.MetricsRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class ModelRouterTest {
    public static void main(String[] args) {
        balancedStatePersistsPerProvider();
        recordsMetricsForRoutes();
        retriesBeforeFallback();
        breakerSkipsRepeatedlyFailingProvider();
        unknownModelIsExplicit();
        errorClassificationIsExplicit();
        openAiCompatibleProviderUsesConfiguredUpstreamModel();
        System.out.println("ModelRouterTest passed");
    }

    private static void balancedStatePersistsPerProvider() {
        ModelRouter router = new ModelRouter(new MetricsRegistry());
        ModelResponse first = router.route("balanced");
        ModelResponse second = router.route("balanced");
        check(first.fallback() && first.provider().equals("fallback-provider"), "first balanced call falls back");
        check(!second.fallback() && second.provider().equals("primary-provider"), "failFirst state persists and primary recovers");
    }

    private static void recordsMetricsForRoutes() {
        MetricsRegistry metrics = new MetricsRegistry();
        ModelRouter router = new ModelRouter(metrics);
        router.route("balanced");
        router.route("balanced");
        String json = metrics.json();
        check(json.contains("\"requests\":2"), "request count recorded");
        check(json.contains("\"fallbacks\":1"), "fallback count recorded");
        check(json.contains("\"provider_calls\":{\"fallback-provider\":1,\"primary-provider\":1}"), "provider counts recorded");
        check(json.contains("\"fallback_providers\":{\"fallback-provider\":1}"), "fallback provider recorded");
        String prometheus = metrics.prometheus();
        check(prometheus.contains("agentops_requests_total 2"), "prometheus request counter");
        check(prometheus.contains("agentops_provider_calls_total{provider=\"fallback-provider\"} 1"), "prometheus provider counter");
    }

    private static void retriesBeforeFallback() {
        AtomicInteger calls = new AtomicInteger();
        ModelProvider flaky = new ModelProvider() {
            public String name() { return "flaky"; }
            public ModelResponse complete(ModelRequest request) {
                if (calls.getAndIncrement() < 2) throw new ProviderException(ProviderException.Kind.TRANSIENT, "temporary");
                return new ModelResponse(name(), "ok", 1, false);
            }
        };
        ModelRouter router = new ModelRouter(new MetricsRegistry(),
                Map.of("flaky", flaky, "fallback", new DeterministicModelProvider("fallback")),
                Map.of("balanced", List.of("flaky", "fallback")), 2, 0, 3);
        ModelResponse response = router.route("balanced");
        check(response.provider().equals("flaky") && response.attempts() == 3 && !response.fallback(), "retries precede fallback");
    }

    private static void breakerSkipsRepeatedlyFailingProvider() {
        ModelProvider down = new ModelProvider() {
            public String name() { return "down"; }
            public ModelResponse complete(ModelRequest request) { throw new ProviderException(ProviderException.Kind.SERVER, "down"); }
        };
        ModelRouter router = new ModelRouter(new MetricsRegistry(),
                Map.of("down", down, "fallback", new DeterministicModelProvider("fallback")),
                Map.of("balanced", List.of("down", "fallback")), 0, 0, 1);
        ModelResponse first = router.route("balanced");
        ModelResponse second = router.route("balanced");
        check(first.attempts() == 2 && second.attempts() == 1 && second.fallback(), "breaker skips open provider");
    }

    private static void unknownModelIsExplicit() {
        try {
            new ModelRouter(new MetricsRegistry()).route("does-not-exist");
            throw new AssertionError("unknown model should fail");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("unknown model route"), "unknown route message");
        }
    }

    private static void errorClassificationIsExplicit() {
        check(new ProviderException(ProviderException.Kind.RATE_LIMITED, "429").retryable(), "rate limit retryable");
        check(new ProviderException(ProviderException.Kind.NETWORK, "io").retryable(), "network retryable");
        check(!new ProviderException(ProviderException.Kind.AUTHENTICATION, "401").retryable(), "auth non-retryable");
        check(!new ProviderException(ProviderException.Kind.INVALID_REQUEST, "400").retryable(), "invalid request non-retryable");
    }

    private static void openAiCompatibleProviderUsesConfiguredUpstreamModel() {
        try {
            AtomicInteger requests = new AtomicInteger();
            AtomicInteger modelSeen = new AtomicInteger();
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/chat", exchange -> {
                requests.incrementAndGet();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("\"model\":\"upstream-model\"")) modelSeen.incrementAndGet();
                byte[] response = "{\"choices\":[{\"message\":{\"content\":\"stub response\"}}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            try {
                ModelProvider provider = new OpenAiCompatibleHttpProvider("remote", new java.net.URI(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/chat"), "secret", "upstream-model");
                ModelResponse response = provider.complete(new ModelRequest("balanced", "hello"));
                check(requests.get() == 1, "remote provider sends one request");
                check(modelSeen.get() == 1, "remote provider uses configured upstream model");
                check(response.content().equals("stub response"), "remote provider extracts response content");
            } finally {
                server.stop(0);
            }
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
