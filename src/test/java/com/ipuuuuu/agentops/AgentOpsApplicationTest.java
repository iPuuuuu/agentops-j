package com.ipuuuuu.agentops;

import com.ipuuuuu.agentops.model.ModelResponse;
import com.ipuuuuu.agentops.model.ModelRouter;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import com.ipuuuuu.agentops.tools.ToolExecutor;

public final class AgentOpsApplicationTest {
    public static void main(String[] args) {
        MetricsRegistry metrics = new MetricsRegistry();
        ModelResponse response = new ModelRouter(metrics).route("balanced");
        assert response.fallback();
        assert response.provider().equals("fallback-provider");
        assert response.attempts() == 2;

        ToolExecutor tools = new ToolExecutor(metrics);
        String first = tools.execute("demo", "send_email", "order-1001");
        String second = tools.execute("demo", "send_email", "order-1001");
        assert first.contains("\"replayed\":false");
        assert second.contains("\"replayed\":true");
        assert metrics.json().contains("\"fallbacks\":1");
        assert metrics.json().contains("\"tool_executions\":1");
        assert metrics.json().contains("\"tool_replays\":1");
        System.out.println("AgentOpsApplicationTest passed");
    }
}
