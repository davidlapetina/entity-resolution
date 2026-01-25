package com.entity.resolution.graph;

import com.entity.resolution.core.model.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for library-managed relationships.
 *
 * This repository handles all relationship CRUD operations and ensures
 * relationships are properly migrated when entities are merged.
 */
public class RelationshipRepository {
    private static final Logger log = LoggerFactory.getLogger(RelationshipRepository.class);

    private final CypherExecutor executor;

    public RelationshipRepository(CypherExecutor executor) {
        this.executor = executor;
    }

    /**
     * Creates a relationship between two entities.
     * The relationship is tracked by the library for merge migration.
     */
    public Relationship create(Relationship relationship) {
        executor.createRelationship(
                relationship.getId(),
                relationship.getSourceEntityId(),
                relationship.getTargetEntityId(),
                relationship.getRelationshipType(),
                relationship.getProperties(),
                relationship.getCreatedBy()
        );
        log.debug("Created relationship {} from {} to {} (type: {})",
                relationship.getId(), relationship.getSourceEntityId(),
                relationship.getTargetEntityId(), relationship.getRelationshipType());
        return relationship;
    }

    /**
     * Finds a relationship by ID.
     */
    public Optional<Relationship> findById(String relationshipId) {
        List<Map<String, Object>> results = executor.findRelationshipById(relationshipId);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToRelationship(results.get(0)));
    }

    /**
     * Finds all relationships where the given entity is the source.
     */
    public List<Relationship> findBySourceEntity(String entityId) {
        return executor.findRelationshipsBySource(entityId).stream()
                .map(this::mapToRelationship)
                .collect(Collectors.toList());
    }

    /**
     * Finds all relationships where the given entity is the target.
     */
    public List<Relationship> findByTargetEntity(String entityId) {
        return executor.findRelationshipsByTarget(entityId).stream()
                .map(this::mapToRelationship)
                .collect(Collectors.toList());
    }

    /**
     * Finds all relationships involving an entity (as source or target).
     */
    public List<Relationship> findByEntity(String entityId) {
        return executor.findRelationshipsByEntity(entityId).stream()
                .map(this::mapToRelationship)
                .collect(Collectors.toList());
    }

    /**
     * Finds relationships between two specific entities.
     */
    public List<Relationship> findBetween(String sourceEntityId, String targetEntityId) {
        return executor.findRelationshipsBetween(sourceEntityId, targetEntityId).stream()
                .map(this::mapToRelationship)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a relationship by ID.
     */
    public void delete(String relationshipId) {
        executor.deleteRelationship(relationshipId);
        log.debug("Deleted relationship {}", relationshipId);
    }

    /**
     * Migrates all relationships from source entity to target entity.
     * Called during entity merge operations.
     */
    public void migrateRelationships(String sourceEntityId, String targetEntityId) {
        executor.migrateLibraryRelationships(sourceEntityId, targetEntityId);
        log.info("Migrated relationships from {} to {}", sourceEntityId, targetEntityId);
    }

    private Relationship mapToRelationship(Map<String, Object> row) {
        Relationship.Builder builder = Relationship.builder()
                .id((String) row.get("id"))
                .sourceEntityId((String) row.get("sourceEntityId"))
                .targetEntityId((String) row.get("targetEntityId"))
                .relationshipType((String) row.get("relationshipType"))
                .createdBy((String) row.get("createdBy"));

        Object createdAt = row.get("createdAt");
        if (createdAt instanceof Instant) {
            builder.createdAt((Instant) createdAt);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) row.get("properties");
        if (props != null) {
            builder.properties(props);
        }

        return builder.build();
    }
}
