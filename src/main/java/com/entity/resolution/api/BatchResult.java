package com.entity.resolution.api;

import java.util.List;

/**
 * Result of a batch resolution operation.
 */
public record BatchResult(
        int totalEntitiesResolved,
        int newEntitiesCreated,
        int entitiesMerged,
        int relationshipsCreated,
        List<String> errors
) {
    public BatchResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    /**
     * Returns true if the batch completed without errors.
     */
    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * Returns true if any errors occurred during the batch.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        return "BatchResult{" +
                "resolved=" + totalEntitiesResolved +
                ", new=" + newEntitiesCreated +
                ", merged=" + entitiesMerged +
                ", relationships=" + relationshipsCreated +
                ", errors=" + errors.size() +
                '}';
    }
}
