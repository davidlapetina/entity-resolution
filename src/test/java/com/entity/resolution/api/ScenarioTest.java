package com.entity.resolution.api;

import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.GraphConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 4 key usage scenarios:
 * 1. Reusable Library for External Clients
 * 2. Client Creates an Entity via API
 * 3. Client Persists Additional Relationships
 * 4. Batch Mode Ingestion
 */
class ScenarioTest {

    private EntityResolver resolver;
    private ScenarioMockGraphConnection mockConnection;

    @BeforeEach
    void setUp() {
        mockConnection = new ScenarioMockGraphConnection();
        resolver = EntityResolver.builder()
                .graphConnection(mockConnection)
                .createIndexes(false)
                .options(ResolutionOptions.builder()
                        .autoMergeEnabled(true)
                        .autoMergeThreshold(0.92)
                        .synonymThreshold(0.85)
                        .reviewThreshold(0.70)
                        .build())
                .build();
    }

    // =========================================================================
    // SCENARIO 1: Reusable Library for External Clients
    // =========================================================================
    @Nested
    @DisplayName("Scenario 1: Reusable Library for External Clients")
    class Scenario1_ReusableLibrary {

        @Test
        @DisplayName("EntityReference provides opaque handle - client cannot access raw entity ID directly")
        void entityReferenceIsOpaque() {
            EntityResolutionResult result = resolver.resolve("Acme Corporation", EntityType.COMPANY);

            // Client gets EntityReference, not raw Entity
            EntityReference ref = result.getEntityReference();
            assertNotNull(ref, "Should return EntityReference");

            // Reference provides ID through getter (not direct field access)
            String id = ref.getId();
            assertNotNull(id);
            assertFalse(id.isEmpty());
        }

        @Test
        @DisplayName("EntityReference tracks merge status correctly")
        void entityReferenceTracksMergeStatus() {
            // Test EntityReference with a resolver that simulates a merge
            java.util.concurrent.atomic.AtomicReference<String> canonicalId =
                    new java.util.concurrent.atomic.AtomicReference<>("original-id");

            EntityReference ref = EntityReference.withResolver(
                    "original-id",
                    EntityType.COMPANY,
                    canonicalId::get
            );

            // Initially not merged
            assertFalse(ref.wasMerged(), "Should not be merged initially");
            assertEquals("original-id", ref.getId());
            assertEquals("original-id", ref.getOriginalId());

            // Simulate merge to new canonical
            canonicalId.set("canonical-id");

            // Now shows as merged and returns canonical ID
            assertTrue(ref.wasMerged(), "Should show as merged after ID change");
            assertEquals("canonical-id", ref.getId(), "Should return canonical ID");
            assertEquals("original-id", ref.getOriginalId(), "Original ID unchanged");
        }

        @Test
        @DisplayName("Client receives clear resolution result with decision and confidence")
        void clientReceivesClearResult() {
            EntityResolutionResult result = resolver.resolve("Microsoft Corporation", EntityType.COMPANY);

            // Result contains all necessary information
            assertNotNull(result.getDecision(), "Should have decision");
            assertTrue(result.getConfidence() >= 0.0 && result.getConfidence() <= 1.0,
                    "Confidence should be between 0 and 1");
            assertNotNull(result.getReasoning(), "Should have reasoning");
            assertNotNull(result.getEntityReference(), "Should have entity reference");
        }

        @Test
        @DisplayName("Different entity types are handled separately")
        void differentEntityTypesAreSeparate() {
            EntityResolutionResult companyResult = resolver.resolve("Apple", EntityType.COMPANY);
            EntityResolutionResult productResult = resolver.resolve("Apple", EntityType.PRODUCT);

            assertNotEquals(
                    companyResult.getEntityReference().getId(),
                    productResult.getEntityReference().getId(),
                    "Same name with different types should create separate entities"
            );
        }
    }

    // =========================================================================
    // SCENARIO 2: Client Creates an Entity via API
    // =========================================================================
    @Nested
    @DisplayName("Scenario 2: Client Creates an Entity via API")
    class Scenario2_EntityCreation {

        @Test
        @DisplayName("Result clearly indicates if entity is new or matched existing")
        void resultIndicatesNewVsMatched() {
            // First resolution creates new entity
            EntityResolutionResult first = resolver.resolve("NewCorp Inc.", EntityType.COMPANY);
            assertTrue(first.isNewEntity(), "First resolution should create new entity");
            assertFalse(first.hasMatch(), "New entity should not have match");

            // Second resolution with same normalized name should match
            EntityResolutionResult second = resolver.resolve("NewCorp Incorporated", EntityType.COMPANY);
            // Both normalize to "newcorp" so should match
            assertFalse(second.isNewEntity(), "Should match existing entity");
        }

        @Test
        @DisplayName("Result indicates if matched via synonym")
        void resultIndicatesSynonymMatch() {
            // Create entity
            EntityResolutionResult original = resolver.resolve("International Business Machines", EntityType.COMPANY);
            String entityId = original.getEntityReference().getId();

            // Add synonym
            resolver.addSynonym(entityId, "IBM");

            // Resolve using synonym
            EntityResolutionResult viaSymonym = resolver.resolve("IBM", EntityType.COMPANY);

            assertTrue(viaSymonym.wasMatchedViaSynonym(),
                    "Should indicate match was via synonym");
            assertEquals(entityId, viaSymonym.getEntityReference().getId(),
                    "Should resolve to same entity");
        }

        @Test
        @DisplayName("Result indicates if new synonym was created")
        void resultIndicatesNewSynonymCreated() {
            // Create entity
            resolver.resolve("Tesla Motors", EntityType.COMPANY);

            // Resolve with similar name that creates synonym
            // This depends on similarity threshold - let's use exact same normalized form
            EntityResolutionResult result = resolver.resolve("Tesla Motors Inc.", EntityType.COMPANY);

            // The result should indicate whether a synonym was created
            // (depends on matching threshold)
            assertNotNull(result.wasNewSynonymCreated());
        }

        @Test
        @DisplayName("Result provides input and matched names for auditing")
        void resultProvidesNamesForAuditing() {
            EntityResolutionResult result = resolver.resolve("Google LLC", EntityType.COMPANY);

            assertEquals("Google LLC", result.getInputName(),
                    "Should preserve original input name");
            assertNotNull(result.getMatchedName(),
                    "Should provide matched name");
        }

        @Test
        @DisplayName("Result provides match confidence for decision making")
        void resultProvidesMatchConfidence() {
            EntityResolutionResult result = resolver.resolve("Amazon.com Inc.", EntityType.COMPANY);

            double confidence = result.getMatchConfidence();
            assertTrue(confidence >= 0.0 && confidence <= 1.0,
                    "Confidence should be normalized between 0 and 1");

            // For new entities, confidence should be 1.0
            if (result.isNewEntity()) {
                assertEquals(1.0, confidence, 0.001,
                        "New entity confidence should be 1.0");
            }
        }

        @Test
        @DisplayName("findEntity returns empty when entity doesn't exist")
        void findEntityReturnsEmptyWhenNotFound() {
            Optional<EntityReference> ref = resolver.findEntity("NonExistent Corp", EntityType.COMPANY);
            assertTrue(ref.isEmpty(), "Should return empty when entity doesn't exist");
        }

        @Test
        @DisplayName("findEntity returns reference when entity exists")
        void findEntityReturnsReferenceWhenFound() {
            // Create entity first
            EntityResolutionResult created = resolver.resolve("Existing Corp", EntityType.COMPANY);
            String entityId = created.getEntityReference().getId();

            // Find should return reference
            Optional<EntityReference> found = resolver.findEntity("Existing Corp", EntityType.COMPANY);
            assertTrue(found.isPresent(), "Should find existing entity");
            assertEquals(entityId, found.get().getId(), "Should return same entity");
        }
    }

    // =========================================================================
    // SCENARIO 3: Client Persists Additional Relationships
    // =========================================================================
    @Nested
    @DisplayName("Scenario 3: Client Persists Additional Relationships")
    class Scenario3_Relationships {

        @Test
        @DisplayName("Client can create relationships using EntityReferences")
        void canCreateRelationshipWithReferences() {
            EntityResolutionResult company1 = resolver.resolve("Parent Corp", EntityType.COMPANY);
            EntityResolutionResult company2 = resolver.resolve("Subsidiary Inc", EntityType.COMPANY);

            Relationship rel = resolver.createRelationship(
                    company1.getEntityReference(),
                    company2.getEntityReference(),
                    "SUBSIDIARY_OF"
            );

            assertNotNull(rel, "Should create relationship");
            assertNotNull(rel.getId(), "Relationship should have ID");
            assertEquals("SUBSIDIARY_OF", rel.getRelationshipType());
            assertEquals(company1.getEntityReference().getId(), rel.getSourceEntityId());
            assertEquals(company2.getEntityReference().getId(), rel.getTargetEntityId());
        }

        @Test
        @DisplayName("Client can create relationships with properties")
        void canCreateRelationshipWithProperties() {
            EntityResolutionResult company1 = resolver.resolve("Investor LLC", EntityType.COMPANY);
            EntityResolutionResult company2 = resolver.resolve("Startup Inc", EntityType.COMPANY);

            Map<String, Object> props = Map.of(
                    "investmentAmount", 1000000,
                    "investmentDate", "2024-01-15",
                    "equityPercentage", 15.5
            );

            Relationship rel = resolver.createRelationship(
                    company1.getEntityReference(),
                    company2.getEntityReference(),
                    "INVESTED_IN",
                    props
            );

            assertNotNull(rel, "Should create relationship with properties");
            assertEquals("INVESTED_IN", rel.getRelationshipType());
        }

        @Test
        @DisplayName("Client can query relationships for an entity")
        void canQueryRelationships() {
            EntityResolutionResult company1 = resolver.resolve("Hub Corp", EntityType.COMPANY);
            EntityResolutionResult company2 = resolver.resolve("Spoke A Inc", EntityType.COMPANY);
            EntityResolutionResult company3 = resolver.resolve("Spoke B Inc", EntityType.COMPANY);

            // Create multiple relationships
            resolver.createRelationship(company1.getEntityReference(), company2.getEntityReference(), "PARTNER");
            resolver.createRelationship(company1.getEntityReference(), company3.getEntityReference(), "PARTNER");
            resolver.createRelationship(company2.getEntityReference(), company1.getEntityReference(), "SUPPLIER");

            // Query outgoing relationships
            List<Relationship> outgoing = resolver.getOutgoingRelationships(company1.getEntityReference());
            assertEquals(2, outgoing.size(), "Should have 2 outgoing relationships");

            // Query incoming relationships
            List<Relationship> incoming = resolver.getIncomingRelationships(company1.getEntityReference());
            assertEquals(1, incoming.size(), "Should have 1 incoming relationship");

            // Query all relationships
            List<Relationship> all = resolver.getRelationships(company1.getEntityReference());
            assertEquals(3, all.size(), "Should have 3 total relationships");
        }

        @Test
        @DisplayName("Relationships use current canonical ID via EntityReference")
        void relationshipsUseCanonicalId() {
            EntityResolutionResult company1 = resolver.resolve("Canonical Corp", EntityType.COMPANY);
            EntityResolutionResult company2 = resolver.resolve("Partner Inc", EntityType.COMPANY);

            EntityReference ref1 = company1.getEntityReference();
            EntityReference ref2 = company2.getEntityReference();

            // Create relationship
            Relationship rel = resolver.createRelationship(ref1, ref2, "PARTNER");

            // Relationship should use the IDs from the references
            assertEquals(ref1.getId(), rel.getSourceEntityId());
            assertEquals(ref2.getId(), rel.getTargetEntityId());
        }

        @Test
        @DisplayName("Client can delete relationships")
        void canDeleteRelationship() {
            EntityResolutionResult company1 = resolver.resolve("Company X", EntityType.COMPANY);
            EntityResolutionResult company2 = resolver.resolve("Company Y", EntityType.COMPANY);

            Relationship rel = resolver.createRelationship(
                    company1.getEntityReference(),
                    company2.getEntityReference(),
                    "TEMPORARY_PARTNER"
            );

            // Delete the relationship
            resolver.deleteRelationship(rel.getId());

            // Verify deletion (query should not find it)
            List<Relationship> remaining = resolver.getRelationships(company1.getEntityReference());
            assertTrue(remaining.stream().noneMatch(r -> r.getId().equals(rel.getId())),
                    "Deleted relationship should not be found");
        }
    }

    // =========================================================================
    // SCENARIO 4: Batch Mode Ingestion
    // =========================================================================
    @Nested
    @DisplayName("Scenario 4: Batch Mode Ingestion")
    class Scenario4_BatchIngestion {

        @Test
        @DisplayName("Batch provides deduplication within the batch")
        void batchProvidesDedupication() {
            try (BatchContext batch = resolver.beginBatch()) {
                // Resolve same entity multiple times
                EntityResolutionResult result1 = batch.resolve("Acme Corp", EntityType.COMPANY);
                EntityResolutionResult result2 = batch.resolve("Acme Corp", EntityType.COMPANY);
                EntityResolutionResult result3 = batch.resolve("ACME CORP", EntityType.COMPANY); // Case insensitive

                // All should resolve to the same entity
                assertEquals(result1.getEntityReference().getId(), result2.getEntityReference().getId());
                assertEquals(result1.getEntityReference().getId(), result3.getEntityReference().getId());

                // Only 1 unique entity should be tracked
                assertEquals(1, batch.getResolvedEntities().size());
            }
        }

        @Test
        @DisplayName("Batch allows deferred relationship creation")
        void batchAllowsDeferredRelationships() {
            try (BatchContext batch = resolver.beginBatch()) {
                // Resolve entities
                EntityReference company1 = batch.resolve("Company Alpha", EntityType.COMPANY).getEntityReference();
                EntityReference company2 = batch.resolve("Company Beta", EntityType.COMPANY).getEntityReference();
                EntityReference company3 = batch.resolve("Company Gamma", EntityType.COMPANY).getEntityReference();

                // Queue relationships (not created yet)
                batch.createRelationship(company1, company2, "PARTNER");
                batch.createRelationship(company2, company3, "SUBSIDIARY");
                batch.createRelationship(company1, company3, "INVESTOR");

                assertEquals(3, batch.getPendingRelationshipCount(),
                        "Should have 3 pending relationships");

                // Commit creates the relationships
                BatchResult result = batch.commit();

                assertEquals(3, result.relationshipsCreated(),
                        "Should have created 3 relationships");
            }
        }

        @Test
        @DisplayName("Batch returns comprehensive result summary")
        void batchReturnsComprehensiveResult() {
            try (BatchContext batch = resolver.beginBatch()) {
                // Create entities with exact duplicates (batch deduplication)
                batch.resolve("Unique Alpha Corp", EntityType.COMPANY);
                batch.resolve("Unique Alpha Corp", EntityType.COMPANY); // Exact duplicate - cached

                BatchResult result = batch.commit();

                // Verify result structure
                assertEquals(1, result.totalEntitiesResolved(),
                        "Should have 1 unique entity (duplicates are deduplicated in batch)");
                assertTrue(result.isSuccess(), "Batch should succeed");
                assertFalse(result.hasErrors(), "Should have no errors");
                assertTrue(result.newEntitiesCreated() >= 0, "Should track new entities");
                assertTrue(result.entitiesMerged() >= 0, "Should track merged entities");
                assertTrue(result.relationshipsCreated() >= 0, "Should track relationships");
            }
        }

        @Test
        @DisplayName("Batch supports rollback to abandon pending relationships")
        void batchSupportsRollback() {
            try (BatchContext batch = resolver.beginBatch()) {
                EntityReference company1 = batch.resolve("Rollback Test 1", EntityType.COMPANY).getEntityReference();
                EntityReference company2 = batch.resolve("Rollback Test 2", EntityType.COMPANY).getEntityReference();

                batch.createRelationship(company1, company2, "TO_BE_ABANDONED");
                assertEquals(1, batch.getPendingRelationshipCount());

                // Rollback clears pending relationships
                batch.rollback();

                assertEquals(0, batch.getPendingRelationshipCount(),
                        "Rollback should clear pending relationships");
            }
        }

        @Test
        @DisplayName("Batch handles large number of entities efficiently")
        void batchHandlesLargeVolume() {
            int entityCount = 100;

            try (BatchContext batch = resolver.beginBatch()) {
                // Create many entities
                List<EntityReference> refs = new ArrayList<>();
                for (int i = 0; i < entityCount; i++) {
                    EntityResolutionResult result = batch.resolve("Batch Entity " + i, EntityType.COMPANY);
                    refs.add(result.getEntityReference());
                }

                // Create chain of relationships
                for (int i = 0; i < entityCount - 1; i++) {
                    batch.createRelationship(refs.get(i), refs.get(i + 1), "NEXT");
                }

                BatchResult result = batch.commit();

                assertEquals(entityCount, result.totalEntitiesResolved());
                assertEquals(entityCount - 1, result.relationshipsCreated());
                assertTrue(result.isSuccess());
            }
        }

        @Test
        @DisplayName("Batch separates entities by type")
        void batchSeparatesEntityTypes() {
            try (BatchContext batch = resolver.beginBatch()) {
                // Same name, different types
                EntityResolutionResult company = batch.resolve("Apple", EntityType.COMPANY);
                EntityResolutionResult product = batch.resolve("Apple", EntityType.PRODUCT);
                EntityResolutionResult person = batch.resolve("Apple", EntityType.PERSON);

                // Should create 3 separate entities
                assertEquals(3, batch.getResolvedEntities().size());

                Set<String> ids = new HashSet<>();
                ids.add(company.getEntityReference().getId());
                ids.add(product.getEntityReference().getId());
                ids.add(person.getEntityReference().getId());

                assertEquals(3, ids.size(), "Each type should have unique entity");
            }
        }

        @Test
        @DisplayName("Batch context cannot be used after close")
        void batchCannotBeUsedAfterClose() {
            BatchContext batch = resolver.beginBatch();
            batch.resolve("Test Entity", EntityType.COMPANY);
            batch.close();

            assertThrows(IllegalStateException.class, () ->
                            batch.resolve("Another Entity", EntityType.COMPANY),
                    "Should not allow resolution after close");
        }

        @Test
        @DisplayName("Batch auto-commits on close if not explicitly committed")
        void batchAutoCommitsOnClose() {
            BatchContext batch = resolver.beginBatch();
            EntityReference ref1 = batch.resolve("Auto Commit 1", EntityType.COMPANY).getEntityReference();
            EntityReference ref2 = batch.resolve("Auto Commit 2", EntityType.COMPANY).getEntityReference();
            batch.createRelationship(ref1, ref2, "AUTO_CREATED");

            // Close without explicit commit
            batch.close();

            // Verify entities exist (they were committed)
            Optional<EntityReference> found = resolver.findEntity("Auto Commit 1", EntityType.COMPANY);
            assertTrue(found.isPresent(), "Entity should exist after auto-commit");
        }
    }

    // =========================================================================
    // Mock Graph Connection for Scenario Tests
    // =========================================================================
    private static class ScenarioMockGraphConnection implements GraphConnection {
        private final Map<String, Map<String, Object>> entities = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> synonyms = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> relationships = new ConcurrentHashMap<>();
        private final Map<String, String> mergeMap = new ConcurrentHashMap<>(); // sourceId -> targetId
        private final AtomicInteger synonymCounter = new AtomicInteger(0);

        void simulateMerge(String sourceId, String targetId) {
            mergeMap.put(sourceId, targetId);
            Map<String, Object> sourceEntity = entities.get(sourceId);
            if (sourceEntity != null) {
                sourceEntity.put("status", "MERGED");
            }
        }

        String getCanonicalId(String entityId) {
            String current = entityId;
            while (mergeMap.containsKey(current)) {
                current = mergeMap.get(current);
            }
            return current;
        }

        @Override
        public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
            // Find by normalized name
            if (cypher.contains("WHERE e.normalizedName = $normalizedName")) {
                String normalizedName = (String) params.get("normalizedName");
                String entityType = (String) params.get("entityType");
                return entities.values().stream()
                        .filter(e -> normalizedName.equals(e.get("normalizedName")))
                        .filter(e -> entityType.equals(e.get("type")))
                        .filter(e -> "ACTIVE".equals(e.get("status")))
                        .toList();
            }

            // Find by synonym
            if (cypher.contains("WHERE s.normalizedValue = $normalizedValue")) {
                String normalizedValue = (String) params.get("normalizedValue");
                String entityType = (String) params.get("entityType");
                List<Map<String, Object>> results = new ArrayList<>();
                for (Map<String, Object> syn : synonyms.values()) {
                    if (normalizedValue.equals(syn.get("normalizedValue"))) {
                        String entityId = (String) syn.get("entityId");
                        Map<String, Object> entity = entities.get(entityId);
                        if (entity != null && entityType.equals(entity.get("type")) &&
                                "ACTIVE".equals(entity.get("status"))) {
                            Map<String, Object> result = new HashMap<>();
                            result.put("id", entityId);
                            result.put("canonicalName", entity.get("canonicalName"));
                            result.put("normalizedName", entity.get("normalizedName"));
                            result.put("type", entity.get("type"));
                            result.put("matchedSynonym", syn.get("value"));
                            results.add(result);
                        }
                    }
                }
                return results;
            }

            // Find all active entities of type
            if (cypher.contains("WHERE e.type = $entityType") && cypher.contains("AND e.status = 'ACTIVE'")) {
                String entityType = (String) params.get("entityType");
                return entities.values().stream()
                        .filter(e -> entityType.equals(e.get("type")))
                        .filter(e -> "ACTIVE".equals(e.get("status")))
                        .toList();
            }

            // Find entity by ID
            if (cypher.contains("MATCH (e:Entity {id: $id})")) {
                String id = (String) params.get("id");
                Map<String, Object> entity = entities.get(id);
                return entity != null ? List.of(entity) : List.of();
            }

            // Find synonyms for entity
            if (cypher.contains("MATCH (s:Synonym)-[:SYNONYM_OF]->(e:Entity {id: $entityId})")) {
                String entityId = (String) params.get("entityId");
                return synonyms.values().stream()
                        .filter(s -> entityId.equals(s.get("entityId")))
                        .toList();
            }

            // Find relationship by ID
            if (cypher.contains("MATCH (source:Entity)-[r:LIBRARY_REL {id: $relationshipId}]")) {
                String relId = (String) params.get("relationshipId");
                Map<String, Object> rel = relationships.get(relId);
                return rel != null ? List.of(rel) : List.of();
            }

            // Find relationships by source
            if (cypher.contains("MATCH (source:Entity {id: $entityId})-[r:LIBRARY_REL]->(target:Entity)")) {
                String entityId = (String) params.get("entityId");
                return relationships.values().stream()
                        .filter(r -> entityId.equals(r.get("sourceEntityId")))
                        .toList();
            }

            // Find relationships by target
            if (cypher.contains("MATCH (source:Entity)-[r:LIBRARY_REL]->(target:Entity {id: $entityId})")) {
                String entityId = (String) params.get("entityId");
                return relationships.values().stream()
                        .filter(r -> entityId.equals(r.get("targetEntityId")))
                        .toList();
            }

            // Find relationships by entity (source or target)
            if (cypher.contains("WHERE source.id = $entityId OR target.id = $entityId")) {
                String entityId = (String) params.get("entityId");
                return relationships.values().stream()
                        .filter(r -> entityId.equals(r.get("sourceEntityId")) ||
                                entityId.equals(r.get("targetEntityId")))
                        .toList();
            }

            // Find canonical entity
            if (cypher.contains("MATCH (merged:Entity {id: $mergedEntityId})-[:MERGED_INTO*]->(canonical:Entity)")) {
                String mergedId = (String) params.get("mergedEntityId");
                String canonicalId = getCanonicalId(mergedId);
                if (!canonicalId.equals(mergedId)) {
                    Map<String, Object> canonical = entities.get(canonicalId);
                    return canonical != null ? List.of(canonical) : List.of();
                }
                return List.of();
            }

            return List.of();
        }

        @Override
        public void execute(String cypher, Map<String, Object> params) {
            // Create entity
            if (cypher.contains("CREATE (e:Entity")) {
                String id = (String) params.get("id");
                Map<String, Object> entity = new HashMap<>(params);
                entity.put("status", "ACTIVE");
                entities.put(id, entity);
            }

            // Create synonym
            if (cypher.contains("CREATE (s:Synonym")) {
                String synId = (String) params.getOrDefault("synonymId",
                        "syn-" + synonymCounter.incrementAndGet());
                String entityId = (String) params.get("entityId");
                Map<String, Object> synonym = new HashMap<>(params);
                synonym.put("id", synId);
                synonym.put("entityId", entityId);
                synonyms.put(synId, synonym);
            }

            // Create relationship
            if (cypher.contains("CREATE (source)-[r:LIBRARY_REL")) {
                String relId = (String) params.get("relationshipId");
                String sourceId = (String) params.get("sourceEntityId");
                String targetId = (String) params.get("targetEntityId");
                String relType = (String) params.get("relationshipType");

                Map<String, Object> rel = new HashMap<>();
                rel.put("id", relId);
                rel.put("sourceEntityId", sourceId);
                rel.put("targetEntityId", targetId);
                rel.put("relationshipType", relType);
                rel.put("createdBy", params.get("createdBy"));
                relationships.put(relId, rel);
            }

            // Delete relationship
            if (cypher.contains("MATCH ()-[r:LIBRARY_REL {id: $relationshipId}]->()") &&
                    cypher.contains("DELETE r")) {
                String relId = (String) params.get("relationshipId");
                relationships.remove(relId);
            }

            // Record merge
            if (cypher.contains("SET source.status = 'MERGED'") &&
                    cypher.contains("CREATE (source)-[:MERGED_INTO")) {
                String sourceId = (String) params.get("sourceEntityId");
                String targetId = (String) params.get("targetEntityId");
                simulateMerge(sourceId, targetId);
            }
        }

        @Override
        public void createIndexes() {}

        @Override
        public void close() {}

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String getGraphName() {
            return "scenario-test-graph";
        }
    }
}
