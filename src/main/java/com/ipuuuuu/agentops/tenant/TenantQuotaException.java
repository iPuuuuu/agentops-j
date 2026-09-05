package com.ipuuuuu.agentops.tenant;

/** Admission failure caused by a tenant policy. */
public final class TenantQuotaException extends RuntimeException {
    public enum Reason { REQUEST_QUOTA_EXCEEDED, TOKEN_QUOTA_EXCEEDED, CONCURRENCY_LIMIT,
        RATE_LIMIT_EXCEEDED, UNKNOWN_TENANT, INVALID_TOKEN_COST }

    private final String tenantId;
    private final Reason reason;
    private final long retryAfterMillis;

    public TenantQuotaException(String tenantId, Reason reason, String message) {
        this(tenantId, reason, message, 0L);
    }

    public TenantQuotaException(String tenantId, Reason reason, String message, long retryAfterMillis) {
        super(message);
        this.tenantId = tenantId;
        this.reason = reason;
        this.retryAfterMillis = Math.max(0L, retryAfterMillis);
    }

    public String tenantId() { return tenantId; }
    public Reason reason() { return reason; }
    public long retryAfterMillis() { return retryAfterMillis; }
}
