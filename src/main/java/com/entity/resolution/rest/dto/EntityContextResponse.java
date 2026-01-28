package com.entity.resolution.rest.dto;

import com.entity.resolution.api.EntityContext;
import com.entity.resolution.core.model.MergeRecord;
import com.entity.resolution.core.model.Relationship;
import com.entity.resolution.core.model.Synonym;

import java.util.List;

/**
 * REST response DTO for an entity context bundle.
 */
public record EntityContextResponse(
        EntityResponse entity,
        List<Synonym> synonyms,
        List<Relationship> relationships,
        List<MatchDecisionResponse> decisions,
        List<MergeRecord> mergeHistory
) {
    /**
     * Creates a response DTO from a domain model.
     */
    public static EntityContextResponse from(EntityContext context) {
        return new EntityContextResponse(
                EntityResponse.fromEntity(context.entity()),
                context.synonyms(),
                context.relationships(),
                context.decisions().stream()
                        .map(MatchDecisionResponse::from)
                        .toList(),
                context.mergeHistory()
        );
    }
}
