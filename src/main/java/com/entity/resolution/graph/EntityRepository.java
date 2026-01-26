package com.entity.resolution.graph;

import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityStatus;
import com.entity.resolution.core.model.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for Entity node CRUD operations.
 */
public class EntityRepository {
    private static final Logger log = LoggerFactory.getLogger(EntityRepository.class);

    private final CypherExecutor executor;

    public EntityRepository(CypherExecutor executor) {
        this.executor = executor;
    }

    public EntityRepository(GraphConnection connection) {
        this(new CypherExecutor(connection));
    }

    /**
     * Saves an entity to the graph.
     */
    public Entity save(Entity entity) {
        executor.createEntity(
                entity.getId(),
                entity.getCanonicalName(),
                entity.getNormalizedName(),
                entity.getType().name(),
                entity.getConfidenceScore()
        );
        return entity;
    }

    /**
     * Finds an entity by ID.
     */
    public Optional<Entity> findById(String id) {
        List<Map<String, Object>> results = executor.findEntityById(id);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(mapToEntity(results.get(0)));
    }

    /**
     * Finds entities by normalized name.
     */
    public List<Entity> findByNormalizedName(String normalizedName, EntityType type) {
        List<Map<String, Object>> results = executor.findCandidatesByNormalizedName(
                normalizedName, type.name());
        return results.stream().map(this::mapToEntity).toList();
    }

    /**
     * Finds all active entities of a given type.
     */
    public List<Entity> findAllActive(EntityType type) {
        List<Map<String, Object>> results = executor.findAllActiveEntities(type.name());
        return results.stream().map(this::mapToEntity).toList();
    }

    /**
     * Updates entity confidence score.
     */
    public void updateConfidence(String entityId, double confidence) {
        executor.updateEntityConfidence(entityId, confidence);
    }

    /**
     * Gets the canonical (active) entity for a potentially merged entity.
     */
    public Optional<Entity> getCanonical(String entityId) {
        // First check if the entity itself is active
        Optional<Entity> entity = findById(entityId);
        if (entity.isPresent() && entity.get().isActive()) {
            return entity;
        }

        // Follow merge chain to find canonical
        List<Map<String, Object>> results = executor.getCanonicalEntity(entityId);
        if (results.isEmpty()) {
            return entity; // Return original even if merged
        }
        return Optional.of(mapToEntity(results.get(0)));
    }

    /**
     * Saves an entity and persists its blocking keys.
     */
    public Entity save(Entity entity, Set<String> blockingKeys) {
        Entity saved = save(entity);
        if (blockingKeys != null && !blockingKeys.isEmpty()) {
            executor.createBlockingKeys(saved.getId(), blockingKeys);
        }
        return saved;
    }

    /**
     * Finds candidate entities that share any of the given blocking keys.
     */
    public List<Entity> findCandidatesByBlockingKeys(Set<String> keys, EntityType type) {
        List<Map<String, Object>> results = executor.findCandidatesByBlockingKeys(keys, type.name());
        return results.stream().map(this::mapToEntity).toList();
    }

    /**
     * Records a merge between two entities.
     */
    public void recordMerge(String sourceEntityId, String targetEntityId, double confidence, String reason) {
        // Migrate synonyms first
        executor.migrateSynonyms(sourceEntityId, targetEntityId);

        // Migrate other relationships
        executor.migrateRelationships(sourceEntityId, targetEntityId);

        // Record the merge
        executor.recordMerge(sourceEntityId, targetEntityId, confidence, reason);
    }

    private Entity mapToEntity(Map<String, Object> row) {
        Entity.Builder builder = Entity.builder()
                .id((String) row.get("id"))
                .canonicalName((String) row.get("canonicalName"))
                .normalizedName((String) row.get("normalizedName"))
                .type(EntityType.valueOf((String) row.get("type")));

        Object confidence = row.get("confidenceScore");
        if (confidence != null) {
            builder.confidenceScore(((Number) confidence).doubleValue());
        }

        Object status = row.get("status");
        if (status != null) {
            builder.status(EntityStatus.valueOf((String) status));
        }

        return builder.build();
    }

    public CypherExecutor getExecutor() {
        return executor;
    }
}
