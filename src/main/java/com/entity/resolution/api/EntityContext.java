package com.entity.resolution.api;

import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.MergeRecord;
import com.entity.resolution.core.model.Relationship;
import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.decision.MatchDecisionRecord;

import java.util.List;

/**
 * Bundled context for an entity, containing the entity itself along with
 * its synonyms, relationships, match decisions, and merge history.
 * Provides a single-call alternative to multiple individual lookups.
 *
 * @param entity       the canonical entity
 * @param synonyms     all synonyms linked to this entity
 * @param relationships all relationships involving this entity
 * @param decisions    all match decisions involving this entity
 * @param mergeHistory merge records for this entity
 */
public record EntityContext(
        Entity entity,
        List<Synonym> synonyms,
        List<Relationship> relationships,
        List<MatchDecisionRecord> decisions,
        List<MergeRecord> mergeHistory
) {
    public EntityContext {
        synonyms = synonyms != null ? List.copyOf(synonyms) : List.of();
        relationships = relationships != null ? List.copyOf(relationships) : List.of();
        decisions = decisions != null ? List.copyOf(decisions) : List.of();
        mergeHistory = mergeHistory != null ? List.copyOf(mergeHistory) : List.of();
    }
}
