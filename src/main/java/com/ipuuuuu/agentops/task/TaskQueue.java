package com.ipuuuuu.agentops.task;

/** Storage-independent task queue contract. Kafka and in-memory adapters implement this seam. */
public interface TaskQueue {
    TaskEnvelope publish(String topic, String taskName, String payload, int maxRetries);

    TaskEnvelope poll(String topic) throws InterruptedException;

    void acknowledge(TaskEnvelope envelope);

    void retry(TaskEnvelope envelope, String reason);

    void deadLetter(TaskEnvelope envelope, String reason);
}
