package com.ipuuuuu.agentops.tools;

import java.util.function.Supplier;

/** Stores completed tool results by idempotency key. Implementations must be thread-safe. */
public interface IdempotencyStore {
    /** Returns the completed result, or {@code null} when the key is absent. */
    String get(String key);

    /** Stores a result only when the key is absent, returning the value that won. */
    String putIfAbsent(String key, String result);

    /** Atomically returns the existing result or computes and stores one. */
    String computeIfAbsent(String key, Supplier<String> resultSupplier);
}
