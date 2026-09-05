package com.ipuuuuu.agentops.task;

@FunctionalInterface
public interface TaskHandler {
    void handle(TaskRecord task) throws Exception;
}
