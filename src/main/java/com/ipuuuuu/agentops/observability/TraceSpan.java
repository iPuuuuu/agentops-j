package com.ipuuuuu.agentops.observability;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Minimal trace data model aligned with OpenTelemetry concepts without its SDK. */
public record TraceSpan(String traceId, String spanId, String operation,
                        Instant startedAt, Instant endedAt, Map<String, String> attributes,
                        String status) {
    public TraceSpan {
        traceId = requireText(traceId, "traceId");
        spanId = requireText(spanId, "spanId");
        operation = requireText(operation, "operation");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        endedAt = Objects.requireNonNull(endedAt, "endedAt");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        status = status == null ? "UNSET" : status;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
