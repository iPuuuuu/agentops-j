package com.ipuuuuu.agentops.task;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of a task and its execution history. */
public record TaskRecord(
        String id,
        String name,
        Object payload,
        TaskStatus status,
        int attempts,
        int maxRetries,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public TaskRecord {
        id = Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name");
        status = Objects.requireNonNull(status, "status");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (attempts < 0) throw new IllegalArgumentException("attempts must be non-negative");
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be non-negative");
    }

    public TaskRecord(String name, Object payload, int maxRetries) {
        this(UUID.randomUUID().toString(), name, payload, TaskStatus.PENDING,
                0, maxRetries, null, Instant.now(), Instant.now());
    }

    public boolean terminal() {
        return status == TaskStatus.SUCCEEDED || status == TaskStatus.DLQ;
    }
}
