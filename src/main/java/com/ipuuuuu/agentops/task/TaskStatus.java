package com.ipuuuuu.agentops.task;

/** Lifecycle states for an asynchronous task. */
public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    RETRYING,
    DLQ
}
