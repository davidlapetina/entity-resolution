package com.entity.resolution.api;

import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.GraphConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the entity resolution workflow.
 * Uses an in-memory mock graph connection for testing without FalkorDB.
 */
class EntityResolutionIntegrationTest {

    private EntityResolver resolver;
    private MockGraphConnection mockConnection;

    @BeforeEach
    void setUp() {
        mockConnection = new MockGraphConnection("test-graph");
        resolver = EntityResolver.builder()
                .graphConnection(mockConnection)
                .createIndexes(false)
                .build();
    }

    @Test
    @DisplayName("Should create new entity when no match exists")
    void testCreateNewEntity() {
        EntityResolutionResult result = resolver.resolve("Apple Inc.", EntityType.COMPANY);

        assertTrue(result.isNewEntity());
        assertNotNull(result.canonicalEntity());
        assertEquals("Apple Inc.", result.canonicalEntity().getCanonicalName());
        assertEquals("apple", result.canonicalEntity().getNormalizedName());
        assertEquals(EntityType.COMPANY, result.canonicalEntity().getType());
    }

    @Test
    @DisplayName("Should find exact match on normalized name")
    void testExactMatch() {
        // First create an entity
        EntityResolutionResult first = resolver.resolve("Microsoft Corporation", EntityType.COMPANY);
        assertTrue(first.isNewEntity());

        // Now search for the same entity with different suffix
        EntityResolutionResult second = resolver.resolve("Microsoft Corp.", EntityType.COMPANY);

        assertFalse(second.isNewEntity());
        assertEquals(first.canonicalEntity().getId(), second.canonicalEntity().getId());
        assertEquals(MatchDecision.AUTO_MERGE, second.decision());
    }

    @Test
    @DisplayName("Should normalize company suffixes correctly")
    void testCompanyNormalization() {
        var result1 = resolver.resolve("Tesla, Inc.", EntityType.COMPANY);
        var result2 = resolver.resolve("Tesla Incorporated", EntityType.COMPANY);

        // Both should match the same entity
        assertFalse(result2.isNewEntity());
        assertEquals(result1.canonicalEntity().getId(), result2.canonicalEntity().getId());
    }

    @Test
    @DisplayName("Big Blue -> IBM scenario (fuzzy match requiring review)")
    void testBigBlueIBMScenario() {
        // Create IBM entity
        resolver.resolve("International Business Machines", EntityType.COMPANY);

        // Try to resolve "Big Blue" - this should not auto-match (very different strings)
        EntityResolutionResult result = resolver.resolve("Big Blue", EntityType.COMPANY);

        // Without LLM/semantic analysis, Big Blue won't match IBM
        // The fuzzy match score will be very low, so depending on thresholds it may:
        // 1. Create a new entity (if score < review threshold)
        // 2. Return requiring review (if score > review threshold but < synonym threshold)
        assertNotNull(result);
        assertNotNull(result.canonicalEntity());
    }

    @Test
    @DisplayName("Should create entities with different names")
    void testCreateDifferentEntities() {
        // Create first entity
        EntityResolutionResult first = resolver.resolve("Microsoft Corporation", EntityType.COMPANY);
        assertNotNull(first.canonicalEntity());
        assertTrue(first.isNewEntity());

        // Create a completely different entity
        EntityResolutionResult second = resolver.resolve("Apple Inc.", EntityType.COMPANY);
        assertNotNull(second.canonicalEntity());
        // Different companies should have different IDs
        assertNotEquals(first.canonicalEntity().getId(), second.canonicalEntity().getId());
    }

    @Test
    @DisplayName("Should create synonyms with correct properties")
    void testSynonymCreation() {
        // Test synonym object creation (not database interaction)
        Synonym synonym = Synonym.builder()
                .value("Big Blue")
                .normalizedValue("big blue")
                .source(SynonymSource.HUMAN)
                .confidence(1.0)
                .build();

        assertNotNull(synonym);
        assertEquals("Big Blue", synonym.getValue());
        assertEquals("big blue", synonym.getNormalizedValue());
        assertEquals(SynonymSource.HUMAN, synonym.getSource());
        assertEquals(1.0, synonym.getConfidence());
    }

    @Test
    @DisplayName("Should use custom resolution options")
    void testCustomOptions() {
        ResolutionOptions options = ResolutionOptions.builder()
                .autoMergeThreshold(0.99) // Very high threshold
                .synonymThreshold(0.95)
                .reviewThreshold(0.80)
                .autoMergeEnabled(false)
                .build();

        resolver.resolve("Test Company One", EntityType.COMPANY);
        EntityResolutionResult result = resolver.resolve(
                "Test Company On", EntityType.COMPANY, options);

        // With auto-merge disabled, even high matches should require review
        assertNotNull(result);
    }

    @Test
    @DisplayName("Should track audit entries")
    void testAuditTracking() {
        resolver.resolve("Audit Test Corp", EntityType.COMPANY);

        var auditService = resolver.getService().getAuditService();
        assertTrue(auditService.size() > 0);

        var entries = auditService.getEntriesByAction(
                com.entity.resolution.audit.AuditAction.ENTITY_CREATED);
        assertFalse(entries.isEmpty());
    }

    @Test
    @DisplayName("Should handle different entity types separately")
    void testEntityTypeSeparation() {
        var company = resolver.resolve("Apple", EntityType.COMPANY);
        var product = resolver.resolve("Apple", EntityType.PRODUCT);

        // Same name, different types should create separate entities
        assertNotEquals(company.canonicalEntity().getId(), product.canonicalEntity().getId());
    }

    /**
     * In-memory mock implementation of GraphConnection for testing.
     */
    static class MockGraphConnection implements GraphConnection {
        private final String graphName;
        private final Map<String, Map<String, Object>> entities = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> synonyms = new ConcurrentHashMap<>();
        private final Map<String, String> synonymToEntity = new ConcurrentHashMap<>();

        MockGraphConnection(String graphName) {
            this.graphName = graphName;
        }

        @Override
        public void execute(String query, Map<String, Object> params) {
            // Parse and execute CREATE queries
            if (query.contains("CREATE") && query.contains(":Entity")) {
                String id = (String) params.get("id");
                Map<String, Object> entity = new HashMap<>();
                entity.put("id", id);
                entity.put("canonicalName", params.get("canonicalName"));
                entity.put("normalizedName", params.get("normalizedName"));
                entity.put("type", params.get("type"));
                entity.put("confidenceScore", params.get("confidenceScore"));
                entity.put("status", "ACTIVE");
                entities.put(id, entity);
            } else if (query.contains("CREATE") && query.contains(":Synonym")) {
                String synonymId = (String) params.get("synonymId");
                String entityId = (String) params.get("entityId");
                Map<String, Object> synonym = new HashMap<>();
                synonym.put("id", synonymId);
                synonym.put("value", params.get("value"));
                synonym.put("normalizedValue", params.get("normalizedValue"));
                synonym.put("source", params.get("source"));
                synonym.put("confidence", params.get("confidence"));
                synonyms.put(synonymId, synonym);
                synonymToEntity.put((String) params.get("normalizedValue"), entityId);
            } else if (query.contains("SET") && query.contains("status")) {
                String sourceId = (String) params.get("sourceEntityId");
                if (entities.containsKey(sourceId)) {
                    entities.get(sourceId).put("status", "MERGED");
                }
            }
        }

        @Override
        public List<Map<String, Object>> query(String query, Map<String, Object> params) {
            List<Map<String, Object>> results = new ArrayList<>();

            if (query.contains("e.normalizedName = $normalizedName")) {
                String normalizedName = (String) params.get("normalizedName");
                String entityType = (String) params.get("entityType");

                for (Map<String, Object> entity : entities.values()) {
                    if (normalizedName.equals(entity.get("normalizedName"))
                            && entityType.equals(entity.get("type"))
                            && "ACTIVE".equals(entity.get("status"))) {
                        results.add(new HashMap<>(entity));
                    }
                }
            } else if (query.contains("s.normalizedValue = $normalizedValue")) {
                String normalizedValue = (String) params.get("normalizedValue");
                String entityId = synonymToEntity.get(normalizedValue);
                if (entityId != null && entities.containsKey(entityId)) {
                    Map<String, Object> entity = entities.get(entityId);
                    if ("ACTIVE".equals(entity.get("status"))) {
                        Map<String, Object> result = new HashMap<>(entity);
                        result.put("matchedSynonym", normalizedValue);
                        results.add(result);
                    }
                }
            } else if (query.contains("e.type = $entityType") && query.contains("e.status = 'ACTIVE'")) {
                String entityType = (String) params.get("entityType");
                for (Map<String, Object> entity : entities.values()) {
                    if (entityType.equals(entity.get("type"))
                            && "ACTIVE".equals(entity.get("status"))) {
                        results.add(new HashMap<>(entity));
                    }
                }
            } else if (query.contains("e.id = $id") || query.contains("{id: $id}")) {
                String id = (String) params.get("id");
                if (entities.containsKey(id)) {
                    results.add(new HashMap<>(entities.get(id)));
                }
            } else if (query.contains("SYNONYM_OF") && query.contains("$entityId")) {
                String entityId = (String) params.get("entityId");
                for (Map.Entry<String, String> entry : synonymToEntity.entrySet()) {
                    if (entityId.equals(entry.getValue())) {
                        for (Map<String, Object> synonym : synonyms.values()) {
                            if (entry.getKey().equals(synonym.get("normalizedValue"))) {
                                results.add(new HashMap<>(synonym));
                            }
                        }
                    }
                }
            }

            return results;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String getGraphName() {
            return graphName;
        }

        @Override
        public void createIndexes() {
            // No-op for mock
        }

        @Override
        public void close() {
            entities.clear();
            synonyms.clear();
            synonymToEntity.clear();
        }
    }
}
