package com.entity.resolution.tracing;

/**
 * Represents a unit of work in a distributed trace.
 * Implements {@link AutoCloseable} so spans can be used in try-with-resources blocks,
 * which automatically ends the span when the block exits.
 *
 * <p>Example usage:</p>
 * <pre>
 * try (Span span = tracingService.startSpan("resolve")) {
 *     span.setAttribute("entityType", "COMPANY");
 *     // ... do work ...
 *     span.setStatus(SpanStatus.OK);
 * }
 * </pre>
 */
public interface Span extends AutoCloseable {

    void setAttribute(String key, String value);

    void setAttribute(String key, long value);

    void setStatus(SpanStatus status);

    void recordException(Throwable t);

    @Override
    void close();

    enum SpanStatus { OK, ERROR }
}
