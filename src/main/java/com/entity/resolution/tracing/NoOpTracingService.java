package com.entity.resolution.tracing;

import java.util.Map;

/**
 * No-op implementation of {@link TracingService}.
 * All methods return {@link NoOpSpan} instances that do nothing.
 */
public class NoOpTracingService implements TracingService {

    private static final Span NO_OP_SPAN = new NoOpSpan();

    @Override
    public Span startSpan(String operationName) {
        return NO_OP_SPAN;
    }

    @Override
    public Span startSpan(String operationName, Map<String, String> attributes) {
        return NO_OP_SPAN;
    }

    private static class NoOpSpan implements Span {
        @Override
        public void setAttribute(String key, String value) {
        }

        @Override
        public void setAttribute(String key, long value) {
        }

        @Override
        public void setStatus(SpanStatus status) {
        }

        @Override
        public void recordException(Throwable t) {
        }

        @Override
        public void close() {
        }
    }
}
