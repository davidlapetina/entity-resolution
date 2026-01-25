package com.entity.resolution.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    @Test
    @DisplayName("Should create entity with required fields")
    void testEntityCreation() {
        Entity entity = Entity.builder()
                .canonicalName("International Business Machines")
                .normalizedName("international business machines")
                .type(EntityType.COMPANY)
                .build();

        assertNotNull(entity.getId());
        assertEquals("International Business Machines", entity.getCanonicalName());
        assertEquals("international business machines", entity.getNormalizedName());
        assertEquals(EntityType.COMPANY, entity.getType());
        assertEquals(EntityStatus.ACTIVE, entity.getStatus());
        assertEquals(1.0, entity.getConfidenceScore());
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("Should require canonicalName")
    void testRequiredCanonicalName() {
        assertThrows(NullPointerException.class, () ->
                Entity.builder()
                        .type(EntityType.COMPANY)
                        .build()
        );
    }

    @Test
    @DisplayName("Should require type")
    void testRequiredType() {
        assertThrows(NullPointerException.class, () ->
                Entity.builder()
                        .canonicalName("Test")
                        .build()
        );
    }

    @Test
    @DisplayName("Should allow custom ID")
    void testCustomId() {
        Entity entity = Entity.builder()
                .id("custom-id-123")
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();

        assertEquals("custom-id-123", entity.getId());
    }

    @Test
    @DisplayName("Should update timestamps on modification")
    void testTimestampUpdate() throws InterruptedException {
        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();

        Instant originalUpdatedAt = entity.getUpdatedAt();
        Thread.sleep(10); // Ensure time passes

        entity.setCanonicalName("Updated Name");
        assertTrue(entity.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    @DisplayName("Should correctly report active/merged status")
    void testStatusMethods() {
        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();

        assertTrue(entity.isActive());
        assertFalse(entity.isMerged());

        entity.setStatus(EntityStatus.MERGED);
        assertFalse(entity.isActive());
        assertTrue(entity.isMerged());
    }

    @Test
    @DisplayName("Should have correct equals and hashCode based on ID")
    void testEqualsHashCode() {
        Entity entity1 = Entity.builder()
                .id("same-id")
                .canonicalName("Entity One")
                .type(EntityType.COMPANY)
                .build();

        Entity entity2 = Entity.builder()
                .id("same-id")
                .canonicalName("Entity Two")
                .type(EntityType.COMPANY)
                .build();

        Entity entity3 = Entity.builder()
                .id("different-id")
                .canonicalName("Entity One")
                .type(EntityType.COMPANY)
                .build();

        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());
        assertNotEquals(entity1, entity3);
    }

    @Test
    @DisplayName("Should copy entity with builder")
    void testCopyBuilder() {
        Entity original = Entity.builder()
                .canonicalName("Original")
                .normalizedName("original")
                .type(EntityType.COMPANY)
                .confidenceScore(0.95)
                .build();

        Entity copy = Entity.builder(original)
                .canonicalName("Modified")
                .build();

        assertEquals(original.getId(), copy.getId());
        assertEquals("Modified", copy.getCanonicalName());
        assertEquals("original", copy.getNormalizedName());
        assertEquals(0.95, copy.getConfidenceScore());
    }
}
