package com.ipuuuuu.agentops.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class InMemoryPromptRegistryTest {
    public static void main(String[] args) throws Exception {
        lifecycleAndAudit();
        concurrentCreation();
        System.out.println("InMemoryPromptRegistryTest passed");
    }

    private static void lifecycleAndAudit() {
        PromptRegistry registry = new InMemoryPromptRegistry();
        PromptVersion first = registry.create("welcome", "hello", "alice");
        PromptVersion second = registry.create("welcome", "hello, {{name}}", "bob");
        assert first.version() == 1;
        assert second.version() == 2;
        assert registry.active("welcome").isEmpty();

        registry.activate("welcome", 1, "alice", "initial rollout");
        registry.activate("welcome", 2, "bob", "personalization");
        assert registry.active("welcome").orElseThrow().version() == 2;
        assert registry.rollback("welcome", "alice", "bad conversion").version() == 1;
        assert registry.active("welcome").orElseThrow().content().equals("hello");
        assert registry.audit("welcome").stream().map(PromptAuditEntry::action)
                .toList().equals(List.of("CREATED", "CREATED", "ACTIVATED", "ACTIVATED", "ROLLED_BACK"));
        assert registry.versions("welcome").size() == 2;
    }

    private static void concurrentCreation() throws Exception {
        PromptRegistry registry = new InMemoryPromptRegistry();
        int workers = 8;
        int each = 25;
        var pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int worker = 0; worker < workers; worker++) {
            int id = worker;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                for (int i = 0; i < each; i++) registry.create("concurrent", "v" + id + ":" + i, "worker");
                return null;
            }));
        }
        assert ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        for (var future : futures) future.get(5, TimeUnit.SECONDS);
        pool.shutdown();
        assert registry.versions("concurrent").size() == workers * each;
        assert registry.audit("concurrent").size() == workers * each;
        for (int i = 0; i < registry.versions("concurrent").size(); i++) {
            assert registry.versions("concurrent").get(i).version() == i + 1;
        }
    }
}
