package com.ipuuuuu.agentops.tools;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** JDK-only, thread-safe idempotency store for a single process. */
public final class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentMap<String, String> values = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        return values.get(Objects.requireNonNull(key, "key"));
    }

    @Override
    public String putIfAbsent(String key, String result) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(result, "result");
        String existing = values.putIfAbsent(key, result);
        return existing == null ? result : existing;
    }

    @Override
    public String computeIfAbsent(String key, Supplier<String> resultSupplier) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resultSupplier, "resultSupplier");
        return values.computeIfAbsent(key, ignored ->
                Objects.requireNonNull(resultSupplier.get(), "resultSupplier returned null"));
    }

    public int size() {
        return values.size();
    }
}
