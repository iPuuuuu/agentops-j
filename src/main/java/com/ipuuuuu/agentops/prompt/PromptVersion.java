package com.ipuuuuu.agentops.prompt;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable version of a named prompt. */
public record PromptVersion(
        String promptName,
        int version,
        String content,
        Instant createdAt,
        String createdBy,
        Map<String, String> metadata) {

    public PromptVersion {
        promptName = requireText(promptName, "promptName");
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        content = Objects.requireNonNull(content, "content");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        createdBy = requireText(createdBy, "createdBy");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public PromptVersion(String promptName, int version, String content,
                         Instant createdAt, String createdBy) {
        this(promptName, version, content, createdAt, createdBy, Map.of());
    }

    /** Alias useful to callers that refer to prompt text as a template. */
    public String template() { return content; }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
