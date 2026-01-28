package com.entity.resolution.api;

import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.Relationship;
import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.decision.MatchDecisionRecord;

import java.util.List;
import java.util.Map;

/**
 * A subgraph rooted at an entity, containing the entity itself, its synonyms,
 * relationships, related entities (at the requested depth), and match decisions.
 * Designed for RAG (Retrieval-Augmented Generation) use cases where an LLM
 * needs the entity's full neighborhood as grounded context.
 *
 * @param rootEntity      the root entity of the subgraph
 * @param synonyms        all synonyms linked to the root entity
 * @param relationships   all relationships at the requested depth
 * @param relatedEntities entities connected to the root via relationships (at depth >= 1)
 * @param decisions       match decisions involving the root entity
 * @param depth           the depth at which the subgraph was exported
 * @param metadata        additional metadata (e.g., entity counts, relationship counts)
 */
public record EntitySubgraph(
        Entity rootEntity,
        List<Synonym> synonyms,
        List<Relationship> relationships,
        List<Entity> relatedEntities,
        List<MatchDecisionRecord> decisions,
        int depth,
        Map<String, Object> metadata
) {
    public EntitySubgraph {
        synonyms = synonyms != null ? List.copyOf(synonyms) : List.of();
        relationships = relationships != null ? List.copyOf(relationships) : List.of();
        relatedEntities = relatedEntities != null ? List.copyOf(relatedEntities) : List.of();
        decisions = decisions != null ? List.copyOf(decisions) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
