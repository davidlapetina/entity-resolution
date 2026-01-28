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
 * Tests for the document linking API.
 */
class DocumentLinkingTest {

    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        var mockConnection = new DocLinkMockGraphConnection();
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
    @DisplayName("linkDocument creates DOCUMENT entity and DOCUMENT_LINK relationship")
    void linkDocumentCreatesRelationship() {
        EntityResolutionResult company = resolver.resolve("Acme Corp", EntityType.COMPANY);
        EntityReference companyRef = company.getEntityReference();

        Relationship rel = resolver.linkDocument(
                companyRef, "doc-123", "Annual Report 2024", Map.of("year", "2024"));

        assertNotNull(rel);
        assertEquals("DOCUMENT_LINK", rel.getRelationshipType());
        assertEquals(companyRef.getId(), rel.getSourceEntityId());
        assertEquals("doc-123", rel.getProperties().get("documentId"));
        assertEquals("2024", rel.getProperties().get("year"));
    }

    @Test
    @DisplayName("linkDocument with null metadata uses empty map")
    void linkDocumentWithNullMetadata() {
        EntityResolutionResult company = resolver.resolve("Beta Corp", EntityType.COMPANY);
        EntityReference companyRef = company.getEntityReference();

        Relationship rel = resolver.linkDocument(companyRef, "doc-456", "Quarterly Report", null);

        assertNotNull(rel);
        assertEquals("DOCUMENT_LINK", rel.getRelationshipType());
        assertEquals("doc-456", rel.getProperties().get("documentId"));
    }

    @Test
    @DisplayName("getLinkedDocuments returns only DOCUMENT_LINK relationships")
    void getLinkedDocumentsFiltersCorrectly() {
        EntityResolutionResult company = resolver.resolve("Gamma Corp", EntityType.COMPANY);
        EntityReference companyRef = company.getEntityReference();

        // Link two documents
        resolver.linkDocument(companyRef, "doc-1", "Report A", Map.of());
        resolver.linkDocument(companyRef, "doc-2", "Report B", Map.of());

        // Create a non-document relationship
        EntityResolutionResult partner = resolver.resolve("Partner Corp", EntityType.COMPANY);
        resolver.createRelationship(companyRef, partner.getEntityReference(), "PARTNER");

        // Should only return document links
        List<Relationship> docs = resolver.getLinkedDocuments(companyRef);
        assertEquals(2, docs.size());
        assertTrue(docs.stream().allMatch(r -> "DOCUMENT_LINK".equals(r.getRelationshipType())));
    }

    @Test
    @DisplayName("getLinkedDocuments returns empty list when no documents linked")
    void getLinkedDocumentsReturnsEmptyWhenNone() {
        EntityResolutionResult company = resolver.resolve("Lonely Corp", EntityType.COMPANY);
        List<Relationship> docs = resolver.getLinkedDocuments(company.getEntityReference());
        assertTrue(docs.isEmpty());
    }

    @Test
    @DisplayName("linkDocument resolves document as DOCUMENT entity type")
    void linkDocumentResolvesAsDocumentEntity() {
        EntityResolutionResult company = resolver.resolve("Delta Corp", EntityType.COMPANY);

        resolver.linkDocument(company.getEntityReference(), "doc-x", "Architecture Doc", Map.of());

        // The document should exist as a DOCUMENT entity
        Optional<EntityReference> docRef = resolver.findEntity("Architecture Doc", EntityType.DOCUMENT);
        assertTrue(docRef.isPresent(), "Document should be resolvable as a DOCUMENT entity");
    }

    @Test
    @DisplayName("linking same document twice reuses existing DOCUMENT entity")
    void linkSameDocumentTwiceReusesEntity() {
        EntityResolutionResult company1 = resolver.resolve("Company One", EntityType.COMPANY);
        EntityResolutionResult company2 = resolver.resolve("Company Two", EntityType.COMPANY);

        resolver.linkDocument(company1.getEntityReference(), "doc-shared", "Shared Doc", Map.of());
        resolver.linkDocument(company2.getEntityReference(), "doc-shared", "Shared Doc", Map.of());

        // Both should link to the same document entity
        Optional<EntityReference> docRef = resolver.findEntity("Shared Doc", EntityType.DOCUMENT);
        assertTrue(docRef.isPresent());
    }

    // =========================================================================
    // Mock Graph Connection for Document Linking Tests
    // =========================================================================
    private static class DocLinkMockGraphConnection implements GraphConnection {
        private final Map<String, Map<String, Object>> entities = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> synonyms = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> relationships = new ConcurrentHashMap<>();
        private final Map<String, String> mergeMap = new ConcurrentHashMap<>();
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

            if (cypher.contains("SET source.status = 'MERGED'") &&
                    cypher.contains("CREATE (source)-[:MERGED_INTO")) {
                String sourceId = (String) params.get("sourceEntityId");
                String targetId = (String) params.get("targetEntityId");
                mergeMap.put(sourceId, targetId);
                Map<String, Object> sourceEntity = entities.get(sourceId);
                if (sourceEntity != null) sourceEntity.put("status", "MERGED");
            }
        }

        @Override
        public void createIndexes() {}

        @Override
        public void close() {}

        @Override
        public boolean isConnected() { return true; }

        @Override
        public String getGraphName() { return "doc-link-test-graph"; }
    }
}
