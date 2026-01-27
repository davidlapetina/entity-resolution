package com.entity.resolution.retention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RetentionResultTest {

    @Test
    @DisplayName("Should calculate total deleted")
    void testTotalDeleted() {
        RetentionResult result = new RetentionResult(10, 20, 5);
        assertEquals(35, result.totalDeleted());
    }

    @Test
    @DisplayName("Should create empty result")
    void testEmpty() {
        RetentionResult result = RetentionResult.empty();
        assertEquals(0, result.totalDeleted());
        assertEquals(0, result.mergedEntitiesDeleted());
        assertEquals(0, result.auditEntriesDeleted());
        assertEquals(0, result.reviewItemsDeleted());
    }

    @Test
    @DisplayName("Should have meaningful toString")
    void testToString() {
        RetentionResult result = new RetentionResult(10, 20, 5);
        String s = result.toString();
        assertTrue(s.contains("merged=10"));
        assertTrue(s.contains("audit=20"));
        assertTrue(s.contains("reviews=5"));
        assertTrue(s.contains("total=35"));
    }
}
