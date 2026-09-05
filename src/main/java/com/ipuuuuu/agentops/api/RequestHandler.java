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
        metrics.httpRequest();
        long startedAt = System.nanoTime();
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if ("GET".equals(method) && "/health".equals(path)) { send(exchange, 200, "{\"status\":\"UP\"}", "application/json; charset=utf-8"); return; }
            if ("GET".equals(method) && "/metrics".equals(path)) {
                String accept = exchange.getRequestHeaders().getFirst("Accept");
                boolean prom = (accept != null && accept.contains("text/plain")) || "format=prometheus".equals(exchange.getRequestURI().getQuery());
                send(exchange, 200, prom ? metrics.prometheus() : metrics.json(), prom ? "text/plain; version=0.0.4; charset=utf-8" : "application/json; charset=utf-8"); return;
            }
            if ("GET".equals(method) && "/traces".equals(path)) { send(exchange, 200, "[" + traces.recent().stream().collect(Collectors.joining(",")) + "]", "application/json; charset=utf-8"); return; }
            if ("POST".equals(method) && "/v1/chat/completions".equals(path)) { handleChat(exchange); return; }
            if ("POST".equals(method) && "/v1/tools/execute".equals(path)) { handleTool(exchange); return; }
            metrics.httpError();
            if (path.equals("/health") || path.equals("/metrics") || path.equals("/traces") || path.equals("/v1/chat/completions") || path.equals("/v1/tools/execute")) {
                exchange.getResponseHeaders().set("Allow", path.startsWith("/v1/") ? "POST" : "GET");
                error(exchange, 405, "method_not_allowed", "Method not allowed");
            } else error(exchange, 404, "not_found", "Route not found");
        } catch (IllegalArgumentException e) { metrics.httpError(); error(exchange, 400, "invalid_request", e.getMessage()); }
        catch (RuntimeException e) { metrics.httpError(); error(exchange, 500, "internal_error", "Internal server error"); }
        finally { metrics.httpLatency((System.nanoTime() - startedAt) / 1_000_000L); }
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        requireJson(exchange);
        String body = read(exchange);
        if (!Json.looksLikeObject(body)) throw new IllegalArgumentException("JSON object is required");
        String model = field(body, "model", 128);
        if (model.isBlank()) throw new IllegalArgumentException("model is required");
        String traceId = traces.start("agent.chat");
        try {
            String prompt = Json.value(body, "prompt");
            ModelResponse result = router.route(new com.ipuuuuu.agentops.model.ModelRequest(model, prompt));
            String response = "{\"id\":\"chatcmpl-" + traceId.substring(0, 8) + "\",\"trace_id\":\"" + traceId
                    + "\",\"model\":\"" + Json.escape(model) + "\",\"provider\":\"" + Json.escape(result.provider())
                    + "\",\"attempts\":" + result.attempts() + ",\"fallback\":" + result.fallback()
                    + ",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                    + Json.escape(result.content()) + "\"}}]}";
            send(exchange, 200, response, "application/json; charset=utf-8");
        } catch (IllegalArgumentException error) {
            metrics.httpError();
            error(exchange, 400, "unknown_model", error.getMessage());
        } catch (IllegalStateException error) {
            metrics.httpError();
            error(exchange, 503, "provider_unavailable", "Provider unavailable");
        }
    }

    private void handleTool(HttpExchange exchange) throws IOException {
        requireJson(exchange);
        String body = read(exchange);
        if (!Json.looksLikeObject(body)) throw new IllegalArgumentException("JSON object is required");
        String tenant = field(body, "tenant_id", 128), tool = field(body, "tool_name", 128), request = field(body, "business_request_id", 256);
        if (tenant.isBlank() || tool.isBlank() || request.isBlank()) throw new IllegalArgumentException("tenant_id, tool_name and business_request_id are required");
        String traceId = traces.start("tool.execute");
        send(exchange, 200, tools.execute(tenant, tool, request).replaceFirst("}$", ",\"trace_id\":\"" + Json.escape(traceId) + "\"}"), "application/json; charset=utf-8");
    }

    private static String field(String body, String name, int max) {
        String value = Json.value(body, name).trim();
        if (value.length() > max || value.chars().anyMatch(c -> c < 0x20)) throw new IllegalArgumentException(name + " is invalid");
        return value;
    }
    private static void requireJson(HttpExchange exchange) {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase().startsWith("application/json")) throw new IllegalArgumentException("Content-Type must be application/json");
    }
    private static String read(HttpExchange exchange) throws IOException {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length != null) { try { if (Long.parseLong(length) > 1024 * 1024) throw new IllegalArgumentException("request body too large"); } catch (NumberFormatException e) { throw new IllegalArgumentException("invalid Content-Length"); } }
        byte[] bytes;
        try (var input = exchange.getRequestBody()) { bytes = input.readNBytes(1024 * 1024 + 1); }
        if (bytes.length > 1024 * 1024) throw new IllegalArgumentException("request body too large");
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static void error(HttpExchange exchange, int status, String code, String message) throws IOException {
        send(exchange, status, "{\"error\":{\"code\":\"" + Json.escape(code) + "\",\"message\":\"" + Json.escape(message == null ? "" : message) + "\"}}", "application/json; charset=utf-8");
    }
    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        send(exchange, status, body, "application/json; charset=utf-8");
    }

    private static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
