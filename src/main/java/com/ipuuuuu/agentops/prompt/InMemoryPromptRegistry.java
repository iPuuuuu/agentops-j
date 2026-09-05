package com.ipuuuuu.agentops.prompt;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, process-local prompt registry with immutable snapshots. */
public final class InMemoryPromptRegistry implements PromptRegistry {
    private final Clock clock;
    private final ConcurrentHashMap<String, PromptState> prompts = new ConcurrentHashMap<>();

    public InMemoryPromptRegistry() { this(Clock.systemUTC()); }

    public InMemoryPromptRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PromptVersion create(String promptName, String content, String actor) {
        return create(promptName, content, actor, Map.of());
    }

    @Override
    public PromptVersion create(String promptName, String content, String actor,
                                Map<String, String> metadata) {
        requireName(promptName);
        Objects.requireNonNull(content, "content");
        requireActor(actor);
        Map<String, String> safeMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        PromptState state = prompts.computeIfAbsent(promptName, ignored -> new PromptState());
        synchronized (state) {
            int version = state.versions.size() + 1;
            PromptVersion created = new PromptVersion(promptName, version, content,
                    Instant.now(clock), actor, safeMetadata);
            state.versions.put(version, created);
            state.audit.add(new PromptAuditEntry(promptName, "CREATED", version,
                    created.createdAt(), actor, ""));
            return created;
        }
    }

    @Override
    public PromptVersion activate(String promptName, int version, String actor, String reason) {
        requireName(promptName);
        requireActor(actor);
        PromptState state = state(promptName);
        synchronized (state) {
            PromptVersion selected = state.versions.get(version);
            if (selected == null) throw new IllegalArgumentException(
                    "unknown prompt version: " + promptName + "@" + version);
            state.activeVersion = version;
            state.activationHistory.add(version);
            state.audit.add(new PromptAuditEntry(promptName, "ACTIVATED", version,
                    Instant.now(clock), actor, reason));
            return selected;
        }
    }

    @Override
    public PromptVersion rollback(String promptName, String actor, String reason) {
        requireName(promptName);
        requireActor(actor);
        PromptState state = state(promptName);
        synchronized (state) {
            if (state.activationHistory.size() < 2) {
                throw new IllegalStateException("no previous active version for: " + promptName);
            }
            // Drop the current activation and restore the preceding activation.
            state.activationHistory.remove(state.activationHistory.size() - 1);
            int previous = state.activationHistory.get(state.activationHistory.size() - 1);
            state.activeVersion = previous;
            state.audit.add(new PromptAuditEntry(promptName, "ROLLED_BACK", previous,
                    Instant.now(clock), actor, reason));
            return state.versions.get(previous);
        }
    }

    @Override
    public Optional<PromptVersion> active(String promptName) {
        requireName(promptName);
        PromptState state = prompts.get(promptName);
        if (state == null) return Optional.empty();
        synchronized (state) {
            return Optional.ofNullable(state.versions.get(state.activeVersion));
        }
    }

    @Override
    public List<PromptVersion> versions(String promptName) {
        requireName(promptName);
        PromptState state = prompts.get(promptName);
        if (state == null) return List.of();
        synchronized (state) { return List.copyOf(state.versions.values()); }
    }

    @Override
    public List<PromptAuditEntry> audit(String promptName) {
        requireName(promptName);
        PromptState state = prompts.get(promptName);
        if (state == null) return List.of();
        synchronized (state) { return List.copyOf(state.audit); }
    }

    private PromptState state(String promptName) {
        PromptState state = prompts.get(promptName);
        if (state == null) throw new IllegalArgumentException("unknown prompt: " + promptName);
        return state;
    }

    private static void requireName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("promptName must not be blank");
    }

    private static void requireActor(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("actor must not be blank");
    }

    private static final class PromptState {
        private final Map<Integer, PromptVersion> versions = new LinkedHashMap<>();
        private final List<PromptAuditEntry> audit = new ArrayList<>();
        private final List<Integer> activationHistory = new ArrayList<>();
        private int activeVersion;
    }
}
