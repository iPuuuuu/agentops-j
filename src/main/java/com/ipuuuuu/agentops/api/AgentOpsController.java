package com.ipuuuuu.agentops.api;

import com.ipuuuuu.agentops.core.Json;
import com.ipuuuuu.agentops.model.ModelRequest;
import com.ipuuuuu.agentops.model.ModelResponse;
import com.ipuuuuu.agentops.model.ModelRouter;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import com.ipuuuuu.agentops.observability.TraceStore;
import com.ipuuuuu.agentops.tools.ToolExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AgentOpsController {
    private final ModelRouter router;
    private final ToolExecutor tools;
    private final MetricsRegistry metrics;
    private final TraceStore traces;

    public AgentOpsController(ModelRouter router, ToolExecutor tools, MetricsRegistry metrics, TraceStore traces) {
        this.router = router;
        this.tools = tools;
        this.metrics = metrics;
        this.traces = traces;
    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping(path = "/metrics")
    public ResponseEntity<String> metrics(@RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                                          @RequestParam(value = "format", required = false) String format) {
        boolean prometheus = "prometheus".equals(format) || (accept != null && accept.contains(MediaType.TEXT_PLAIN_VALUE));
        return ResponseEntity.ok()
                .contentType(prometheus ? MediaType.TEXT_PLAIN : MediaType.APPLICATION_JSON)
                .body(prometheus ? metrics.prometheus() : metrics.json());
    }

    @GetMapping(path = "/traces", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> traces() {
        return ResponseEntity.ok("[" + traces.recent().stream().collect(Collectors.joining(",")) + "]");
    }

    @PostMapping(path = "/v1/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody Map<String, Object> body) {
        String model = requiredString(body, "model", 128);
        String traceId = traces.start("agent.chat");
        try {
            ModelResponse result = router.route(new ModelRequest(model, prompt(body)));
            String response = "{\"id\":\"chatcmpl-" + traceId.substring(0, 8) + "\",\"trace_id\":\"" + traceId
                    + "\",\"model\":\"" + Json.escape(model) + "\",\"provider\":\"" + Json.escape(result.provider())
                    + "\",\"attempts\":" + result.attempts() + ",\"fallback\":" + result.fallback()
                    + ",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                    + Json.escape(result.content()) + "\"}}]}";
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "unknown_model", error.getMessage());
        } catch (IllegalStateException error) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "provider_unavailable", "Provider unavailable");
        }
    }

    @PostMapping(path = "/v1/tools/execute", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> tool(@RequestBody Map<String, Object> body) {
        String tenant = requiredString(body, "tenant_id", 128);
        String tool = requiredString(body, "tool_name", 128);
        String businessRequest = requiredString(body, "business_request_id", 256);
        String traceId = traces.start("tool.execute");
        try {
            return ResponseEntity.ok(addTraceId(tools.execute(tenant, tool, businessRequest, arguments(body)), traceId));
        } catch (SecurityException error) {
            throw new ApiException(HttpStatus.FORBIDDEN, "tool_forbidden", "Tool forbidden");
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", error.getMessage());
        }
    }

    private static String requiredString(Map<String, Object> body, String name, int maxLength) {
        String value = stringValue(body.get(name), name, maxLength).trim();
        if (value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", name + " is required");
        return value;
    }

    private static String stringValue(Object raw, String name, int maxLength) {
        if (raw == null) return "";
        if (!(raw instanceof String value)) throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", name + " must be a string");
        if (value.length() > maxLength || value.chars().anyMatch(c -> c < 0x20)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", name + " is invalid");
        }
        return value;
    }

    private static String prompt(Map<String, Object> body) {
        String direct = stringValue(body.get("prompt"), "prompt", 65_536);
        if (!direct.isBlank()) return direct;
        Object messages = body.get("messages");
        if (!(messages instanceof List<?> list)) return "";
        for (int i = list.size() - 1; i >= 0; i--) {
            Object item = list.get(i);
            if (item instanceof Map<?, ?> message) {
                Object content = message.get("content");
                if (content instanceof String text && !text.isBlank()) return text;
            }
        }
        return "";
    }

    private static Map<String, ?> arguments(Map<String, Object> body) {
        Object raw = body.get("arguments");
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?> input)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "arguments must be an object");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() instanceof String key) output.put(key, entry.getValue());
        }
        return Map.copyOf(output);
    }

    private static String addTraceId(String json, String traceId) {
        if (json == null || !json.endsWith("}")) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Invalid tool result");
        return json.substring(0, json.length() - 1) + ",\"trace_id\":\"" + Json.escape(traceId) + "\"}";
    }
}
