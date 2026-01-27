package com.entity.resolution.rest.dto;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.core.model.Entity;

/**
 * Response DTO for entity resolution results.
 */
public record EntityResponse(
        String entityId,
        String canonicalName,
        String normalizedName,
        String entityType,
        String status,
        double confidenceScore,
        String decision,
        double matchConfidence,
        boolean isNewEntity,
        boolean wasMerged,
        boolean wasMatchedViaSynonym,
        boolean wasNewSynonymCreated,
        String inputName,
        String matchedName,
        String reasoning
) {
    public static EntityResponse from(EntityResolutionResult result) {
        Entity entity = result.getCanonicalEntity();
        return new EntityResponse(
                entity != null ? entity.getId() : null,
                entity != null ? entity.getCanonicalName() : null,
                entity != null ? entity.getNormalizedName() : null,
                entity != null ? entity.getType().name() : null,
                entity != null ? entity.getStatus().name() : null,
                entity != null ? entity.getConfidenceScore() : 0.0,
                result.getDecision().name(),
                result.getMatchConfidence(),
                result.isNewEntity(),
                result.wasMerged(),
                result.wasMatchedViaSynonym(),
                result.wasNewSynonymCreated(),
                result.getInputName(),
                result.getMatchedName(),
                result.getReasoning()
        );
    }

    public static EntityResponse fromEntity(Entity entity) {
        return new EntityResponse(
                entity.getId(),
                entity.getCanonicalName(),
                entity.getNormalizedName(),
                entity.getType().name(),
                entity.getStatus().name(),
                entity.getConfidenceScore(),
                null,
                entity.getConfidenceScore(),
                false,
                false,
                false,
                false,
                null,
                null,
                null
        );
    }
}
