package com.ipuuuuu.agentops.model;

import com.ipuuuuu.agentops.core.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Optional JDK-only OpenAI-compatible HTTP implementation. */
public final class OpenAiCompatibleHttpProvider implements ModelProvider {
    private final String providerName;
    private final URI endpoint;
    private final String apiKey;
    private final HttpClient client;
    private final Duration timeout;

    public OpenAiCompatibleHttpProvider(String providerName, URI endpoint, String apiKey) {
        this(providerName, endpoint, apiKey, "", Duration.ofSeconds(30));
    }
    public OpenAiCompatibleHttpProvider(String providerName, URI endpoint, String apiKey, Duration timeout) {
        this(providerName, endpoint, apiKey, "", timeout);
    }
    public OpenAiCompatibleHttpProvider(String providerName, URI endpoint, String apiKey, String upstreamModel) {
        this(providerName, endpoint, apiKey, upstreamModel, Duration.ofSeconds(30));
    }
    public OpenAiCompatibleHttpProvider(String providerName, URI endpoint, String apiKey, String upstreamModel,
                                        Duration timeout) {
        this.providerName = providerName; this.endpoint = endpoint; this.apiKey = apiKey == null ? "" : apiKey;
        this.upstreamModel = upstreamModel == null ? "" : upstreamModel;
        this.timeout = timeout; this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }
    private final String upstreamModel;
    @Override public String name() { return providerName; }
    @Override public ModelResponse complete(ModelRequest request) {
        String model = upstreamModel.isBlank() ? request.model() : upstreamModel;
        StringBuilder body = new StringBuilder("{\"model\":\"").append(Json.escape(model)).append("\",\"messages\":[");
        for (int index = 0; index < request.messages().size(); index++) {
            ModelRequest.Message message = request.messages().get(index);
            if (index > 0) body.append(',');
            body.append("{\"role\":\"").append(Json.escape(message.role()))
                    .append("\",\"content\":\"").append(Json.escape(message.content())).append("\"}");
        }
        body.append(']');
        if (request.temperature() != null) body.append(",\"temperature\":").append(request.temperature());
        if (request.maxTokens() != null) body.append(",\"max_tokens\":").append(request.maxTokens());
        body.append('}');
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) throw new ProviderException(classify(status), "provider HTTP " + status + ": " + response.body(), status);
            String content = Json.value(response.body(), "content");
            return new ModelResponse(providerName, content.isBlank() ? response.body() : content, 1, false);
        } catch (ProviderException e) { throw e;
        } catch (Exception e) { throw new ProviderException(ProviderException.Kind.NETWORK, "provider request failed", e); }
    }
    private static ProviderException.Kind classify(int s) {
        if (s == 401 || s == 403) return ProviderException.Kind.AUTHENTICATION;
        if (s == 408 || s == 429) return s == 429 ? ProviderException.Kind.RATE_LIMITED : ProviderException.Kind.TRANSIENT;
        if (s >= 400 && s < 500) return ProviderException.Kind.INVALID_REQUEST;
        if (s >= 500) return ProviderException.Kind.SERVER;
        return ProviderException.Kind.UNKNOWN;
    }
}
