package com.entity.resolution.bulk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportResultTest {

    @Test
    @DisplayName("Should calculate success count")
    void testSuccessCount() {
        ImportResult result = new ImportResult(100, 50, 30, 10, 5, List.of());
        assertEquals(90, result.successCount());
    }

    @Test
    @DisplayName("Should track errors")
    void testErrorTracking() {
        var errors = List.of(
                new ImportResult.ImportError(5, "Bad Corp", "Parse error"),
                new ImportResult.ImportError(10, "Another Bad", "Timeout")
        );
        ImportResult result = new ImportResult(100, 50, 30, 10, 5, errors);
        assertEquals(2, result.errorCount());
        assertTrue(result.hasErrors());
        assertEquals("Bad Corp", result.errors().get(0).inputName());
    }

    @Test
    @DisplayName("Should handle null errors list")
    void testNullErrors() {
        ImportResult result = new ImportResult(10, 10, 0, 0, 0, null);
        assertFalse(result.hasErrors());
        assertEquals(0, result.errorCount());
    }

    @Test
    @DisplayName("Should have meaningful toString")
    void testToString() {
        ImportResult result = new ImportResult(100, 50, 30, 10, 5, List.of());
        String s = result.toString();
        assertTrue(s.contains("total=100"));
        assertTrue(s.contains("created=50"));
    }
}
