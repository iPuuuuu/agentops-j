package com.ipuuuuu.agentops.observability;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Optional JDK HttpClient OTLP/HTTP JSON seam; no OpenTelemetry dependency required. */
public final class OtlpTraceExporter implements TraceExporter {
    private final URI endpoint;
    private final HttpClient client;
    private final Duration timeout;

    public OtlpTraceExporter(URI endpoint) { this(endpoint, Duration.ofSeconds(5)); }

    public OtlpTraceExporter(URI endpoint, Duration timeout) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override public void export(TraceSpan span) {
        Objects.requireNonNull(span, "span");
        String body = "{\"trace_id\":\"" + escape(span.traceId()) + "\",\"span_id\":\""
                + escape(span.spanId()) + "\",\"name\":\"" + escape(span.operation())
                + "\",\"status\":\"" + escape(span.status()) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OTLP exporter returned HTTP " + response.statusCode());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OTLP export interrupted", interrupted);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("OTLP export failed", error);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
