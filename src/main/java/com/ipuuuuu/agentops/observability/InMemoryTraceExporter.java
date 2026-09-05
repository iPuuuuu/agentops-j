package com.ipuuuuu.agentops.observability;

import java.util.ArrayList;
import java.util.List;

/** Deterministic exporter for tests and local development. */
public final class InMemoryTraceExporter implements TraceExporter {
    private final List<TraceSpan> spans = new ArrayList<>();

    @Override public synchronized void export(TraceSpan span) { spans.add(span); }
    public synchronized List<TraceSpan> spans() { return List.copyOf(spans); }
}
