package com.ipuuuuu.agentops;

import com.ipuuuuu.agentops.model.ModelResponse;
import com.ipuuuuu.agentops.model.ModelRouter;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import com.ipuuuuu.agentops.tools.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class AgentOpsApplicationTest {
    public static void main(String[] args) throws Exception {
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

        concurrentToolCallsAreIdempotent();
        com.ipuuuuu.agentops.model.ModelRouterTest.main(new String[0]);
        com.ipuuuuu.agentops.prompt.InMemoryPromptRegistryTest.main(new String[0]);
        com.ipuuuuu.agentops.task.TaskExecutorTest.main(new String[0]);
        com.ipuuuuu.agentops.tenant.InMemoryQuotaManagerTest.main(new String[0]);
        System.out.println("AgentOpsApplicationTest passed");
    }

    private static void concurrentToolCallsAreIdempotent() {
        MetricsRegistry metrics = new MetricsRegistry();
        ToolExecutor tools = new ToolExecutor(metrics);
        int callers = 32;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<String> results = new ArrayList<>();
        Object resultsLock = new Object();
        for (int i = 0; i < callers; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    String result = tools.execute("concurrent", "send_email", "order-1");
                    synchronized (resultsLock) { results.add(result); }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            });
            threads.add(thread);
            thread.start();
        }
        try {
            ready.await();
            start.countDown();
            for (Thread thread : threads) thread.join();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
        assert results.size() == callers;
        long initialCalls = results.stream().filter(value -> value.contains("\"replayed\":false")).count();
        long replays = results.stream().filter(value -> value.contains("\"replayed\":true")).count();
        assert initialCalls == 1 : results;
        assert replays == callers - 1 : results;
        assert metrics.json().contains("\"tool_executions\":1") : metrics.json();
        assert metrics.json().contains("\"tool_replays\":" + (callers - 1)) : metrics.json();
    }
}
