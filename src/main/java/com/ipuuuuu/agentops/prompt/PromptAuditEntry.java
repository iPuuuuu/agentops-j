package com.ipuuuuu.agentops.prompt;

import java.time.Instant;
import java.util.Objects;

/** Immutable audit event emitted by registry mutations. */
public record PromptAuditEntry(
        String promptName,
        String action,
        int version,
        Instant occurredAt,
        String actor,
        String reason) {

    public PromptAuditEntry {
        promptName = Objects.requireNonNull(promptName, "promptName");
        action = Objects.requireNonNull(action, "action");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        actor = Objects.requireNonNull(actor, "actor");
        reason = reason == null ? "" : reason;
    }
}
