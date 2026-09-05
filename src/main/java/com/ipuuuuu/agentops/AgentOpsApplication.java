package com.ipuuuuu.agentops;

import com.ipuuuuu.agentops.model.ModelRouter;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import com.ipuuuuu.agentops.observability.TraceStore;
import com.ipuuuuu.agentops.tools.ToolExecutor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootApplication
public class AgentOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOpsApplication.class, args);
    }

    @Bean
    MetricsRegistry metricsRegistry() {
        return new MetricsRegistry();
    }

    @Bean
    TraceStore traceStore() {
        return new TraceStore();
    }

    @Bean
    ModelRouter modelRouter(MetricsRegistry metrics) {
        return ModelRouter.fromEnvironment(metrics);
    }

    @Bean
    @Primary
    ToolExecutor toolExecutor(MetricsRegistry metrics,
                              org.springframework.beans.factory.ObjectProvider<com.ipuuuuu.agentops.tools.IdempotencyStore> store) {
        com.ipuuuuu.agentops.tools.IdempotencyStore idempotency = store.getIfAvailable();
        return idempotency == null ? new ToolExecutor(metrics) : new ToolExecutor(
                metrics, idempotency, com.ipuuuuu.agentops.tools.ToolRegistry.defaultRegistry());
    }
}
