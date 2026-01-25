package com.entity.resolution.core.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Relationship - library-managed relationships.
 */
class RelationshipTest {

    @Test
    void testBuilderDefaults() {
        Relationship rel = Relationship.builder()
                .sourceEntityId("source-123")
                .targetEntityId("target-456")
                .relationshipType("PARTNER")
                .build();

        assertNotNull(rel.getId());
        assertEquals("source-123", rel.getSourceEntityId());
        assertEquals("target-456", rel.getTargetEntityId());
        assertEquals("PARTNER", rel.getRelationshipType());
        assertNotNull(rel.getCreatedAt());
        assertTrue(rel.getProperties().isEmpty());
    }

    @Test
    void testBuilderWithAllFields() {
        Instant now = Instant.now();
        Map<String, Object> props = Map.of("strength", 0.9, "notes", "Strong partnership");

        Relationship rel = Relationship.builder()
                .id("rel-789")
                .sourceEntityId("source-123")
                .targetEntityId("target-456")
                .relationshipType("PARTNER")
                .properties(props)
                .createdAt(now)
                .createdBy("test-system")
                .build();

        assertEquals("rel-789", rel.getId());
        assertEquals("source-123", rel.getSourceEntityId());
        assertEquals("target-456", rel.getTargetEntityId());
        assertEquals("PARTNER", rel.getRelationshipType());
        assertEquals(props, rel.getProperties());
        assertEquals(now, rel.getCreatedAt());
        assertEquals("test-system", rel.getCreatedBy());
    }

    @Test
    void testPropertiesAreImmutable() {
        Map<String, Object> props = Map.of("key", "value");
        Relationship rel = Relationship.builder()
                .sourceEntityId("source-123")
                .targetEntityId("target-456")
                .relationshipType("PARTNER")
                .properties(props)
                .build();

        assertThrows(UnsupportedOperationException.class, () ->
                rel.getProperties().put("new", "value"));
    }

    @Test
    void testEqualityById() {
        Relationship rel1 = Relationship.builder()
                .id("rel-123")
                .sourceEntityId("source-a")
                .targetEntityId("target-a")
                .relationshipType("TYPE_A")
                .build();

        Relationship rel2 = Relationship.builder()
                .id("rel-123")
                .sourceEntityId("source-b")
                .targetEntityId("target-b")
                .relationshipType("TYPE_B")
                .build();

        // Same ID means equal
        assertEquals(rel1, rel2);
        assertEquals(rel1.hashCode(), rel2.hashCode());
    }

    @Test
    void testInequalityDifferentIds() {
        Relationship rel1 = Relationship.builder()
                .id("rel-123")
                .sourceEntityId("source-123")
                .targetEntityId("target-456")
                .relationshipType("PARTNER")
                .build();

        Relationship rel2 = Relationship.builder()
                .id("rel-456")
                .sourceEntityId("source-123")
                .targetEntityId("target-456")
                .relationshipType("PARTNER")
                .build();

        assertNotEquals(rel1, rel2);
    }

    @Test
    void testNullSourceThrows() {
        assertThrows(NullPointerException.class, () ->
                Relationship.builder()
                        .targetEntityId("target-456")
                        .relationshipType("PARTNER")
                        .build());
    }

    @Test
    void testNullTargetThrows() {
        assertThrows(NullPointerException.class, () ->
                Relationship.builder()
                        .sourceEntityId("source-123")
                        .relationshipType("PARTNER")
                        .build());
    }

    @Test
    void testNullRelationshipTypeThrows() {
        assertThrows(NullPointerException.class, () ->
                Relationship.builder()
                        .sourceEntityId("source-123")
                        .targetEntityId("target-456")
                        .build());
    }

    @Test
    void testToString() {
        Relationship rel = Relationship.builder()
                .id("rel-123")
                .sourceEntityId("source-123")
                .targetEntityId("target-456")
                .relationshipType("PARTNER")
                .build();

        String str = rel.toString();
        assertTrue(str.contains("rel-123"));
        assertTrue(str.contains("source-123"));
        assertTrue(str.contains("target-456"));
        assertTrue(str.contains("PARTNER"));
    }
}
