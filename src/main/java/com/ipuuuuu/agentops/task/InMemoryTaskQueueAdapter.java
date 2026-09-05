package com.ipuuuuu.agentops.task;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

/** TaskQueue adapter used by local tests and the JDK-only runtime. */
public final class InMemoryTaskQueueAdapter implements TaskQueue {
    private final ConcurrentMap<String, BlockingQueue<TaskEnvelope>> topics = new ConcurrentHashMap<>();
    private final BlockingQueue<TaskEnvelope> dlq = new LinkedBlockingQueue<>();

    @Override public TaskEnvelope publish(String topic, String taskName, String payload, int maxRetries) {
        TaskEnvelope envelope = new TaskEnvelope(topic, taskName, payload, maxRetries);
        topics.computeIfAbsent(topic, ignored -> new LinkedBlockingQueue<>()).add(envelope);
        return envelope;
    }

    @Override public TaskEnvelope poll(String topic) throws InterruptedException {
        return topics.computeIfAbsent(topic, ignored -> new LinkedBlockingQueue<>()).take();
    }

    @Override public void acknowledge(TaskEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
    }

    @Override public void retry(TaskEnvelope envelope, String reason) {
        TaskEnvelope next = Objects.requireNonNull(envelope, "envelope").nextAttempt();
        if (next.attempt() > next.maxRetries()) deadLetter(next, reason);
        else topics.computeIfAbsent(next.topic(), ignored -> new LinkedBlockingQueue<>()).add(next);
    }

    @Override public void deadLetter(TaskEnvelope envelope, String reason) {
        dlq.add(Objects.requireNonNull(envelope, "envelope"));
    }

    public int deadLetterSize() { return dlq.size(); }
}
