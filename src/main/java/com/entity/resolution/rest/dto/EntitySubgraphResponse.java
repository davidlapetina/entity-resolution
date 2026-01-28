package com.entity.resolution.rest.dto;

import com.entity.resolution.api.EntitySubgraph;
import com.entity.resolution.core.model.Relationship;
import com.entity.resolution.core.model.Synonym;

import java.util.List;
import java.util.Map;

/**
 * REST response DTO for an entity subgraph export.
 */
public record EntitySubgraphResponse(
        EntityResponse rootEntity,
        List<Synonym> synonyms,
        List<Relationship> relationships,
        List<EntityResponse> relatedEntities,
        List<MatchDecisionResponse> decisions,
        int depth,
        Map<String, Object> metadata
) {
    /**
     * Creates a response DTO from a domain model.
     */
    public static EntitySubgraphResponse from(EntitySubgraph subgraph) {
        return new EntitySubgraphResponse(
                EntityResponse.fromEntity(subgraph.rootEntity()),
                subgraph.synonyms(),
                subgraph.relationships(),
                subgraph.relatedEntities().stream()
                        .map(EntityResponse::fromEntity)
                        .toList(),
                subgraph.decisions().stream()
                        .map(MatchDecisionResponse::from)
                        .toList(),
                subgraph.depth(),
                subgraph.metadata()
        );
    }
}
