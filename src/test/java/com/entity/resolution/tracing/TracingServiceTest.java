package com.entity.resolution.tracing;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TracingService Tests")
class TracingServiceTest {

    @Nested
    @DisplayName("NoOpTracingService")
    class NoOpTests {

        @Test
        @DisplayName("Span lifecycle should work without errors")
        void spanLifecycleNoErrors() {
            NoOpTracingService noOp = new NoOpTracingService();

            assertDoesNotThrow(() -> {
                try (Span span = noOp.startSpan("test-operation")) {
                    span.setAttribute("key", "value");
                    span.setAttribute("count", 42L);
                    span.setStatus(Span.SpanStatus.OK);
                    span.recordException(new RuntimeException("test"));
                }
            });
        }

        @Test
        @DisplayName("Should create span with attributes")
        void spanWithAttributes() {
            NoOpTracingService noOp = new NoOpTracingService();

            assertDoesNotThrow(() -> {
                Span span = noOp.startSpan("test", Map.of("key1", "val1", "key2", "val2"));
                span.setStatus(Span.SpanStatus.ERROR);
                span.close();
            });
        }

        @Test
        @DisplayName("Should return same singleton span")
        void sameSpanReturned() {
            NoOpTracingService noOp = new NoOpTracingService();
            Span span1 = noOp.startSpan("op1");
            Span span2 = noOp.startSpan("op2");
            assertSame(span1, span2);
        }
    }

    @Nested
    @DisplayName("OpenTelemetryTracingService")
    class OTelTests {

        @Test
        @DisplayName("Should create span with correct operation name")
        void createSpanWithName() {
            Tracer mockTracer = mock(Tracer.class);
            SpanBuilder mockBuilder = mock(SpanBuilder.class);
            io.opentelemetry.api.trace.Span mockOtelSpan = mock(io.opentelemetry.api.trace.Span.class);

            when(mockTracer.spanBuilder("test-op")).thenReturn(mockBuilder);
            when(mockBuilder.startSpan()).thenReturn(mockOtelSpan);

            OpenTelemetryTracingService service = new OpenTelemetryTracingService(mockTracer);
            Span span = service.startSpan("test-op");

            assertNotNull(span);
            verify(mockTracer).spanBuilder("test-op");
            verify(mockBuilder).startSpan();
        }

        @Test
        @DisplayName("Should set attributes on span")
        void setAttributes() {
            Tracer mockTracer = mock(Tracer.class);
            SpanBuilder mockBuilder = mock(SpanBuilder.class);
            io.opentelemetry.api.trace.Span mockOtelSpan = mock(io.opentelemetry.api.trace.Span.class);

            when(mockTracer.spanBuilder(anyString())).thenReturn(mockBuilder);
            when(mockBuilder.startSpan()).thenReturn(mockOtelSpan);

            OpenTelemetryTracingService service = new OpenTelemetryTracingService(mockTracer);
            Span span = service.startSpan("test-op");
            span.setAttribute("key", "value");
            span.setAttribute("count", 42L);

            verify(mockOtelSpan).setAttribute("key", "value");
            verify(mockOtelSpan).setAttribute("count", 42L);
        }

        @Test
        @DisplayName("Should record exception on span")
        void recordException() {
            Tracer mockTracer = mock(Tracer.class);
            SpanBuilder mockBuilder = mock(SpanBuilder.class);
            io.opentelemetry.api.trace.Span mockOtelSpan = mock(io.opentelemetry.api.trace.Span.class);

            when(mockTracer.spanBuilder(anyString())).thenReturn(mockBuilder);
            when(mockBuilder.startSpan()).thenReturn(mockOtelSpan);

            OpenTelemetryTracingService service = new OpenTelemetryTracingService(mockTracer);
            Span span = service.startSpan("test-op");

            RuntimeException ex = new RuntimeException("test error");
            span.recordException(ex);

            verify(mockOtelSpan).recordException(ex);
        }

        @Test
        @DisplayName("Should end span on close")
        void endSpanOnClose() {
            Tracer mockTracer = mock(Tracer.class);
            SpanBuilder mockBuilder = mock(SpanBuilder.class);
            io.opentelemetry.api.trace.Span mockOtelSpan = mock(io.opentelemetry.api.trace.Span.class);

            when(mockTracer.spanBuilder(anyString())).thenReturn(mockBuilder);
            when(mockBuilder.startSpan()).thenReturn(mockOtelSpan);

            OpenTelemetryTracingService service = new OpenTelemetryTracingService(mockTracer);
            Span span = service.startSpan("test-op");
            span.close();

            verify(mockOtelSpan).end();
        }

        @Test
        @DisplayName("Should set OK status")
        void setOkStatus() {
            Tracer mockTracer = mock(Tracer.class);
            SpanBuilder mockBuilder = mock(SpanBuilder.class);
            io.opentelemetry.api.trace.Span mockOtelSpan = mock(io.opentelemetry.api.trace.Span.class);

            when(mockTracer.spanBuilder(anyString())).thenReturn(mockBuilder);
            when(mockBuilder.startSpan()).thenReturn(mockOtelSpan);

            OpenTelemetryTracingService service = new OpenTelemetryTracingService(mockTracer);
            Span span = service.startSpan("test-op");
            span.setStatus(Span.SpanStatus.OK);

            verify(mockOtelSpan).setStatus(StatusCode.OK);
        }

        @Test
        @DisplayName("Should set ERROR status")
        void setErrorStatus() {
            Tracer mockTracer = mock(Tracer.class);
            SpanBuilder mockBuilder = mock(SpanBuilder.class);
            io.opentelemetry.api.trace.Span mockOtelSpan = mock(io.opentelemetry.api.trace.Span.class);

            when(mockTracer.spanBuilder(anyString())).thenReturn(mockBuilder);
            when(mockBuilder.startSpan()).thenReturn(mockOtelSpan);

            OpenTelemetryTracingService service = new OpenTelemetryTracingService(mockTracer);
            Span span = service.startSpan("test-op");
            span.setStatus(Span.SpanStatus.ERROR);

            verify(mockOtelSpan).setStatus(StatusCode.ERROR);
        }

        @Test
        @DisplayName("Should create span with initial attributes")
        void createSpanWithAttributes() {
            Tracer mockTracer = mock(Tracer.class);
            SpanBuilder mockBuilder = mock(SpanBuilder.class);
            io.opentelemetry.api.trace.Span mockOtelSpan = mock(io.opentelemetry.api.trace.Span.class);

            when(mockTracer.spanBuilder("test-op")).thenReturn(mockBuilder);
            when(mockBuilder.setAttribute(anyString(), anyString())).thenReturn(mockBuilder);
            when(mockBuilder.startSpan()).thenReturn(mockOtelSpan);

            OpenTelemetryTracingService service = new OpenTelemetryTracingService(mockTracer);
            Span span = service.startSpan("test-op", Map.of("entityType", "COMPANY"));

            assertNotNull(span);
            verify(mockBuilder).setAttribute("entityType", "COMPANY");
        }
    }
}
