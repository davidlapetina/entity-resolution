package com.entity.resolution.tracing;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.util.Map;

/**
 * OpenTelemetry-based implementation of {@link TracingService}.
 * Requires {@code opentelemetry-api} on the classpath (optional dependency).
 *
 * <p>Wraps OpenTelemetry spans in the library's {@link Span} interface,
 * allowing the library to create traces without a hard dependency on OTel.</p>
 */
public class OpenTelemetryTracingService implements TracingService {

    private final Tracer tracer;

    public OpenTelemetryTracingService(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Span startSpan(String operationName) {
        io.opentelemetry.api.trace.Span otelSpan = tracer.spanBuilder(operationName).startSpan();
        return new OTelSpanAdapter(otelSpan);
    }

    @Override
    public Span startSpan(String operationName, Map<String, String> attributes) {
        SpanBuilder builder = tracer.spanBuilder(operationName);
        if (attributes != null) {
            attributes.forEach(builder::setAttribute);
        }
        io.opentelemetry.api.trace.Span otelSpan = builder.startSpan();
        return new OTelSpanAdapter(otelSpan);
    }

    private static class OTelSpanAdapter implements Span {

        private final io.opentelemetry.api.trace.Span otelSpan;

        OTelSpanAdapter(io.opentelemetry.api.trace.Span otelSpan) {
            this.otelSpan = otelSpan;
        }

        @Override
        public void setAttribute(String key, String value) {
            otelSpan.setAttribute(key, value);
        }

        @Override
        public void setAttribute(String key, long value) {
            otelSpan.setAttribute(key, value);
        }

        @Override
        public void setStatus(SpanStatus status) {
            otelSpan.setStatus(status == SpanStatus.OK ? StatusCode.OK : StatusCode.ERROR);
        }

        @Override
        public void recordException(Throwable t) {
            otelSpan.recordException(t);
        }

        @Override
        public void close() {
            otelSpan.end();
        }
    }
}
