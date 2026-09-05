# Prompt Registry

`com.ipuuuuu.agentops.prompt.InMemoryPromptRegistry` is a small JDK-only registry for process-local prompt lifecycle management.

- `create` assigns monotonically increasing versions per prompt and records creator/time metadata.
- `activate` selects an existing version and records the actor and reason.
- `rollback` restores the previous activation (not merely the numerically previous version).
- `active`, `versions`, and `audit` return immutable snapshots.

The implementation uses a `ConcurrentHashMap` for prompt lookup and a per-prompt monitor for atomic version creation and lifecycle transitions. It is thread-safe within one JVM; data is intentionally lost when the process exits. A durable backend can implement the `PromptRegistry` interface later without changing callers.

Example:

```java
PromptRegistry registry = new InMemoryPromptRegistry();
registry.create("welcome", "Hello", "release-bot");
registry.create("welcome", "Hello, {{name}}", "release-bot");
registry.activate("welcome", 2, "release-bot", "personalization rollout");
PromptVersion current = registry.active("welcome").orElseThrow();
```

Compile and run the standalone tests:

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -ea -cp out com.ipuuuuu.agentops.prompt.InMemoryPromptRegistryTest
```
