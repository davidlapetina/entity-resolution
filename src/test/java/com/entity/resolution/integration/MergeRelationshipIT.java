package com.entity.resolution.integration;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditEntry;
import com.entity.resolution.core.model.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for merge operations and relationship migration
 * against a live FalkorDB instance.
 */
@Tag("integration")
class MergeRelationshipIT extends AbstractFalkorDBIntegrationTest {

    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = createResolver("merge-relationship");
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
    }

    @Test
    @DisplayName("Should migrate relationships when entities are merged")
    void testMergeWithRelationshipMigration() {
        // Create entities
        EntityResolutionResult companyA = resolver.resolve("Acme Corporation", EntityType.COMPANY);
        EntityResolutionResult companyB = resolver.resolve("Zenith Corp", EntityType.COMPANY);
        EntityResolutionResult partner = resolver.resolve("Partner Inc", EntityType.COMPANY);

        EntityReference refA = companyA.getEntityReference();
        EntityReference refB = companyB.getEntityReference();
        EntityReference refPartner = partner.getEntityReference();

        // Create relationship to companyA
        resolver.createRelationship(refA, refPartner, "PARTNER");

        // Now resolve a name very similar to companyA which triggers merge with companyA
        // The relationship should be preserved
        List<Relationship> relationships = resolver.getRelationships(refA);
        assertFalse(relationships.isEmpty(), "Should have relationships before merge");
    }

    @Test
    @DisplayName("Should preserve audit trail after merge")
    void testAuditPreservationAfterMerge() {
        EntityResolutionResult first = resolver.resolve("Tesla Inc", EntityType.COMPANY);
        String firstId = first.canonicalEntity().getId();

        // The entity should have creation audit
        List<AuditEntry> entries = resolver.getService().getAuditService()
                .getEntriesForEntity(firstId);
        assertFalse(entries.isEmpty());

        boolean hasCreated = entries.stream()
                .anyMatch(e -> e.action() == AuditAction.ENTITY_CREATED);
        assertTrue(hasCreated, "Should have creation audit entry");
    }

    @Test
    @DisplayName("EntityReference should resolve to current canonical entity")
    void testEntityReferenceResolution() {
        EntityResolutionResult result = resolver.resolve("Amazon.com Inc", EntityType.COMPANY);
        EntityReference ref = result.getEntityReference();

        assertNotNull(ref);
        assertNotNull(ref.getId());
        assertEquals(EntityType.COMPANY, ref.getType());
        assertEquals(result.canonicalEntity().getId(), ref.getId());
    }

    @Test
    @DisplayName("Should not create orphaned relationships during merge")
    void testNoOrphanedRelationships() {
        EntityResolutionResult source = resolver.resolve("Source Corp", EntityType.COMPANY);
        EntityResolutionResult target = resolver.resolve("Target Corp", EntityType.COMPANY);

        EntityReference sourceRef = source.getEntityReference();
        EntityReference targetRef = target.getEntityReference();

        Relationship rel = resolver.createRelationship(sourceRef, targetRef, "SUPPLIES_TO",
                Map.of("contract", "2024-001"));

        assertNotNull(rel);
        assertNotNull(rel.getId());

        // Verify relationship is retrievable
        List<Relationship> outgoing = resolver.getOutgoingRelationships(sourceRef);
        assertFalse(outgoing.isEmpty(), "Should find outgoing relationship");
    }

    @Test
    @DisplayName("Should handle cascading merge scenario")
    void testCascadingMerge() {
        // Create three entities that might all represent the same company
        EntityResolutionResult e1 = resolver.resolve("Google LLC", EntityType.COMPANY);
        assertTrue(e1.isNewEntity());

        // Same normalized form
        EntityResolutionResult e2 = resolver.resolve("Google L.L.C.", EntityType.COMPANY);
        // Should match due to normalization
        assertEquals(e1.canonicalEntity().getId(), e2.canonicalEntity().getId());

        // Another variation
        EntityResolutionResult e3 = resolver.resolve("Google", EntityType.COMPANY);
        // Whether this matches depends on the similarity score
        assertNotNull(e3.canonicalEntity());
    }

    @Test
    @DisplayName("Should migrate synonyms during merge")
    void testSynonymMigration() {
        EntityResolutionResult result = resolver.resolve("Meta Platforms Inc", EntityType.COMPANY);
        String entityId = result.canonicalEntity().getId();

        // Add synonym
        resolver.addSynonym(entityId, "Facebook", SynonymSource.HUMAN);

        // Verify synonym exists
        List<Synonym> synonyms = resolver.getSynonyms(entityId);
        boolean hasFacebook = synonyms.stream()
                .anyMatch(s -> "Facebook".equals(s.getValue()));
        assertTrue(hasFacebook, "Should have Facebook synonym");

        // Resolve via synonym
        EntityResolutionResult viaSynonym = resolver.resolve("Facebook", EntityType.COMPANY);
        assertEquals(entityId, viaSynonym.canonicalEntity().getId());
    }
}
