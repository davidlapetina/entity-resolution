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
 * Tests for the EntityContext API (getEntityContext).
 */
class EntityContextTest {

    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        var mockConnection = new ContextMockGraphConnection();
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
    @DisplayName("getEntityContext returns empty for non-existent entity")
    void returnsEmptyForNonExistent() {
        Optional<EntityContext> ctx = resolver.getEntityContext("non-existent-id");
        assertTrue(ctx.isEmpty());
    }

    @Test
    @DisplayName("getEntityContext returns context for existing entity")
    void returnsContextForExistingEntity() {
        EntityResolutionResult result = resolver.resolve("Acme Corp", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();

        Optional<EntityContext> ctx = resolver.getEntityContext(entityId);

        assertTrue(ctx.isPresent());
        EntityContext context = ctx.get();
        assertNotNull(context.entity());
        assertEquals("Acme Corp", context.entity().getCanonicalName());
        assertEquals(EntityType.COMPANY, context.entity().getType());
    }

    @Test
    @DisplayName("getEntityContext includes synonyms")
    void includesSynonyms() {
        EntityResolutionResult result = resolver.resolve("International Business Machines", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();

        // Add synonym
        resolver.addSynonym(entityId, "IBM");

        Optional<EntityContext> ctx = resolver.getEntityContext(entityId);
        assertTrue(ctx.isPresent());
        assertFalse(ctx.get().synonyms().isEmpty(), "Should include synonyms");
    }

    @Test
    @DisplayName("getEntityContext includes relationships")
    void includesRelationships() {
        EntityResolutionResult company = resolver.resolve("Parent Corp", EntityType.COMPANY);
        EntityResolutionResult subsidiary = resolver.resolve("Child Inc", EntityType.COMPANY);

        resolver.createRelationship(
                company.getEntityReference(),
                subsidiary.getEntityReference(),
                "SUBSIDIARY_OF");

        Optional<EntityContext> ctx = resolver.getEntityContext(company.getEntityReference().getId());
        assertTrue(ctx.isPresent());
        assertFalse(ctx.get().relationships().isEmpty(), "Should include relationships");
    }

    @Test
    @DisplayName("getEntityContext has decisions list (may be empty for exact matches)")
    void includesDecisionsList() {
        EntityResolutionResult result = resolver.resolve("Test Corp", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();

        Optional<EntityContext> ctx = resolver.getEntityContext(entityId);
        assertTrue(ctx.isPresent());
        assertNotNull(ctx.get().decisions(), "Decisions list should not be null");
    }

    @Test
    @DisplayName("getEntityContext has merge history list")
    void includesMergeHistoryList() {
        EntityResolutionResult result = resolver.resolve("Stable Corp", EntityType.COMPANY);
        String entityId = result.getEntityReference().getId();

        Optional<EntityContext> ctx = resolver.getEntityContext(entityId);
        assertTrue(ctx.isPresent());
        assertNotNull(ctx.get().mergeHistory(), "Merge history list should not be null");
    }

    @Test
    @DisplayName("EntityContext record is immutable")
    void entityContextIsImmutable() {
        Entity entity = Entity.builder()
                .canonicalName("Test")
                .type(EntityType.COMPANY)
                .build();
        EntityContext ctx = new EntityContext(entity, List.of(), List.of(), List.of(), List.of());

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.synonyms().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.relationships().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.decisions().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.mergeHistory().add(null));
    }

    // =========================================================================
    // Mock Graph Connection
    // =========================================================================
    private static class ContextMockGraphConnection implements GraphConnection {
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
        @Override public String getGraphName() { return "context-test-graph"; }
    }
}
