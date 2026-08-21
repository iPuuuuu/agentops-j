package com.ipuuuuu.agentops.api;

import com.ipuuuuu.agentops.core.Json;
import com.ipuuuuu.agentops.model.ModelResponse;
import com.ipuuuuu.agentops.model.ModelRouter;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import com.ipuuuuu.agentops.observability.TraceStore;
import com.ipuuuuu.agentops.tools.ToolExecutor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class RequestHandler implements HttpHandler {
    private final ModelRouter router;
    private final ToolExecutor tools;
    private final MetricsRegistry metrics;
    private final TraceStore traces;

    public RequestHandler(ModelRouter router, ToolExecutor tools, MetricsRegistry metrics, TraceStore traces) {
        this.router = router;
        this.tools = tools;
        this.metrics = metrics;
        this.traces = traces;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if ("GET".equals(method) && "/health".equals(path)) { send(exchange, 200, "{\"status\":\"UP\"}"); return; }
        if ("GET".equals(method) && "/metrics".equals(path)) { send(exchange, 200, metrics.json()); return; }
        if ("GET".equals(method) && "/traces".equals(path)) { send(exchange, 200, "[" + traces.recent().stream().collect(Collectors.joining(",")) + "]"); return; }
        if ("POST".equals(method) && "/v1/chat/completions".equals(path)) { handleChat(exchange); return; }
        if ("POST".equals(method) && "/v1/tools/execute".equals(path)) { handleTool(exchange); return; }
        send(exchange, 404, "{\"error\":\"not_found\"}");
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        String model = Json.value(read(exchange), "model");
        if (model.isBlank()) { send(exchange, 400, "{\"error\":\"model is required\"}"); return; }
        String traceId = traces.start("agent.chat");
        try {
            ModelResponse result = router.route(model);
            String response = "{\"id\":\"chatcmpl-" + traceId.substring(0, 8) + "\",\"trace_id\":\"" + traceId
                    + "\",\"model\":\"" + model + "\",\"provider\":\"" + result.provider()
                    + "\",\"attempts\":" + result.attempts() + ",\"fallback\":" + result.fallback()
                    + ",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                    + result.content() + "\"}}]}";
            send(exchange, 200, response);
        } catch (IllegalStateException error) {
            send(exchange, 503, "{\"error\":\"provider_unavailable\"}");
        }
    }

    private void handleTool(HttpExchange exchange) throws IOException {
        String body = read(exchange);
        String tenant = Json.value(body, "tenant_id");
        String tool = Json.value(body, "tool_name");
        String request = Json.value(body, "business_request_id");
        if (tenant.isBlank() || tool.isBlank() || request.isBlank()) {
            send(exchange, 400, "{\"error\":\"tenant_id, tool_name and business_request_id are required\"}");
            return;
        }
        String traceId = traces.start("tool.execute");
        send(exchange, 200, tools.execute(tenant, tool, request) + ",\"trace_id\":\"" + traceId + "\"}");
    }

    private static String read(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
