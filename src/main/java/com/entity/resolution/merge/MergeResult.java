package com.entity.resolution.merge;

import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.MergeRecord;

import java.util.List;
import java.util.Objects;

/**
 * Result of a merge operation.
 */
public record MergeResult(
        boolean success,
        Entity canonicalEntity,
        Entity mergedEntity,
        MergeRecord mergeRecord,
        List<String> synonymsCreated,
        String duplicateEntityId,
        String errorMessage
) {
    public MergeResult {
        synonymsCreated = synonymsCreated != null ? List.copyOf(synonymsCreated) : List.of();
    }

    /**
     * Creates a successful merge result.
     */
    public static MergeResult success(Entity canonicalEntity, Entity mergedEntity,
                                       MergeRecord mergeRecord, List<String> synonymsCreated,
                                       String duplicateEntityId) {
        return new MergeResult(true, canonicalEntity, mergedEntity, mergeRecord,
                synonymsCreated, duplicateEntityId, null);
    }

    /**
     * Creates a failed merge result.
     */
    public static MergeResult failure(String errorMessage) {
        return new MergeResult(false, null, null, null, List.of(), null, errorMessage);
    }

    /**
     * Creates a failed merge result with context.
     */
    public static MergeResult failure(Entity sourceEntity, Entity targetEntity, String errorMessage) {
        return new MergeResult(false, targetEntity, sourceEntity, null, List.of(), null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }
}
