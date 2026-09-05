package com.ipuuuuu.agentops.task;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Dependency-free smoke tests; run with assertions enabled. */
public final class TaskExecutorTest {
    public static void main(String[] args) throws Exception {
        InMemoryTaskQueue queue = new InMemoryTaskQueue();
        try (TaskExecutor executor = new TaskExecutor(queue, 2)) {
            AtomicInteger successfulAttempts = new AtomicInteger();
            executor.register("success", task -> successfulAttempts.incrementAndGet());
            TaskRecord success = executor.submit("success", "payload", 0);
            await(queue, success.id(), record -> record.status() == TaskStatus.SUCCEEDED);
            assert successfulAttempts.get() == 1;

            AtomicInteger flakyAttempts = new AtomicInteger();
            executor.register("flaky", task -> {
                if (flakyAttempts.incrementAndGet() < 3) throw new IllegalStateException("temporary");
            });
            TaskRecord flaky = executor.submit("flaky", null, 2);
            TaskRecord flakyResult = await(queue, flaky.id(), record -> record.status() == TaskStatus.SUCCEEDED);
            assert flakyResult.attempts() == 3;
            assert flakyAttempts.get() == 3;

            executor.register("always-fails", task -> { throw new RuntimeException("permanent"); });
            TaskRecord doomed = executor.submit("always-fails", null, 1);
            TaskRecord dlq = await(queue, doomed.id(), record -> record.status() == TaskStatus.DLQ);
            assert dlq.attempts() == 2;
            assert queue.deadLetters().stream().anyMatch(record -> record.id().equals(doomed.id()));

            AtomicInteger replayAttempts = new AtomicInteger();
            executor.register("replayable", task -> {
                if (replayAttempts.incrementAndGet() == 1) throw new RuntimeException("first run fails");
            });
            TaskRecord replayTarget = executor.submit("replayable", null, 0);
            await(queue, replayTarget.id(), record -> record.status() == TaskStatus.DLQ);
            TaskRecord replayed = executor.replay(replayTarget.id());
            assert replayed.status() == TaskStatus.PENDING;
            TaskRecord replayResult = await(queue, replayTarget.id(), record -> record.status() == TaskStatus.SUCCEEDED);
            assert replayResult.attempts() == 1;
            assert replayAttempts.get() == 2;
        }
        System.out.println("TaskExecutorTest passed");
    }

    private static TaskRecord await(InMemoryTaskQueue queue, String id, Predicate<TaskRecord> condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            TaskRecord record = queue.get(id).orElseThrow();
            if (condition.test(record)) return record;
            Thread.sleep(5);
        }
        throw new AssertionError("Timed out waiting for task " + id + ": " + queue.get(id).orElse(null));
    }
}
