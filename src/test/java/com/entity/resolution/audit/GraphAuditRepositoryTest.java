package com.entity.resolution.audit;

import com.entity.resolution.graph.GraphConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GraphAuditRepository using a stub GraphConnection.
 */
class GraphAuditRepositoryTest {

    private StubGraphConnection connection;
    private GraphAuditRepository repository;

    @BeforeEach
    void setUp() {
        connection = new StubGraphConnection();
        repository = new GraphAuditRepository(connection);
    }

    @Test
    void save_executesCypherCreate() {
        AuditEntry entry = AuditEntry.builder()
                .id("audit-1")
                .action(AuditAction.ENTITY_CREATED)
                .entityId("e1")
                .actorId("actor1")
                .details(Map.of("key", "value"))
                .timestamp(Instant.parse("2024-01-15T10:30:00Z"))
                .build();

        AuditEntry result = repository.save(entry);

        assertEquals(entry, result);
        // Verify an execute call was made containing CREATE (a:AuditEntry
        assertTrue(connection.executedQueries.stream()
                .anyMatch(q -> q.contains("CREATE (a:AuditEntry")));
    }

    @Test
    void findAll_queriesAllEntries() {
        connection.queryResults = List.of(
                Map.of("id", "a1", "action", "ENTITY_CREATED", "entityId", "e1",
                        "actorId", "actor1", "details", "{}", "timestamp", "2024-01-15T10:30:00Z")
        );

        List<AuditEntry> results = repository.findAll();

        assertEquals(1, results.size());
        assertEquals(AuditAction.ENTITY_CREATED, results.get(0).action());
        assertEquals("e1", results.get(0).entityId());
    }

    @Test
    void findByEntityId_queriesByEntityId() {
        connection.queryResults = List.of(
                Map.of("id", "a1", "action", "ENTITY_MERGED", "entityId", "e1",
                        "actorId", "actor1", "details", "{}", "timestamp", "2024-01-15T10:30:00Z")
        );

        List<AuditEntry> results = repository.findByEntityId("e1");

        assertEquals(1, results.size());
        assertEquals("e1", results.get(0).entityId());
    }

    @Test
    void findByAction_queriesByAction() {
        connection.queryResults = List.of(
                Map.of("id", "a1", "action", "SYNONYM_CREATED", "entityId", "e1",
                        "actorId", "actor1", "details", "{}", "timestamp", "2024-01-15T10:30:00Z")
        );

        List<AuditEntry> results = repository.findByAction(AuditAction.SYNONYM_CREATED);

        assertEquals(1, results.size());
        assertEquals(AuditAction.SYNONYM_CREATED, results.get(0).action());
    }

    @Test
    void findByActorId_queriesByActorId() {
        connection.queryResults = List.of(
                Map.of("id", "a1", "action", "ENTITY_CREATED", "entityId", "e1",
                        "actorId", "admin", "details", "{}", "timestamp", "2024-01-15T10:30:00Z")
        );

        List<AuditEntry> results = repository.findByActorId("admin");

        assertEquals(1, results.size());
        assertEquals("admin", results.get(0).actorId());
    }

    @Test
    void count_returnsCount() {
        connection.queryResults = List.of(Map.of("cnt", 42L));

        int count = repository.count();
        assertEquals(42, count);
    }

    @Test
    void count_returnsZeroWhenEmpty() {
        connection.queryResults = List.of();

        int count = repository.count();
        assertEquals(0, count);
    }

    @Test
    void save_handlesNullEntityIdAndActorId() {
        AuditEntry entry = AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED)
                .entityId(null)
                .actorId(null)
                .build();

        assertDoesNotThrow(() -> repository.save(entry));
    }

    @Test
    void save_serializesDetailsAsJson() {
        AuditEntry entry = AuditEntry.builder()
                .action(AuditAction.ENTITY_MERGED)
                .entityId("e1")
                .actorId("actor1")
                .details(Map.of("confidence", 0.95, "reason", "exact match"))
                .build();

        repository.save(entry);

        // Verify the execute call included details
        assertTrue(connection.executedQueries.stream()
                .anyMatch(q -> q.contains("CREATE (a:AuditEntry")));
        assertTrue(connection.executedParams.stream()
                .anyMatch(p -> {
                    String details = (String) p.get("details");
                    return details != null && details.contains("confidence");
                }));
    }

    @Test
    void findAll_deserializesDetailsFromJson() {
        connection.queryResults = List.of(
                Map.of("id", "a1", "action", "ENTITY_MERGED", "entityId", "e1",
                        "actorId", "actor1",
                        "details", "{\"confidence\":0.95,\"reason\":\"exact match\"}",
                        "timestamp", "2024-01-15T10:30:00Z")
        );

        List<AuditEntry> results = repository.findAll();

        assertEquals(1, results.size());
        assertFalse(results.get(0).details().isEmpty());
    }

    @Test
    void findByEntityIdBetween_queriesWithEntityIdAndTimeRange() {
        connection.queryResults = List.of(
                Map.of("id", "a1", "action", "ENTITY_MERGED", "entityId", "e1",
                        "actorId", "actor1", "details", "{}", "timestamp", "2024-01-15T10:30:00Z")
        );

        List<AuditEntry> results = repository.findByEntityIdBetween("e1",
                Instant.parse("2024-01-15T00:00:00Z"), Instant.parse("2024-01-16T00:00:00Z"));

        assertEquals(1, results.size());
        assertEquals("e1", results.get(0).entityId());
        // Verify the query included entityId and time range params
        assertTrue(connection.executedParams.stream()
                .anyMatch(p -> "e1".equals(p.get("entityId")) && p.containsKey("start") && p.containsKey("end")));
    }

    @Test
    void createIndexes_calledOnConstruction() {
        // Index creation happens in constructor; verify queries
        assertTrue(connection.executedQueries.stream()
                .filter(q -> q.startsWith("CREATE INDEX"))
                .count() >= 4);
    }

    /**
     * Stub implementation of GraphConnection for testing.
     */
    private static class StubGraphConnection implements GraphConnection {
        final List<String> executedQueries = new ArrayList<>();
        final List<Map<String, Object>> executedParams = new ArrayList<>();
        List<Map<String, Object>> queryResults = List.of();

        @Override
        public void execute(String query, Map<String, Object> params) {
            executedQueries.add(query);
            executedParams.add(params);
        }

        @Override
        public List<Map<String, Object>> query(String query, Map<String, Object> params) {
            executedQueries.add(query);
            executedParams.add(params);
            return queryResults;
        }

        @Override
        public List<Map<String, Object>> query(String query) {
            executedQueries.add(query);
            return queryResults;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String getGraphName() {
            return "test-graph";
        }

        @Override
        public void createIndexes() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
