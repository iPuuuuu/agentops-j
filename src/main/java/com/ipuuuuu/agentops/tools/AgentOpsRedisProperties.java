package com.ipuuuuu.agentops.tools;

import java.time.Duration;

record AgentOpsRedisProperties(Duration ttl) {
    static AgentOpsRedisProperties fromEnvironment() {
        String raw = System.getenv().getOrDefault("AGENTOPS_IDEMPOTENCY_TTL_SECONDS", "86400");
        try {
            return new AgentOpsRedisProperties(Duration.ofSeconds(Math.max(1, Long.parseLong(raw))));
        } catch (NumberFormatException error) {
            return new AgentOpsRedisProperties(Duration.ofDays(1));
        }
    }
}
