package com.ipuuuuu.agentops.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/** Thread-safe in-memory task queue and dead-letter store. */
public final class InMemoryTaskQueue {
    private final Map<String, TaskRecord> records = new ConcurrentHashMap<>();
    private final BlockingQueue<String> ready = new LinkedBlockingQueue<>();
    private final Map<String, TaskRecord> deadLetter = new ConcurrentHashMap<>();

    public TaskRecord enqueue(String name, Object payload, int maxRetries) {
        TaskRecord record = new TaskRecord(name, payload, maxRetries);
        records.put(record.id(), record);
        ready.add(record.id());
        return record;
    }

    public TaskRecord enqueue(TaskRecord record) {
        Objects.requireNonNull(record, "record");
        records.put(record.id(), record);
        if (record.status() == TaskStatus.DLQ) deadLetter.put(record.id(), record);
        else ready.add(record.id());
        return record;
    }

    TaskRecord take() throws InterruptedException {
        return records.get(ready.take());
    }

    void requeue(TaskRecord record) {
        records.put(record.id(), record);
        ready.add(record.id());
    }

    void update(TaskRecord record) {
        records.put(record.id(), record);
        if (record.status() == TaskStatus.DLQ) deadLetter.put(record.id(), record);
        else deadLetter.remove(record.id());
    }

    public Optional<TaskRecord> get(String id) { return Optional.ofNullable(records.get(id)); }

    public List<TaskRecord> all() { return List.copyOf(new ArrayList<>(records.values())); }

    public List<TaskRecord> deadLetters() { return List.copyOf(new ArrayList<>(deadLetter.values())); }

    /** Move a DLQ task back to the ready queue and reset its retry history. */
    public TaskRecord replay(String id) {
        TaskRecord old = deadLetter.remove(id);
        if (old == null) throw new IllegalArgumentException("Task is not in DLQ: " + id);
        Instant now = Instant.now();
        TaskRecord replayed = new TaskRecord(old.id(), old.name(), old.payload(), TaskStatus.PENDING,
                0, old.maxRetries(), null, old.createdAt(), now);
        records.put(id, replayed);
        ready.add(id);
        return replayed;
    }
}
