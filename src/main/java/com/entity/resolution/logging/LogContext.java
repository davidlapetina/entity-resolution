package com.entity.resolution.logging;

import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AutoCloseable MDC (Mapped Diagnostic Context) wrapper for structured logging.
 * Adds key-value pairs to SLF4J MDC and automatically removes them on close.
 *
 * <p>Usage with try-with-resources:</p>
 * <pre>
 * try (LogContext ctx = LogContext.forResolution(correlationId, "COMPANY")) {
 *     log.info("entity.resolved entityId={} decision={}", entityId, decision);
 * } // MDC entries are automatically cleared
 * </pre>
 */
public class LogContext implements AutoCloseable {

    private final List<String> keys = new ArrayList<>();

    private LogContext() {
    }

    /**
     * Creates a log context for entity resolution operations.
     */
    public static LogContext forResolution(String correlationId, String entityType) {
        LogContext ctx = new LogContext();
        ctx.put("correlationId", correlationId);
        ctx.put("entityType", entityType);
        ctx.put("operation", "resolve");
        return ctx;
    }

    /**
     * Creates a log context for batch operations.
     */
    public static LogContext forBatch(String batchId) {
        LogContext ctx = new LogContext();
        ctx.put("batchId", batchId);
        ctx.put("operation", "batch");
        return ctx;
    }

    /**
     * Creates a log context for merge operations.
     */
    public static LogContext forMerge(String correlationId, String sourceId, String targetId) {
        LogContext ctx = new LogContext();
        ctx.put("correlationId", correlationId);
        ctx.put("sourceEntityId", sourceId);
        ctx.put("targetEntityId", targetId);
        ctx.put("operation", "merge");
        return ctx;
    }

    /**
     * Generates a unique correlation ID.
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Adds an additional key-value pair to this log context.
     */
    public LogContext with(String key, String value) {
        put(key, value);
        return this;
    }

    private void put(String key, String value) {
        keys.add(key);
        MDC.put(key, value);
    }

    @Override
    public void close() {
        for (String key : keys) {
            MDC.remove(key);
        }
        keys.clear();
    }
}
