package com.entity.resolution.api;

import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.GraphConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the entity subgraph export API (exportEntitySubgraph).
 */
class EntitySubgraphTest {

    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        var mockConnection = new SubgraphMockGraphConnection();
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

    @Test
    @DisplayName("exportEntitySubgraph returns empty for non-existent entity")
    void returnsEmptyForNonExistent() {
        Optional<EntitySubgraph> sg = resolver.exportEntitySubgraph("non-existent-id", 1);
        assertTrue(sg.isEmpty());
    }

    @Test
    @DisplayName("exportEntitySubgraph returns subgraph for existing entity")
    void returnsSubgraphForExistingEntity() {
        EntityResolutionResult result = resolver.resolve("Root Corp", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();

        Optional<EntitySubgraph> sg = resolver.exportEntitySubgraph(entityId, 1);
        assertTrue(sg.isPresent());
        assertEquals(entityId, sg.get().rootEntity().getId());
    }

    @Test
    @DisplayName("exportEntitySubgraph clamps depth between 1 and 3")
    void clampsDepth() {
        EntityResolutionResult result = resolver.resolve("Depth Test Corp", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();

        Optional<EntitySubgraph> sgLow = resolver.exportEntitySubgraph(entityId, 0);
        assertTrue(sgLow.isPresent());
        assertEquals(1, sgLow.get().depth(), "Depth should be clamped to minimum 1");

        Optional<EntitySubgraph> sgHigh = resolver.exportEntitySubgraph(entityId, 10);
        assertTrue(sgHigh.isPresent());
        assertEquals(3, sgHigh.get().depth(), "Depth should be clamped to maximum 3");
    }

    @Test
    @DisplayName("exportEntitySubgraph includes related entities at depth 1")
    void includesRelatedEntitiesAtDepth1() {
        // Use different entity types to avoid fuzzy-match interference
        EntityResolutionResult root = resolver.resolve("Hub Corporation", EntityType.COMPANY);
        EntityResolutionResult spoke1 = resolver.resolve("Alice Johnson", EntityType.PERSON);
        EntityResolutionResult spoke2 = resolver.resolve("Chicago Illinois", EntityType.LOCATION);

        // Verify these are distinct entities
        assertNotEquals(root.getEntityReference().getId(), spoke1.getEntityReference().getId());
        assertNotEquals(root.getEntityReference().getId(), spoke2.getEntityReference().getId());
        assertNotEquals(spoke1.getEntityReference().getId(), spoke2.getEntityReference().getId());

        resolver.createRelationship(root.getEntityReference(), spoke1.getEntityReference(), "HAS_CEO");
        resolver.createRelationship(root.getEntityReference(), spoke2.getEntityReference(), "LOCATED_IN");

        Optional<EntitySubgraph> sg = resolver.exportEntitySubgraph(
                root.getEntityReference().getId(), 1);

        assertTrue(sg.isPresent());
        EntitySubgraph subgraph = sg.get();

        assertFalse(subgraph.relationships().isEmpty(), "Should include relationships");
        assertFalse(subgraph.relatedEntities().isEmpty(), "Should include related entities");
        assertEquals(2, subgraph.relatedEntities().size(), "Should have 2 related entities");
    }

    @Test
    @DisplayName("exportEntitySubgraph includes synonyms")
    void includesSynonyms() {
        EntityResolutionResult result = resolver.resolve("IBM Corporation", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();
        resolver.addSynonym(entityId, "IBM");

        Optional<EntitySubgraph> sg = resolver.exportEntitySubgraph(entityId, 1);
        assertTrue(sg.isPresent());
        assertFalse(sg.get().synonyms().isEmpty(), "Should include synonyms");
    }

    @Test
    @DisplayName("exportEntitySubgraph metadata contains counts")
    void metadataContainsCounts() {
        EntityResolutionResult root = resolver.resolve("Meta Corp", EntityType.COMPANY);
        EntityResolutionResult partner = resolver.resolve("Partner Corp", EntityType.COMPANY);
        resolver.createRelationship(root.getEntityReference(), partner.getEntityReference(), "PARTNER");

        Optional<EntitySubgraph> sg = resolver.exportEntitySubgraph(
                root.getEntityReference().getId(), 1);

        assertTrue(sg.isPresent());
        Map<String, Object> metadata = sg.get().metadata();
        assertNotNull(metadata.get("entityCount"));
        assertNotNull(metadata.get("relationshipCount"));
        assertNotNull(metadata.get("synonymCount"));
    }

    @Test
    @DisplayName("EntitySubgraph record is immutable")
    void subgraphIsImmutable() {
        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        EntitySubgraph sg = new EntitySubgraph(
                entity, List.of(), List.of(), List.of(), List.of(), 1, Map.of());

        assertThrows(UnsupportedOperationException.class,
                () -> sg.synonyms().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> sg.relationships().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> sg.relatedEntities().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> sg.decisions().add(null));
    }

    @Test
    @DisplayName("exportEntitySubgraph includes decisions list")
    void includesDecisionsList() {
        EntityResolutionResult result = resolver.resolve("Decisions Corp", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();

        Optional<EntitySubgraph> sg = resolver.exportEntitySubgraph(entityId, 1);
        assertTrue(sg.isPresent());
        assertNotNull(sg.get().decisions(), "Decisions list should not be null");
    }

    // =========================================================================
    // Mock Graph Connection
    // =========================================================================
    private static class SubgraphMockGraphConnection implements GraphConnection {
        private final Map<String, Map<String, Object>> entities = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> synonyms = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> relationships = new ConcurrentHashMap<>();
        private final AtomicInteger synonymCounter = new AtomicInteger(0);

        @Override
        public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
            if (cypher.contains("WHERE e.normalizedName = $normalizedName")) {
                String normalizedName = (String) params.get("normalizedName");
                String entityType = (String) params.get("entityType");
                return entities.values().stream()
                        .filter(e -> normalizedName.equals(e.get("normalizedName")))
                        .filter(e -> entityType.equals(e.get("type")))
                        .filter(e -> "ACTIVE".equals(e.get("status")))
                        .toList();
            }
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
            if (cypher.contains("WHERE e.type = $entityType") && cypher.contains("AND e.status = 'ACTIVE'")) {
                String entityType = (String) params.get("entityType");
                return entities.values().stream()
                        .filter(e -> entityType.equals(e.get("type")))
                        .filter(e -> "ACTIVE".equals(e.get("status")))
                        .toList();
            }
            if (cypher.contains("MATCH (e:Entity {id: $id})")) {
                String id = (String) params.get("id");
                Map<String, Object> entity = entities.get(id);
                return entity != null ? List.of(entity) : List.of();
            }
            if (cypher.contains("MATCH (s:Synonym)-[:SYNONYM_OF]->(e:Entity {id: $entityId})")) {
                String entityId = (String) params.get("entityId");
                return synonyms.values().stream()
                        .filter(s -> entityId.equals(s.get("entityId")))
                        .toList();
            }
            if (cypher.contains("MATCH (source:Entity {id: $entityId})-[r:LIBRARY_REL]->(target:Entity)")) {
                String entityId = (String) params.get("entityId");
                return relationships.values().stream()
                        .filter(r -> entityId.equals(r.get("sourceEntityId")))
                        .toList();
            }
            if (cypher.contains("MATCH (source:Entity)-[r:LIBRARY_REL]->(target:Entity {id: $entityId})")) {
                String entityId = (String) params.get("entityId");
                return relationships.values().stream()
                        .filter(r -> entityId.equals(r.get("targetEntityId")))
                        .toList();
            }
            if (cypher.contains("WHERE source.id = $entityId OR target.id = $entityId")) {
                String entityId = (String) params.get("entityId");
                return relationships.values().stream()
                        .filter(r -> entityId.equals(r.get("sourceEntityId")) ||
                                entityId.equals(r.get("targetEntityId")))
                        .toList();
            }
            if (cypher.contains("MATCH (merged:Entity {id: $mergedEntityId})-[:MERGED_INTO*]->(canonical:Entity)")) {
                return List.of();
            }
            return List.of();
        }

        @Override
        public void execute(String cypher, Map<String, Object> params) {
            if (cypher.contains("CREATE (e:Entity")) {
                String id = (String) params.get("id");
                Map<String, Object> entity = new HashMap<>(params);
                entity.put("status", "ACTIVE");
                entities.put(id, entity);
            }
            if (cypher.contains("CREATE (s:Synonym")) {
                String synId = (String) params.getOrDefault("synonymId",
                        "syn-" + synonymCounter.incrementAndGet());
                String entityId = (String) params.get("entityId");
                Map<String, Object> synonym = new HashMap<>(params);
                synonym.put("id", synId);
                synonym.put("entityId", entityId);
                synonyms.put(synId, synonym);
            }
            if (cypher.contains("CREATE (source)-[r:LIBRARY_REL")) {
                String relId = (String) params.get("relationshipId");
                Map<String, Object> rel = new HashMap<>();
                rel.put("id", relId);
                rel.put("sourceEntityId", params.get("sourceEntityId"));
                rel.put("targetEntityId", params.get("targetEntityId"));
                rel.put("relationshipType", params.get("relationshipType"));
                rel.put("createdBy", params.get("createdBy"));
                relationships.put(relId, rel);
            }
            if (cypher.contains("SET source.status = 'MERGED'") &&
                    cypher.contains("CREATE (source)-[:MERGED_INTO")) {
                String sourceId = (String) params.get("sourceEntityId");
                Map<String, Object> sourceEntity = entities.get(sourceId);
                if (sourceEntity != null) sourceEntity.put("status", "MERGED");
            }
        }

        @Override public void createIndexes() {}
        @Override public void close() {}
        @Override public boolean isConnected() { return true; }
        @Override public String getGraphName() { return "subgraph-test-graph"; }
    }
}
