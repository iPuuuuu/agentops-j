package com.ipuuuuu.agentops.task;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Serializable task message metadata suitable for Kafka headers and payloads. */
public record TaskEnvelope(
        String id,
        String topic,
        String taskName,
        String payload,
        int attempt,
        int maxRetries,
        String traceId,
        Instant createdAt) {
    public TaskEnvelope {
        id = requireText(id, "id");
        topic = requireText(topic, "topic");
        taskName = requireText(taskName, "taskName");
        payload = payload == null ? "" : payload;
        if (attempt < 0 || maxRetries < 0 || attempt > maxRetries + 1) {
            throw new IllegalArgumentException("invalid task retry counters");
        }
        traceId = traceId == null ? "" : traceId;
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public TaskEnvelope(String topic, String taskName, String payload, int maxRetries) {
        this(UUID.randomUUID().toString(), topic, taskName, payload, 0, maxRetries, "", Instant.now());
    }

    public TaskEnvelope nextAttempt() {
        return new TaskEnvelope(id, topic, taskName, payload, attempt + 1, maxRetries, traceId, createdAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
