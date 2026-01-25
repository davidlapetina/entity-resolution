package com.entity.resolution.graph;

import com.entity.resolution.core.model.DuplicateEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Repository for DuplicateEntity node CRUD operations.
 */
public class DuplicateEntityRepository {
    private static final Logger log = LoggerFactory.getLogger(DuplicateEntityRepository.class);

    private final CypherExecutor executor;
    private final GraphConnection connection;

    public DuplicateEntityRepository(CypherExecutor executor) {
        this.executor = executor;
        this.connection = executor.getConnection();
    }

    public DuplicateEntityRepository(GraphConnection connection) {
        this(new CypherExecutor(connection));
    }

    /**
     * Creates a duplicate entity node linked to the canonical entity.
     */
    public DuplicateEntity createForCanonical(DuplicateEntity duplicate, String canonicalEntityId) {
        executor.createDuplicateEntity(
                duplicate.getId(),
                duplicate.getOriginalName(),
                duplicate.getNormalizedName(),
                duplicate.getSourceSystem(),
                canonicalEntityId
        );
        return duplicate;
    }

    /**
     * Finds all duplicate entities that point to a canonical entity.
     */
    public List<DuplicateEntity> findByCanonicalId(String canonicalEntityId) {
        String query = """
                MATCH (d:DuplicateEntity)-[:DUPLICATE_OF]->(e:Entity {id: $entityId})
                RETURN d.id as id, d.originalName as originalName, d.normalizedName as normalizedName,
                       d.sourceSystem as sourceSystem, d.createdAt as createdAt
                """;
        List<Map<String, Object>> results = connection.query(query,
                Map.of("entityId", canonicalEntityId));
        return results.stream().map(this::mapToDuplicateEntity).toList();
    }

    /**
     * Finds the canonical entity for a duplicate.
     */
    public String findCanonicalEntityId(String duplicateId) {
        String query = """
                MATCH (d:DuplicateEntity {id: $duplicateId})-[:DUPLICATE_OF]->(e:Entity)
                RETURN e.id as entityId
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("duplicateId", duplicateId));
        if (results.isEmpty()) {
            return null;
        }
        return (String) results.get(0).get("entityId");
    }

    private DuplicateEntity mapToDuplicateEntity(Map<String, Object> row) {
        return DuplicateEntity.builder()
                .id((String) row.get("id"))
                .originalName((String) row.get("originalName"))
                .normalizedName((String) row.get("normalizedName"))
                .sourceSystem((String) row.get("sourceSystem"))
                .build();
    }

    public CypherExecutor getExecutor() {
        return executor;
    }
}
