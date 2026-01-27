package com.entity.resolution.retention;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyTest {

    @Test
    @DisplayName("Should create default policy")
    void testDefaults() {
        RetentionPolicy policy = RetentionPolicy.defaults();

        assertEquals(Duration.ofDays(365), policy.mergedEntityRetention());
        assertEquals(Duration.ofDays(2555), policy.auditEntryRetention());
        assertEquals(Duration.ofDays(90), policy.reviewItemRetention());
        assertTrue(policy.softDeleteEnabled());
    }

    @Test
    @DisplayName("Should create no-expiration policy")
    void testNoExpiration() {
        RetentionPolicy policy = RetentionPolicy.noExpiration();

        assertEquals(Duration.ofDays(36500), policy.mergedEntityRetention());
        assertTrue(policy.softDeleteEnabled());
    }

    @Test
    @DisplayName("Should build custom policy")
    void testBuilder() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .mergedEntityRetention(Duration.ofDays(30))
                .auditEntryRetention(Duration.ofDays(365))
                .reviewItemRetention(Duration.ofDays(7))
                .softDeleteEnabled(false)
                .build();

        assertEquals(Duration.ofDays(30), policy.mergedEntityRetention());
        assertEquals(Duration.ofDays(365), policy.auditEntryRetention());
        assertEquals(Duration.ofDays(7), policy.reviewItemRetention());
        assertFalse(policy.softDeleteEnabled());
    }

    @Test
    @DisplayName("Should reject negative durations")
    void testNegativeDuration() {
        assertThrows(IllegalArgumentException.class, () ->
                RetentionPolicy.builder()
                        .mergedEntityRetention(Duration.ofDays(-1))
                        .build());
    }

    @Test
    @DisplayName("Should reject null durations")
    void testNullDuration() {
        assertThrows(NullPointerException.class, () ->
                new RetentionPolicy(null, Duration.ofDays(1), Duration.ofDays(1), true));
    }
}
