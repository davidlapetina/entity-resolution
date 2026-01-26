package com.entity.resolution.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LogContext Tests")
class LogContextTest {

    @AfterEach
    void cleanupMDC() {
        MDC.clear();
    }

    @Test
    @DisplayName("forResolution should set correlationId, entityType, and operation in MDC")
    void forResolutionSetsMDC() {
        try (LogContext ctx = LogContext.forResolution("corr-123", "COMPANY")) {
            assertEquals("corr-123", MDC.get("correlationId"));
            assertEquals("COMPANY", MDC.get("entityType"));
            assertEquals("resolve", MDC.get("operation"));
        }
    }

    @Test
    @DisplayName("forBatch should set batchId and operation in MDC")
    void forBatchSetsMDC() {
        try (LogContext ctx = LogContext.forBatch("batch-456")) {
            assertEquals("batch-456", MDC.get("batchId"));
            assertEquals("batch", MDC.get("operation"));
        }
    }

    @Test
    @DisplayName("forMerge should set correlationId, sourceEntityId, targetEntityId, and operation in MDC")
    void forMergeSetsMDC() {
        try (LogContext ctx = LogContext.forMerge("corr-789", "source-1", "target-2")) {
            assertEquals("corr-789", MDC.get("correlationId"));
            assertEquals("source-1", MDC.get("sourceEntityId"));
            assertEquals("target-2", MDC.get("targetEntityId"));
            assertEquals("merge", MDC.get("operation"));
        }
    }

    @Test
    @DisplayName("MDC should be cleared on close")
    void mdcClearedOnClose() {
        LogContext ctx = LogContext.forResolution("corr-123", "COMPANY");
        assertNotNull(MDC.get("correlationId"));
        assertNotNull(MDC.get("entityType"));

        ctx.close();

        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("entityType"));
        assertNull(MDC.get("operation"));
    }

    @Test
    @DisplayName("Try-with-resources should clean up MDC")
    void tryWithResourcesCleansUp() {
        try (LogContext ctx = LogContext.forResolution("corr-123", "PERSON")) {
            assertEquals("corr-123", MDC.get("correlationId"));
        }
        // After the block, MDC should be cleaned
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("entityType"));
    }

    @Test
    @DisplayName("with() should add additional keys to MDC")
    void withAddsKeys() {
        try (LogContext ctx = LogContext.forResolution("corr-123", "COMPANY")
                .with("inputName", "Acme Corp")
                .with("normalizedName", "acme corp")) {
            assertEquals("corr-123", MDC.get("correlationId"));
            assertEquals("Acme Corp", MDC.get("inputName"));
            assertEquals("acme corp", MDC.get("normalizedName"));
        }
        // All keys should be cleaned
        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("inputName"));
        assertNull(MDC.get("normalizedName"));
    }

    @Test
    @DisplayName("generateCorrelationId should return unique UUIDs")
    void generateCorrelationIdReturnsUniqueIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(LogContext.generateCorrelationId());
        }
        assertEquals(100, ids.size(), "All generated IDs should be unique");
    }

    @Test
    @DisplayName("generateCorrelationId should return valid UUID format")
    void generateCorrelationIdFormat() {
        String id = LogContext.generateCorrelationId();
        assertNotNull(id);
        // UUID format: 8-4-4-4-12
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Should be valid UUID format: " + id);
    }

    @Test
    @DisplayName("Nested contexts should not interfere with each other")
    void nestedContexts() {
        try (LogContext outer = LogContext.forResolution("outer", "COMPANY")) {
            assertEquals("outer", MDC.get("correlationId"));

            try (LogContext inner = LogContext.forBatch("batch-1")) {
                // Inner context adds batchId
                assertEquals("batch-1", MDC.get("batchId"));
                // Note: correlationId from outer may be overridden by inner if keys overlap,
                // but forBatch doesn't set correlationId, so outer's value persists
                assertEquals("outer", MDC.get("correlationId"));
            }
            // After inner closes, batchId should be removed
            assertNull(MDC.get("batchId"));
            // Outer context should still have its keys
            assertEquals("outer", MDC.get("correlationId"));
        }
        assertNull(MDC.get("correlationId"));
    }
}
