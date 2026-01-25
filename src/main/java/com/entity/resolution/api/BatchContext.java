package com.entity.resolution.api;

import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.Relationship;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context for batch entity resolution operations.
 *
 * BatchContext provides:
 * - Deduplication within the batch (same name resolves to same entity)
 * - Deferred relationship creation (relationships created after all entities resolved)
 * - Transaction-like semantics (all or nothing commitment)
 *
 * Usage:
 * <pre>
 * try (BatchContext batch = resolver.beginBatch()) {
 *     EntityReference acme = batch.resolve("Acme Corp", EntityType.COMPANY).getEntityReference();
 *     EntityReference bigBlue = batch.resolve("Big Blue", EntityType.COMPANY).getEntityReference();
 *
 *     batch.createRelationship(acme, bigBlue, "PARTNER");
 *
 *     BatchResult result = batch.commit();
 * }
 * </pre>
 */
public class BatchContext implements AutoCloseable {

    private final EntityResolver resolver;
    private final ResolutionOptions options;
    private final Map<BatchKey, EntityResolutionResult> resolvedEntities;
    private final List<PendingRelationship> pendingRelationships;
    private boolean committed = false;
    private boolean closed = false;

    BatchContext(EntityResolver resolver, ResolutionOptions options) {
        this.resolver = resolver;
        this.options = options != null ? options : ResolutionOptions.defaults();
        this.resolvedEntities = new ConcurrentHashMap<>();
        this.pendingRelationships = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Resolves an entity within this batch context.
     * If the same name was already resolved in this batch, returns the cached result.
     */
    public EntityResolutionResult resolve(String entityName, EntityType entityType) {
        checkNotClosed();

        BatchKey key = new BatchKey(entityName, entityType);
        return resolvedEntities.computeIfAbsent(key, k ->
                resolver.resolve(entityName, entityType, options));
    }

    /**
     * Queues a relationship to be created when the batch is committed.
     * Relationships are created after all entities are resolved, ensuring
     * no stale references exist.
     */
    public void createRelationship(EntityReference source, EntityReference target,
                                    String relationshipType) {
        createRelationship(source, target, relationshipType, Map.of());
    }

    /**
     * Queues a relationship with properties to be created when the batch is committed.
     */
    public void createRelationship(EntityReference source, EntityReference target,
                                    String relationshipType, Map<String, Object> properties) {
        checkNotClosed();
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(relationshipType, "relationshipType is required");

        pendingRelationships.add(new PendingRelationship(source, target, relationshipType, properties));
    }

    /**
     * Commits the batch, creating all queued relationships.
     * Returns a summary of the batch operation.
     */
    public BatchResult commit() {
        checkNotClosed();
        if (committed) {
            throw new IllegalStateException("Batch already committed");
        }

        List<Relationship> createdRelationships = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Create all pending relationships
        for (PendingRelationship pending : pendingRelationships) {
            try {
                Relationship rel = resolver.createRelationship(
                        pending.source,
                        pending.target,
                        pending.relationshipType,
                        pending.properties
                );
                createdRelationships.add(rel);
            } catch (Exception e) {
                errors.add("Failed to create relationship " + pending.relationshipType +
                        " from " + pending.source.getId() + " to " + pending.target.getId() +
                        ": " + e.getMessage());
            }
        }

        committed = true;

        return new BatchResult(
                resolvedEntities.size(),
                countNewEntities(),
                countMergedEntities(),
                createdRelationships.size(),
                errors
        );
    }

    /**
     * Abandons the batch without committing relationships.
     * Entities that were created during resolution remain in the database.
     */
    public void rollback() {
        checkNotClosed();
        pendingRelationships.clear();
        closed = true;
    }

    @Override
    public void close() {
        if (!closed && !committed) {
            // Auto-commit on close if not explicitly committed or rolled back
            commit();
        }
        closed = true;
    }

    /**
     * Returns all entities resolved in this batch.
     */
    public Collection<EntityResolutionResult> getResolvedEntities() {
        return Collections.unmodifiableCollection(resolvedEntities.values());
    }

    /**
     * Returns the number of pending relationships queued for creation.
     */
    public int getPendingRelationshipCount() {
        return pendingRelationships.size();
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Batch context is closed");
        }
    }

    private int countNewEntities() {
        return (int) resolvedEntities.values().stream()
                .filter(EntityResolutionResult::isNewEntity)
                .count();
    }

    private int countMergedEntities() {
        return (int) resolvedEntities.values().stream()
                .filter(EntityResolutionResult::wasMerged)
                .count();
    }

    /**
     * Key for deduplication within a batch.
     */
    private record BatchKey(String name, EntityType type) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BatchKey batchKey = (BatchKey) o;
            // Use case-insensitive comparison for name
            return name.equalsIgnoreCase(batchKey.name) && type == batchKey.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name.toLowerCase(), type);
        }
    }

    /**
     * Pending relationship to be created on commit.
     */
    private record PendingRelationship(
            EntityReference source,
            EntityReference target,
            String relationshipType,
            Map<String, Object> properties
    ) {}
}
