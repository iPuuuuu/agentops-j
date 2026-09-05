package com.ipuuuuu.agentops.task;

import java.util.Objects;

/**
 * Kafka integration seam with no Kafka dependency. Wire the callbacks to a Kafka
 * client in an adapter module; the core remains javac/JDK-only buildable.
 */
public final class KafkaTaskQueue implements TaskQueue {
    public interface Client {
        void send(String topic, TaskEnvelope envelope);
        TaskEnvelope poll(String topic) throws InterruptedException;
        void acknowledge(TaskEnvelope envelope);
        void publishRetry(String topic, TaskEnvelope envelope, String reason);
        void publishDeadLetter(String topic, TaskEnvelope envelope, String reason);
    }

    private final Client client;
    private final String retrySuffix;
    private final String deadLetterSuffix;

    public KafkaTaskQueue(Client client) {
        this(client, ".retry", ".dlq");
    }

    public KafkaTaskQueue(Client client, String retrySuffix, String deadLetterSuffix) {
        this.client = Objects.requireNonNull(client, "client");
        this.retrySuffix = requireSuffix(retrySuffix);
        this.deadLetterSuffix = requireSuffix(deadLetterSuffix);
    }

    @Override public TaskEnvelope publish(String topic, String taskName, String payload, int maxRetries) {
        TaskEnvelope envelope = new TaskEnvelope(topic, taskName, payload, maxRetries);
        client.send(topic, envelope);
        return envelope;
    }

    @Override public TaskEnvelope poll(String topic) throws InterruptedException {
        return client.poll(topic);
    }

    @Override public void acknowledge(TaskEnvelope envelope) {
        client.acknowledge(Objects.requireNonNull(envelope, "envelope"));
    }

    @Override public void retry(TaskEnvelope envelope, String reason) {
        TaskEnvelope next = Objects.requireNonNull(envelope, "envelope").nextAttempt();
        if (next.attempt() > next.maxRetries()) {
            deadLetter(next, reason);
            return;
        }
        client.publishRetry(next.topic() + retrySuffix, next, reason == null ? "" : reason);
    }

    @Override public void deadLetter(TaskEnvelope envelope, String reason) {
        TaskEnvelope value = Objects.requireNonNull(envelope, "envelope");
        client.publishDeadLetter(value.topic() + deadLetterSuffix, value, reason == null ? "" : reason);
    }

    public String retryTopic(String topic) { return topic + retrySuffix; }
    public String deadLetterTopic(String topic) { return topic + deadLetterSuffix; }

    private static String requireSuffix(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("topic suffix is required");
        return value;
    }
}
