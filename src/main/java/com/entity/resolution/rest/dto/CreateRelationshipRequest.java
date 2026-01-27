package com.entity.resolution.rest.dto;

import java.util.Map;

/**
 * Request DTO for creating a relationship between two entities.
 */
public record CreateRelationshipRequest(
        String sourceEntityId,
        String targetEntityId,
        String relationshipType,
        Map<String, Object> properties
) {
    public CreateRelationshipRequest {
        if (sourceEntityId == null || sourceEntityId.isBlank()) {
            throw new IllegalArgumentException("sourceEntityId is required");
        }
        if (targetEntityId == null || targetEntityId.isBlank()) {
            throw new IllegalArgumentException("targetEntityId is required");
        }
        if (relationshipType == null || relationshipType.isBlank()) {
            throw new IllegalArgumentException("relationshipType is required");
        }
        if (properties == null) {
            properties = Map.of();
        }
    }
}
