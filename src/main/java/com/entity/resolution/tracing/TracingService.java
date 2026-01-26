package com.entity.resolution.tracing;

import java.util.Map;

/**
 * Interface for distributed tracing integration.
 * Implementations can integrate with OpenTelemetry, Zipkin, or other tracing systems.
 * The default {@link NoOpTracingService} does nothing, ensuring the library works
 * without any tracing dependencies on the classpath.
 */
public interface TracingService {

    Span startSpan(String operationName);

    Span startSpan(String operationName, Map<String, String> attributes);
}
