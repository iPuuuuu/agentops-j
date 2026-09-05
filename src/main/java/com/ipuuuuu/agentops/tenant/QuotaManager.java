package com.ipuuuuu.agentops.tenant;

import java.util.Map;

/** Tenant admission control contract. A successful acquire must be paired with release. */
public interface QuotaManager {
    void register(TenantPolicy policy);
    void remove(String tenantId);
    TenantPolicy policy(String tenantId);
    void acquire(String tenantId, long tokenCost);
    void release(String tenantId);
    Map<String, TenantPolicy> policies();
}
