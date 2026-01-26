package com.entity.resolution.audit;

import com.entity.resolution.api.CursorPage;
import com.entity.resolution.graph.GraphConnection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FalkorDB-backed implementation of AuditRepository.
 * Persists AuditEntry as :AuditEntry nodes in the graph database.
 * Details maps are serialized as JSON strings.
 */
public class GraphAuditRepository implements AuditRepository {
    private static final Logger log = LoggerFactory.getLogger(GraphAuditRepository.class);

    private final GraphConnection connection;
    private final ObjectMapper objectMapper;

    public GraphAuditRepository(GraphConnection connection) {
        this.connection = connection;
        this.objectMapper = new ObjectMapper();
        createIndexes();
    }

    private void createIndexes() {
        safeExecute("CREATE INDEX FOR (a:AuditEntry) ON (a.id)");
        safeExecute("CREATE INDEX FOR (a:AuditEntry) ON (a.entityId)");
        safeExecute("CREATE INDEX FOR (a:AuditEntry) ON (a.action)");
        safeExecute("CREATE INDEX FOR (a:AuditEntry) ON (a.timestamp)");
    }

    private void safeExecute(String query) {
        try {
            connection.execute(query);
        } catch (Exception e) {
            log.debug("Index creation query result: {} - {}", query, e.getMessage());
        }
    }

    @Override
    public AuditEntry save(AuditEntry entry) {
        String detailsJson = serializeDetails(entry.details());
        String query = """
                CREATE (a:AuditEntry {
                    id: $id,
                    action: $action,
                    entityId: $entityId,
                    actorId: $actorId,
                    details: $details,
                    timestamp: $timestamp
                })
                """;
        connection.execute(query, Map.of(
                "id", entry.id(),
                "action", entry.action().name(),
                "entityId", entry.entityId() != null ? entry.entityId() : "",
                "actorId", entry.actorId() != null ? entry.actorId() : "",
                "details", detailsJson,
                "timestamp", entry.timestamp().toString()
        ));
        log.debug("Persisted audit entry: {} for entity {}", entry.action(), entry.entityId());
        return entry;
    }

    @Override
    public List<AuditEntry> findAll() {
        String query = """
                MATCH (a:AuditEntry)
                RETURN a.id as id, a.action as action, a.entityId as entityId,
                       a.actorId as actorId, a.details as details, a.timestamp as timestamp
                ORDER BY a.timestamp ASC
                """;
        return mapResults(connection.query(query));
    }

    @Override
    public List<AuditEntry> findByEntityId(String entityId) {
        String query = """
                MATCH (a:AuditEntry {entityId: $entityId})
                RETURN a.id as id, a.action as action, a.entityId as entityId,
                       a.actorId as actorId, a.details as details, a.timestamp as timestamp
                ORDER BY a.timestamp ASC
                """;
        return mapResults(connection.query(query, Map.of("entityId", entityId)));
    }

    @Override
    public List<AuditEntry> findByAction(AuditAction action) {
        String query = """
                MATCH (a:AuditEntry {action: $action})
                RETURN a.id as id, a.action as action, a.entityId as entityId,
                       a.actorId as actorId, a.details as details, a.timestamp as timestamp
                ORDER BY a.timestamp ASC
                """;
        return mapResults(connection.query(query, Map.of("action", action.name())));
    }

    @Override
    public List<AuditEntry> findByActorId(String actorId) {
        String query = """
                MATCH (a:AuditEntry {actorId: $actorId})
                RETURN a.id as id, a.action as action, a.entityId as entityId,
                       a.actorId as actorId, a.details as details, a.timestamp as timestamp
                ORDER BY a.timestamp ASC
                """;
        return mapResults(connection.query(query, Map.of("actorId", actorId)));
    }

    @Override
    public List<AuditEntry> findBetween(Instant start, Instant end) {
        String query = """
                MATCH (a:AuditEntry)
                WHERE a.timestamp >= $start AND a.timestamp <= $end
                RETURN a.id as id, a.action as action, a.entityId as entityId,
                       a.actorId as actorId, a.details as details, a.timestamp as timestamp
                ORDER BY a.timestamp ASC
                """;
        return mapResults(connection.query(query, Map.of(
                "start", start.toString(),
                "end", end.toString()
        )));
    }

    @Override
    public List<AuditEntry> findByEntityIdBetween(String entityId, Instant start, Instant end) {
        String query = """
                MATCH (a:AuditEntry)
                WHERE a.entityId = $entityId AND a.timestamp >= $start AND a.timestamp <= $end
                RETURN a.id as id, a.action as action, a.entityId as entityId,
                       a.actorId as actorId, a.details as details, a.timestamp as timestamp
                ORDER BY a.timestamp ASC
                """;
        return mapResults(connection.query(query, Map.of(
                "entityId", entityId,
                "start", start.toString(),
                "end", end.toString()
        )));
    }

    @Override
    public int count() {
        String query = """
                MATCH (a:AuditEntry)
                RETURN count(a) as cnt
                """;
        List<Map<String, Object>> results = connection.query(query);
        if (results.isEmpty()) {
            return 0;
        }
        return ((Number) results.get(0).get("cnt")).intValue();
    }

    @Override
    public List<AuditEntry> findRecent(int limit) {
        String query = """
                MATCH (a:AuditEntry)
                RETURN a.id as id, a.action as action, a.entityId as entityId,
                       a.actorId as actorId, a.details as details, a.timestamp as timestamp
                ORDER BY a.timestamp DESC
                LIMIT $limit
                """;
        List<AuditEntry> results = mapResults(connection.query(query, Map.of("limit", limit)));
        // Reverse to return in chronological order
        List<AuditEntry> reversed = new ArrayList<>(results);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public CursorPage<AuditEntry> findByEntityIdAfter(String entityId, String cursor, int limit) {
        // Fetch limit + 1 to detect if there are more results
        int fetchLimit = limit + 1;
        String query;
        Map<String, Object> params;
        if (cursor != null && !cursor.isEmpty()) {
            query = """
                    MATCH (a:AuditEntry)
                    WHERE a.entityId = $entityId AND a.timestamp > $cursor
                    RETURN a.id as id, a.action as action, a.entityId as entityId,
                           a.actorId as actorId, a.details as details, a.timestamp as timestamp
                    ORDER BY a.timestamp ASC
                    LIMIT $limit
                    """;
            params = Map.of("entityId", entityId, "cursor", cursor, "limit", fetchLimit);
        } else {
            query = """
                    MATCH (a:AuditEntry)
                    WHERE a.entityId = $entityId
                    RETURN a.id as id, a.action as action, a.entityId as entityId,
                           a.actorId as actorId, a.details as details, a.timestamp as timestamp
                    ORDER BY a.timestamp ASC
                    LIMIT $limit
                    """;
            params = Map.of("entityId", entityId, "limit", fetchLimit);
        }
        List<AuditEntry> results = mapResults(connection.query(query, params));
        boolean hasMore = results.size() > limit;
        List<AuditEntry> content = hasMore ? results.subList(0, limit) : results;
        String nextCursor = hasMore ? content.get(content.size() - 1).timestamp().toString() : null;
        return new CursorPage<>(content, nextCursor, hasMore);
    }

    private List<AuditEntry> mapResults(List<Map<String, Object>> rows) {
        return rows.stream().map(this::mapToAuditEntry).toList();
    }

    private AuditEntry mapToAuditEntry(Map<String, Object> row) {
        String id = (String) row.get("id");
        AuditAction action = AuditAction.valueOf((String) row.get("action"));
        String entityId = (String) row.get("entityId");
        String actorId = (String) row.get("actorId");
        String detailsJson = (String) row.get("details");
        String timestampStr = (String) row.get("timestamp");

        Map<String, Object> details = deserializeDetails(detailsJson);
        Instant timestamp = timestampStr != null ? Instant.parse(timestampStr) : Instant.now();

        return new AuditEntry(id, action,
                entityId != null && !entityId.isEmpty() ? entityId : null,
                actorId != null && !actorId.isEmpty() ? actorId : null,
                details, timestamp);
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit details: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserializeDetails(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize audit details: {}", e.getMessage());
            return Map.of();
        }
    }
}
