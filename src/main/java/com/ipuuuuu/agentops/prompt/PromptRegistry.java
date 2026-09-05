package com.ipuuuuu.agentops.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal lifecycle API for versioned prompts. */
public interface PromptRegistry {
    PromptVersion create(String promptName, String content, String actor);

    PromptVersion create(String promptName, String content, String actor,
                         Map<String, String> metadata);

    PromptVersion activate(String promptName, int version, String actor, String reason);

    PromptVersion rollback(String promptName, String actor, String reason);

    Optional<PromptVersion> active(String promptName);

    List<PromptVersion> versions(String promptName);

    List<PromptAuditEntry> audit(String promptName);
}
