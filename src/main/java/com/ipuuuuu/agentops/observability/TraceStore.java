package com.ipuuuuu.agentops.observability;

import com.ipuuuuu.agentops.core.Json;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class TraceStore {
    private final Deque<String> traces = new ArrayDeque<>();

    public synchronized String start(String operation) {
        String traceId = UUID.randomUUID().toString();
        traces.addFirst("{\"trace_id\":\"" + Json.escape(traceId) + "\",\"operation\":\""
                + Json.escape(operation) + "\",\"started_at\":\"" + Json.escape(Instant.now().toString()) + "\"}");
        while (traces.size() > 100) traces.removeLast();
        return traceId;
    }

    public synchronized List<String> recent() { return new ArrayList<>(traces); }
}
