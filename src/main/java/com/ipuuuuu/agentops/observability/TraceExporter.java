package com.ipuuuuu.agentops.observability;

/** Storage/export seam for traces; implementations may target OTel, OTLP, or logs. */
public interface TraceExporter extends AutoCloseable {
    void export(TraceSpan span);

    @Override default void close() {}
}
