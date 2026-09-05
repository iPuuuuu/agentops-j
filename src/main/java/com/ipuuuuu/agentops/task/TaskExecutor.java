package com.ipuuuuu.agentops.task;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** JDK-only asynchronous task executor with bounded retries and a DLQ. */
public final class TaskExecutor implements AutoCloseable {
    private final InMemoryTaskQueue queue;
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();
    private final ExecutorService workers;
    private volatile boolean closed;

    public TaskExecutor(InMemoryTaskQueue queue, int workerCount) {
        this.queue = Objects.requireNonNull(queue, "queue");
        if (workerCount < 1) throw new IllegalArgumentException("workerCount must be positive");
        this.workers = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "task-executor");
            thread.setDaemon(true);
            return thread;
        });
        for (int i = 0; i < workerCount; i++) workers.submit(this::runWorker);
    }

    public TaskExecutor(InMemoryTaskQueue queue) { this(queue, 1); }

    public void register(String name, TaskHandler handler) {
        handlers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(handler, "handler"));
    }

    public TaskRecord submit(String name, Object payload, int maxRetries) {
        if (closed) throw new IllegalStateException("executor is closed");
        return queue.enqueue(name, payload, maxRetries);
    }

    public TaskRecord replay(String id) {
        if (closed) throw new IllegalStateException("executor is closed");
        return queue.replay(id);
    }

    private void runWorker() {
        while (!closed) {
            try {
                TaskRecord task = queue.take();
                process(task);
            } catch (InterruptedException interrupted) {
                if (closed) Thread.currentThread().interrupt();
            }
        }
    }

    private void process(TaskRecord task) {
        TaskHandler handler = handlers.get(task.name());
        if (handler == null) {
            fail(task, new IllegalArgumentException("No handler registered for task: " + task.name()));
            return;
        }
        Instant now = Instant.now();
        TaskRecord running = replace(task, TaskStatus.RUNNING, task.attempts() + 1, null, now);
        queue.update(running);
        try {
            handler.handle(running);
            queue.update(replace(running, TaskStatus.SUCCEEDED, running.attempts(), null, Instant.now()));
        } catch (Exception error) {
            fail(running, error);
        } catch (Throwable error) {
            fail(running, new RuntimeException(error));
        }
    }

    private void fail(TaskRecord task, Throwable error) {
        String message = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        if (task.attempts() <= task.maxRetries()) {
            TaskRecord retrying = replace(task, TaskStatus.RETRYING, task.attempts(), message, Instant.now());
            queue.update(retrying);
            queue.requeue(retrying);
        } else {
            queue.update(replace(task, TaskStatus.DLQ, task.attempts(), message, Instant.now()));
        }
    }

    private static TaskRecord replace(TaskRecord task, TaskStatus status, int attempts, String error, Instant updated) {
        return new TaskRecord(task.id(), task.name(), task.payload(), status, attempts,
                task.maxRetries(), error, task.createdAt(), updated);
    }

    @Override public void close() {
        closed = true;
        workers.shutdownNow();
        try { workers.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
