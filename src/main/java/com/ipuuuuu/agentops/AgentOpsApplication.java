package com.ipuuuuu.agentops;

import com.ipuuuuu.agentops.model.ModelRouter;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import com.ipuuuuu.agentops.observability.TraceStore;
import com.ipuuuuu.agentops.tools.ToolExecutor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

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
    ToolExecutor toolExecutor(MetricsRegistry metrics) {
        return new ToolExecutor(metrics);
    }
}
