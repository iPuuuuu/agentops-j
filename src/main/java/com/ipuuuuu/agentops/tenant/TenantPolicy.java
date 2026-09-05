package com.ipuuuuu.agentops.tenant;

import java.time.Duration;
import java.util.Objects;

/** Immutable admission policy for one tenant. All window quotas use a rolling window. */
public record TenantPolicy(
        String tenantId,
        long requestQuota,
        long tokenQuota,
        int maxConcurrent,
        int rateLimit,
        Duration window) {
    public TenantPolicy {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (requestQuota < 1) throw new IllegalArgumentException("requestQuota must be positive");
        if (tokenQuota < 1) throw new IllegalArgumentException("tokenQuota must be positive");
        if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent must be positive");
        if (rateLimit < 1) throw new IllegalArgumentException("rateLimit must be positive");
        window = Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) throw new IllegalArgumentException("window must be positive");
    }

    public static TenantPolicy of(String tenantId, long requestQuota, long tokenQuota,
                                  int maxConcurrent, int rateLimit, Duration window) {
        return new TenantPolicy(tenantId, requestQuota, tokenQuota, maxConcurrent, rateLimit, window);
    }
}
