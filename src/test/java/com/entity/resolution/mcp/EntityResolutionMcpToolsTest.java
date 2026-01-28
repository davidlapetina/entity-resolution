package com.entity.resolution.mcp;

import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.ResolutionOptions;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.graph.GraphConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the MCP tools module.
 */
class EntityResolutionMcpToolsTest {

    private EntityResolver resolver;
    private EntityResolutionMcpTools mcpTools;

    @BeforeEach
    void setUp() {
        var mockConnection = new McpMockGraphConnection();
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
        mcpTools = new EntityResolutionMcpTools(resolver);
    }

    @Test
    @DisplayName("getToolDefinitions returns exactly 5 tools")
    void returnsExactlyFiveTools() {
        List<McpToolDefinition> tools = mcpTools.getToolDefinitions();
        assertEquals(5, tools.size());
    }

    @Test
    @DisplayName("all tools have required fields")
    void allToolsHaveRequiredFields() {
        for (McpToolDefinition tool : mcpTools.getToolDefinitions()) {
            assertNotNull(tool.name(), "Tool name should not be null");
            assertFalse(tool.name().isBlank(), "Tool name should not be blank");
            assertNotNull(tool.description(), "Description should not be null");
            assertFalse(tool.description().isBlank(), "Description should not be blank");
            assertNotNull(tool.inputSchema(), "Input schema should not be null");
            assertFalse(tool.inputSchema().isEmpty(), "Input schema should not be empty");
            assertNotNull(tool.handler(), "Handler should not be null");
        }
    }

    @Test
    @DisplayName("tool names are unique")
    void toolNamesAreUnique() {
        List<String> names = mcpTools.getToolDefinitions().stream()
                .map(McpToolDefinition::name)
                .toList();
        assertEquals(names.size(), new HashSet<>(names).size(), "Tool names should be unique");
    }

    @Test
    @DisplayName("expected tool names are present")
    void expectedToolNamesPresent() {
        List<String> names = mcpTools.getToolDefinitions().stream()
                .map(McpToolDefinition::name)
                .toList();
        assertTrue(names.contains("resolve_entity"));
        assertTrue(names.contains("get_entity_context"));
        assertTrue(names.contains("get_entity_decisions"));
        assertTrue(names.contains("search_entities"));
        assertTrue(names.contains("get_entity_synonyms"));
    }

    @Test
    @DisplayName("getTool finds tool by name")
    void getToolByName() {
        Optional<McpToolDefinition> tool = mcpTools.getTool("resolve_entity");
        assertTrue(tool.isPresent());
        assertEquals("resolve_entity", tool.get().name());
    }

    @Test
    @DisplayName("getTool returns empty for unknown name")
    void getToolReturnsEmptyForUnknown() {
        Optional<McpToolDefinition> tool = mcpTools.getTool("nonexistent_tool");
        assertTrue(tool.isEmpty());
    }

    // === Tool handler tests ===

    @Test
    @DisplayName("resolve_entity returns found=false for non-existent entity")
    void resolveEntityNotFound() {
        McpToolDefinition tool = mcpTools.getTool("resolve_entity").orElseThrow();
        Map<String, Object> result = tool.handler().apply(
                Map.of("name", "NonExistent Corp", "entity_type", "COMPANY"));
        assertEquals(false, result.get("found"));
    }

    @Test
    @DisplayName("resolve_entity returns entity data for existing entity")
    void resolveEntityFound() {
        // Create an entity first
        resolver.resolve("Acme Corp", EntityType.COMPANY);

        McpToolDefinition tool = mcpTools.getTool("resolve_entity").orElseThrow();
        Map<String, Object> result = tool.handler().apply(
                Map.of("name", "Acme Corp", "entity_type", "COMPANY"));
        assertEquals(true, result.get("found"));
        assertNotNull(result.get("entityId"));
        assertEquals("Acme Corp", result.get("canonicalName"));
        assertEquals("COMPANY", result.get("type"));
    }

    @Test
    @DisplayName("get_entity_context returns found=false for non-existent entity")
    void getEntityContextNotFound() {
        McpToolDefinition tool = mcpTools.getTool("get_entity_context").orElseThrow();
        Map<String, Object> result = tool.handler().apply(
                Map.of("entity_id", "nonexistent-id"));
        assertEquals(false, result.get("found"));
    }

    @Test
    @DisplayName("get_entity_context returns context for existing entity")
    void getEntityContextFound() {
        EntityResolutionResult created = resolver.resolve("Context Corp", EntityType.COMPANY);
        String entityId = created.getEntityReference().getId();

        McpToolDefinition tool = mcpTools.getTool("get_entity_context").orElseThrow();
        Map<String, Object> result = tool.handler().apply(Map.of("entity_id", entityId));
        assertEquals(true, result.get("found"));
        assertEquals(entityId, result.get("entityId"));
        assertNotNull(result.get("synonymCount"));
        assertNotNull(result.get("relationshipCount"));
    }

    @Test
    @DisplayName("get_entity_decisions returns decisions list")
    void getEntityDecisions() {
        EntityResolutionResult created = resolver.resolve("Decisions Corp", EntityType.COMPANY);
        String entityId = created.getEntityReference().getId();

        McpToolDefinition tool = mcpTools.getTool("get_entity_decisions").orElseThrow();
        Map<String, Object> result = tool.handler().apply(Map.of("entity_id", entityId));
        assertNotNull(result.get("decisions"));
        assertEquals(entityId, result.get("entityId"));
    }

    @Test
    @DisplayName("search_entities returns entities of specified type")
    void searchEntities() {
        resolver.resolve("Search Corp A", EntityType.COMPANY);
        resolver.resolve("Search Corp B", EntityType.COMPANY);

        McpToolDefinition tool = mcpTools.getTool("search_entities").orElseThrow();
        Map<String, Object> result = tool.handler().apply(Map.of("entity_type", "COMPANY"));
        assertEquals("COMPANY", result.get("entityType"));
        assertTrue((int) result.get("count") >= 2);
    }

    @Test
    @DisplayName("get_entity_synonyms returns synonyms")
    void getEntitySynonyms() {
        EntityResolutionResult created = resolver.resolve("Synonym Corp", EntityType.COMPANY);
        String entityId = created.getEntityReference().getId();
        resolver.addSynonym(entityId, "SC");

        McpToolDefinition tool = mcpTools.getTool("get_entity_synonyms").orElseThrow();
        Map<String, Object> result = tool.handler().apply(Map.of("entity_id", entityId));
        assertEquals(entityId, result.get("entityId"));
        assertTrue((int) result.get("count") >= 1);
    }

    @Test
    @DisplayName("constructor requires non-null resolver")
    void constructorRequiresResolver() {
        assertThrows(NullPointerException.class, () -> new EntityResolutionMcpTools(null));
    }

    @Test
    @DisplayName("McpToolDefinition validates required fields")
    void toolDefinitionValidation() {
        assertThrows(NullPointerException.class, () ->
                new McpToolDefinition(null, "desc", Map.of(), params -> Map.of()));
        assertThrows(NullPointerException.class, () ->
                new McpToolDefinition("name", null, Map.of(), params -> Map.of()));
        assertThrows(NullPointerException.class, () ->
                new McpToolDefinition("name", "desc", null, params -> Map.of()));
        assertThrows(NullPointerException.class, () ->
                new McpToolDefinition("name", "desc", Map.of(), null));
    }

    // =========================================================================
    // Mock Graph Connection
    // =========================================================================
    private static class McpMockGraphConnection implements GraphConnection {
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
        @Override public String getGraphName() { return "mcp-test-graph"; }
    }
}
