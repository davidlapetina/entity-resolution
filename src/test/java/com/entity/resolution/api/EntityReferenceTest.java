package com.entity.resolution.api;

import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EntityReference - the opaque handle to canonical entities.
 */
class EntityReferenceTest {

    @Test
    void testStaticReference() {
        EntityReference ref = EntityReference.of("entity-123", EntityType.COMPANY);

        assertEquals("entity-123", ref.getId());
        assertEquals("entity-123", ref.getOriginalId());
        assertEquals(EntityType.COMPANY, ref.getType());
        assertFalse(ref.wasMerged());
    }

    @Test
    void testResolverReference() {
        AtomicReference<String> currentId = new AtomicReference<>("entity-123");

        EntityReference ref = EntityReference.withResolver(
                "entity-123",
                EntityType.COMPANY,
                currentId::get
        );

        assertEquals("entity-123", ref.getId());
        assertEquals("entity-123", ref.getOriginalId());
        assertFalse(ref.wasMerged());

        // Simulate a merge
        currentId.set("entity-456");

        assertEquals("entity-456", ref.getId());
        assertEquals("entity-123", ref.getOriginalId());
        assertTrue(ref.wasMerged());
    }

    @Test
    void testEqualityByCanonicalId() {
        AtomicReference<String> ref1Resolver = new AtomicReference<>("canonical-id");
        AtomicReference<String> ref2Resolver = new AtomicReference<>("canonical-id");

        EntityReference ref1 = EntityReference.withResolver("original-1", EntityType.COMPANY, ref1Resolver::get);
        EntityReference ref2 = EntityReference.withResolver("original-2", EntityType.COMPANY, ref2Resolver::get);

        // Even with different original IDs, if they resolve to same canonical, they're equal
        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    void testInequalityDifferentTypes() {
        EntityReference ref1 = EntityReference.of("entity-123", EntityType.COMPANY);
        EntityReference ref2 = EntityReference.of("entity-123", EntityType.PERSON);

        assertNotEquals(ref1, ref2);
    }

    @Test
    void testToStringNonMerged() {
        EntityReference ref = EntityReference.of("entity-123", EntityType.COMPANY);
        String str = ref.toString();

        assertTrue(str.contains("entity-123"));
        assertTrue(str.contains("COMPANY"));
        assertFalse(str.contains("canonicalId"));
    }

    @Test
    void testToStringMerged() {
        AtomicReference<String> currentId = new AtomicReference<>("entity-456");

        EntityReference ref = EntityReference.withResolver("entity-123", EntityType.COMPANY, currentId::get);
        String str = ref.toString();

        assertTrue(str.contains("originalId='entity-123'"));
        assertTrue(str.contains("canonicalId='entity-456'"));
    }

    @Test
    void testNullOriginalIdThrows() {
        assertThrows(NullPointerException.class, () ->
                EntityReference.of(null, EntityType.COMPANY));
    }

    @Test
    void testNullTypeThrows() {
        assertThrows(NullPointerException.class, () ->
                EntityReference.of("entity-123", null));
    }

    @Test
    void testNullResolverThrows() {
        assertThrows(NullPointerException.class, () ->
                EntityReference.withResolver("entity-123", EntityType.COMPANY, null));
    }
}
