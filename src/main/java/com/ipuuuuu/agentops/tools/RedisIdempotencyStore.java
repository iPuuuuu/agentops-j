package com.ipuuuuu.agentops.tools;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Redis adapter seam. The command implementation is injected so the core stays
 * JDK-only; production wiring should map these operations to GET/SETNX (or a
 * Lua script) on a Redis client.
 */
public final class RedisIdempotencyStore implements IdempotencyStore {
    public interface Commands {
        String get(String key);
        boolean setIfAbsent(String key, String value);
    }

    private final Commands commands;

    public RedisIdempotencyStore(Commands commands) {
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    @Override public String get(String key) {
        return commands.get(Objects.requireNonNull(key, "key"));
    }

    @Override public String putIfAbsent(String key, String result) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(result, "result");
        if (commands.setIfAbsent(key, result)) return result;
        String existing = commands.get(key);
        if (existing == null) throw new IllegalStateException("Redis SETNX lost but value is unavailable");
        return existing;
    }

    @Override public String computeIfAbsent(String key, Supplier<String> supplier) {
        String existing = get(key);
        if (existing != null) return existing;
        return putIfAbsent(key, Objects.requireNonNull(supplier.get(), "resultSupplier returned null"));
    }
}
