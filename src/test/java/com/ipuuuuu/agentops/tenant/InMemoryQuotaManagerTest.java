package com.ipuuuuu.agentops.tenant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryQuotaManagerTest {
    public static void main(String[] args) throws Exception {
        quotaAndWindow();
        concurrencyIsAtomic();
        unknownAndInvalidInputs();
        System.out.println("InMemoryQuotaManagerTest passed");
    }

    private static void quotaAndWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        InMemoryQuotaManager manager = new InMemoryQuotaManager(clock);
        manager.register(TenantPolicy.of("acme", 2, 10, 1, 2, Duration.ofSeconds(10)));
        manager.acquire("acme", 4);
        assert manager.snapshot("acme").requests() == 1;
        assert manager.snapshot("acme").tokens() == 4;
        assertThrows(TenantQuotaException.Reason.CONCURRENCY_LIMIT, () -> manager.acquire("acme", 1));
        manager.release("acme");
        manager.acquire("acme", 6);
        manager.release("acme");
        assertThrows(TenantQuotaException.Reason.REQUEST_QUOTA_EXCEEDED, () -> manager.acquire("acme", 0));
        clock.advance(Duration.ofSeconds(11));
        manager.acquire("acme", 2);
        assert manager.snapshot("acme").requests() == 1;
        manager.release("acme");
    }

    private static void concurrencyIsAtomic() throws Exception {
        InMemoryQuotaManager manager = new InMemoryQuotaManager();
        manager.register(TenantPolicy.of("parallel", 5, 100, 5, 100, Duration.ofMinutes(1)));
        int callers = 40;
        CountDownLatch ready = new CountDownLatch(callers), start = new CountDownLatch(1);
        AtomicInteger admitted = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            Thread t = new Thread(() -> {
                ready.countDown();
                try { start.await(); manager.acquire("parallel", 1); admitted.incrementAndGet(); }
                catch (TenantQuotaException expected) { }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            });
            threads.add(t); t.start();
        }
        assert ready.await(3, java.util.concurrent.TimeUnit.SECONDS);
        start.countDown();
        for (Thread t : threads) t.join();
        assert admitted.get() == 5 : admitted;
        assert manager.snapshot("parallel").requests() == 5;
        for (int i = 0; i < admitted.get(); i++) manager.release("parallel");
    }

    private static void unknownAndInvalidInputs() {
        InMemoryQuotaManager manager = new InMemoryQuotaManager();
        assertThrows(TenantQuotaException.Reason.UNKNOWN_TENANT, () -> manager.acquire("missing", 1));
        manager.register(TenantPolicy.of("known", 10, 10, 1, 10, Duration.ofMinutes(1)));
        assertThrows(TenantQuotaException.Reason.INVALID_TOKEN_COST, () -> manager.acquire("known", -1));
    }

    private static void assertThrows(TenantQuotaException.Reason reason, Runnable action) {
        try { action.run(); throw new AssertionError("expected " + reason); }
        catch (TenantQuotaException e) { assert e.reason() == reason : e.reason(); }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
