package com.entity.resolution.integration;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditEntry;
import com.entity.resolution.core.model.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the full entity resolution workflow against a live FalkorDB instance.
 */
@Tag("integration")
class ResolutionWorkflowIT extends AbstractFalkorDBIntegrationTest {

    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = createResolver("resolution-workflow");
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
    }

    @Test
    @DisplayName("Should create a new entity and retrieve it by exact match")
    void testCreateAndResolveExactMatch() {
        EntityResolutionResult created = resolver.resolve("Microsoft Corporation", EntityType.COMPANY);
        assertTrue(created.isNewEntity());
        assertNotNull(created.canonicalEntity());
        assertEquals("Microsoft Corporation", created.canonicalEntity().getCanonicalName());

        // Resolve again - should find exact match
        EntityResolutionResult found = resolver.resolve("Microsoft Corporation", EntityType.COMPANY);
        assertFalse(found.isNewEntity());
        assertEquals(created.canonicalEntity().getId(), found.canonicalEntity().getId());
    }

    @Test
    @DisplayName("Should match entities through normalization")
    void testNormalizationMatch() {
        EntityResolutionResult first = resolver.resolve("Apple Inc.", EntityType.COMPANY);
        assertTrue(first.isNewEntity());

        EntityResolutionResult second = resolver.resolve("Apple Incorporated", EntityType.COMPANY);
        assertFalse(second.isNewEntity());
        assertEquals(first.canonicalEntity().getId(), second.canonicalEntity().getId());
    }

    @Test
    @DisplayName("Should detect fuzzy matches above threshold")
    void testFuzzyMatch() {
        resolver.resolve("International Business Machines", EntityType.COMPANY);

        EntityResolutionResult result = resolver.resolve(
                "International Busines Machines", EntityType.COMPANY);
        // Slight typo should still match (high similarity)
        assertFalse(result.isNewEntity());
    }

    @Test
    @DisplayName("Should resolve entities via synonym lookup")
    void testSynonymResolution() {
        EntityResolutionResult created = resolver.resolve("International Business Machines", EntityType.COMPANY);
        String entityId = created.canonicalEntity().getId();

        // Add synonym
        resolver.addSynonym(entityId, "IBM", SynonymSource.HUMAN);

        // Resolve via synonym
        EntityResolutionResult viaSynonym = resolver.resolve("IBM", EntityType.COMPANY);
        assertFalse(viaSynonym.isNewEntity());
        assertEquals(entityId, viaSynonym.canonicalEntity().getId());
    }

    @Test
    @DisplayName("Should use blocking keys for candidate discovery")
    void testBlockingKeys() {
        // Create several entities
        resolver.resolve("Acme Corporation", EntityType.COMPANY);
        resolver.resolve("Zenith Systems", EntityType.COMPANY);
        resolver.resolve("Global Industries", EntityType.COMPANY);

        // Resolve a similar name - should find via blocking keys
        EntityResolutionResult result = resolver.resolve("Acme Corp", EntityType.COMPANY);
        assertFalse(result.isNewEntity(), "Should match via normalization/blocking keys");
    }

    @Test
    @DisplayName("Should create audit trail entries for resolution operations")
    void testAuditTrail() {
        EntityResolutionResult result = resolver.resolve("Audit Test Corp", EntityType.COMPANY);
        String entityId = result.canonicalEntity().getId();

        List<AuditEntry> entries = resolver.getService().getAuditService()
                .getEntriesForEntity(entityId);
        assertFalse(entries.isEmpty(), "Should have audit entries");

        boolean hasCreatedEntry = entries.stream()
                .anyMatch(e -> e.action() == AuditAction.ENTITY_CREATED);
        assertTrue(hasCreatedEntry, "Should have ENTITY_CREATED audit entry");
    }

    @Test
    @DisplayName("Should separate entities by type")
    void testEntityTypeSeparation() {
        EntityResolutionResult company = resolver.resolve("Apple", EntityType.COMPANY);
        EntityResolutionResult product = resolver.resolve("Apple", EntityType.PRODUCT);

        assertNotEquals(
                company.canonicalEntity().getId(),
                product.canonicalEntity().getId(),
                "Same name with different types should create separate entities"
        );
    }

    @Test
    @DisplayName("Should handle special characters in entity names")
    void testSpecialCharacters() {
        EntityResolutionResult result = resolver.resolve("O'Reilly & Associates", EntityType.COMPANY);
        assertTrue(result.isNewEntity());
        assertNotNull(result.canonicalEntity().getId());

        EntityResolutionResult found = resolver.resolve("O'Reilly & Associates", EntityType.COMPANY);
        assertFalse(found.isNewEntity());
    }
}
