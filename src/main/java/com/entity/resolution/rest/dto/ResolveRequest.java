package com.entity.resolution.rest.dto;

/**
 * Request DTO for resolving a single entity.
 */
public record ResolveRequest(
        String name,
        String entityType,
        Double autoMergeThreshold,
        Double synonymThreshold,
        Double reviewThreshold,
        String sourceSystem
) {
    public ResolveRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
    }
}
