package com.entity.resolution.api;

import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.graph.GraphConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BatchContext - batch entity resolution operations.
 */
class BatchContextTest {

    private EntityResolver resolver;
    private MockGraphConnection mockConnection;

    @BeforeEach
    void setUp() {
        mockConnection = new MockGraphConnection();
        resolver = EntityResolver.builder()
                .graphConnection(mockConnection)
                .createIndexes(false)
                .build();
    }

    @Test
    void testBatchDeduplication() {
        try (BatchContext batch = resolver.beginBatch()) {
            EntityResolutionResult result1 = batch.resolve("Acme Corp", EntityType.COMPANY);
            EntityResolutionResult result2 = batch.resolve("Acme Corp", EntityType.COMPANY);

            // Same name should resolve to same entity
            assertEquals(result1.getEntityReference().getId(),
                    result2.getEntityReference().getId());
        }
    }

    @Test
    void testBatchCaseInsensitiveDeduplication() {
        try (BatchContext batch = resolver.beginBatch()) {
            EntityResolutionResult result1 = batch.resolve("Acme Corp", EntityType.COMPANY);
            EntityResolutionResult result2 = batch.resolve("ACME CORP", EntityType.COMPANY);

            // Case-insensitive deduplication
            assertEquals(result1.getEntityReference().getId(),
                    result2.getEntityReference().getId());
        }
    }

    @Test
    void testBatchDifferentTypesNotDeduplicated() {
        try (BatchContext batch = resolver.beginBatch()) {
            EntityResolutionResult companyResult = batch.resolve("Apple", EntityType.COMPANY);
            EntityResolutionResult productResult = batch.resolve("Apple", EntityType.PRODUCT);

            // Different types should create different entities
            assertNotEquals(companyResult.getEntityReference().getId(),
                    productResult.getEntityReference().getId());
        }
    }

    @Test
    void testBatchPendingRelationships() {
        try (BatchContext batch = resolver.beginBatch()) {
            EntityResolutionResult a = batch.resolve("Company A", EntityType.COMPANY);
            EntityResolutionResult b = batch.resolve("Company B", EntityType.COMPANY);

            batch.createRelationship(a.getEntityReference(), b.getEntityReference(), "PARTNER");
            batch.createRelationship(a.getEntityReference(), b.getEntityReference(), "SUBSIDIARY");

            assertEquals(2, batch.getPendingRelationshipCount());
        }
    }

    @Test
    void testBatchCommitResult() {
        try (BatchContext batch = resolver.beginBatch()) {
            batch.resolve("Company A", EntityType.COMPANY);
            batch.resolve("Company B", EntityType.COMPANY);
            batch.resolve("Company A", EntityType.COMPANY); // Duplicate

            BatchResult result = batch.commit();

            assertEquals(2, result.totalEntitiesResolved()); // 2 unique
            assertEquals(2, result.newEntitiesCreated());
            assertTrue(result.isSuccess());
            assertFalse(result.hasErrors());
        }
    }

    @Test
    void testBatchRollback() {
        try (BatchContext batch = resolver.beginBatch()) {
            EntityResolutionResult a = batch.resolve("Company A", EntityType.COMPANY);
            EntityResolutionResult b = batch.resolve("Company B", EntityType.COMPANY);

            batch.createRelationship(a.getEntityReference(), b.getEntityReference(), "PARTNER");

            batch.rollback();

            assertEquals(0, batch.getPendingRelationshipCount());
        }
    }

    @Test
    void testBatchCannotResolveAfterClose() {
        BatchContext batch = resolver.beginBatch();
        batch.close();

        assertThrows(IllegalStateException.class, () ->
                batch.resolve("Company A", EntityType.COMPANY));
    }

    @Test
    void testBatchCannotCommitTwice() {
        try (BatchContext batch = resolver.beginBatch()) {
            batch.resolve("Company A", EntityType.COMPANY);
            batch.commit();

            assertThrows(IllegalStateException.class, batch::commit);
        }
    }

    @Test
    void testGetResolvedEntities() {
        try (BatchContext batch = resolver.beginBatch()) {
            batch.resolve("Company A", EntityType.COMPANY);
            batch.resolve("Company B", EntityType.COMPANY);
            batch.resolve("Company A", EntityType.COMPANY); // Duplicate

            assertEquals(2, batch.getResolvedEntities().size());
        }
    }

    /**
     * Mock graph connection for testing.
     */
    private static class MockGraphConnection implements GraphConnection {
        private final Map<String, Map<String, Object>> entities = new HashMap<>();

        @Override
        public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
            // Handle find queries
            if (cypher.contains("WHERE e.normalizedName = $normalizedName")) {
                String normalizedName = (String) params.get("normalizedName");
                String entityType = (String) params.get("entityType");
                return entities.values().stream()
                        .filter(e -> normalizedName.equals(e.get("normalizedName")))
                        .filter(e -> entityType.equals(e.get("type")))
                        .toList();
            }
            if (cypher.contains("WHERE s.normalizedValue = $normalizedValue")) {
                return List.of(); // No synonyms in mock
            }
            if (cypher.contains("WHERE e.type = $entityType") && cypher.contains("AND e.status = 'ACTIVE'")) {
                String entityType = (String) params.get("entityType");
                return entities.values().stream()
                        .filter(e -> entityType.equals(e.get("type")))
                        .filter(e -> "ACTIVE".equals(e.get("status")))
                        .toList();
            }
            return List.of();
        }

        @Override
        public void execute(String cypher, Map<String, Object> params) {
            // Handle entity creation
            if (cypher.contains("CREATE (e:Entity")) {
                String id = (String) params.get("id");
                Map<String, Object> entity = new HashMap<>(params);
                entity.put("status", "ACTIVE");
                entities.put(id, entity);
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
            return "test-graph";
        }
    }
}
