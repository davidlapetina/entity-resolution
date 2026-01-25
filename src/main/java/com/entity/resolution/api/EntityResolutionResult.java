package com.entity.resolution.api;

import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.Synonym;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Result of an entity resolution operation.
 *
 * Contains the canonical entity reference, synonyms, decision, confidence,
 * and detailed flags about what happened during resolution.
 *
 * Key design: Returns {@link EntityReference} instead of raw Entity to prevent
 * clients from storing stale IDs that may become invalid after merges.
 */
public final class EntityResolutionResult {

    private final EntityReference entityReference;
    private final Entity canonicalEntity; // For backward compatibility and internal use
    private final List<Synonym> synonyms;
    private final MatchDecision decision;
    private final double confidence;
    private final String reasoning;

    // Enhanced flags for scenarios
    private final boolean isNewEntity;
    private final boolean wasMerged;
    private final boolean wasMatchedViaSynonym;
    private final boolean wasNewSynonymCreated;
    private final String inputName;
    private final String matchedName;

    private EntityResolutionResult(Builder builder) {
        this.entityReference = builder.entityReference;
        this.canonicalEntity = builder.canonicalEntity;
        this.synonyms = builder.synonyms != null ? List.copyOf(builder.synonyms) : List.of();
        this.decision = Objects.requireNonNull(builder.decision, "decision is required");
        this.confidence = builder.confidence;
        this.reasoning = builder.reasoning;
        this.isNewEntity = builder.isNewEntity;
        this.wasMerged = builder.wasMerged;
        this.wasMatchedViaSynonym = builder.wasMatchedViaSynonym;
        this.wasNewSynonymCreated = builder.wasNewSynonymCreated;
        this.inputName = builder.inputName;
        this.matchedName = builder.matchedName;

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
    }

    /**
     * Returns an opaque reference to the canonical entity.
     * Use this reference for creating relationships - it will always
     * resolve to the current canonical ID even if merges occur.
     */
    public EntityReference getEntityReference() {
        return entityReference;
    }

    /**
     * Returns the canonical entity.
     * @deprecated Use {@link #getEntityReference()} instead to get a merge-safe handle.
     */
    @Deprecated
    public Entity canonicalEntity() {
        return canonicalEntity;
    }

    /**
     * Returns the canonical entity (for backward compatibility).
     */
    public Entity getCanonicalEntity() {
        return canonicalEntity;
    }

    public List<Synonym> synonyms() {
        return synonyms;
    }

    public List<Synonym> getSynonyms() {
        return synonyms;
    }

    public MatchDecision decision() {
        return decision;
    }

    public MatchDecision getDecision() {
        return decision;
    }

    public double confidence() {
        return confidence;
    }

    public double getConfidence() {
        return confidence;
    }

    /**
     * Returns the match confidence score (0.0 to 1.0).
     * For new entities, this is 1.0. For matches, this reflects the similarity score.
     */
    public double getMatchConfidence() {
        return confidence;
    }

    public String reasoning() {
        return reasoning;
    }

    public String getReasoning() {
        return reasoning;
    }

    public boolean isNewEntity() {
        return isNewEntity;
    }

    public boolean wasMerged() {
        return wasMerged;
    }

    /**
     * Returns true if the entity was matched via a synonym lookup.
     * This indicates that the input name didn't match directly but was
     * found through an existing synonym.
     */
    public boolean wasMatchedViaSynonym() {
        return wasMatchedViaSynonym;
    }

    /**
     * Returns true if a new synonym was created during this resolution.
     * This happens when a fuzzy match creates a synonym link to an existing entity.
     */
    public boolean wasNewSynonymCreated() {
        return wasNewSynonymCreated;
    }

    /**
     * Returns the original input name that was resolved.
     */
    public String getInputName() {
        return inputName;
    }

    /**
     * Returns the name that was matched against.
     * For exact matches, this equals the canonical name.
     * For synonym matches, this is the synonym value.
     * For fuzzy matches, this is the matched entity's canonical name.
     */
    public String getMatchedName() {
        return matchedName;
    }

    /**
     * Returns true if resolution was successful and an entity was found/created.
     */
    public boolean isResolved() {
        return canonicalEntity != null;
    }

    /**
     * Returns true if this result requires human review.
     */
    public boolean requiresReview() {
        return decision == MatchDecision.REVIEW;
    }

    /**
     * Returns true if a match was found (not a new entity).
     */
    public boolean hasMatch() {
        return !isNewEntity && canonicalEntity != null;
    }

    // ========== Static Factory Methods ==========

    /**
     * Creates a result for a new entity (no match found).
     */
    public static EntityResolutionResult newEntity(Entity entity, String inputName,
                                                    Supplier<String> canonicalIdResolver) {
        return builder()
                .canonicalEntity(entity)
                .entityReference(EntityReference.withResolver(entity.getId(), entity.getType(), canonicalIdResolver))
                .decision(MatchDecision.NO_MATCH)
                .confidence(1.0)
                .isNewEntity(true)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .inputName(inputName)
                .matchedName(entity.getCanonicalName())
                .reasoning("No matching entity found - created new entity")
                .build();
    }

    /**
     * Creates a result for a new entity (no match found) - simple version.
     */
    public static EntityResolutionResult newEntity(Entity entity) {
        return builder()
                .canonicalEntity(entity)
                .entityReference(EntityReference.of(entity.getId(), entity.getType()))
                .decision(MatchDecision.NO_MATCH)
                .confidence(1.0)
                .isNewEntity(true)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .matchedName(entity.getCanonicalName())
                .reasoning("No matching entity found - created new entity")
                .build();
    }

    /**
     * Creates a result for an exact match.
     */
    public static EntityResolutionResult exactMatch(Entity entity, List<Synonym> synonyms,
                                                     Supplier<String> canonicalIdResolver) {
        return builder()
                .canonicalEntity(entity)
                .entityReference(EntityReference.withResolver(entity.getId(), entity.getType(), canonicalIdResolver))
                .synonyms(synonyms)
                .decision(MatchDecision.AUTO_MERGE)
                .confidence(1.0)
                .isNewEntity(false)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .matchedName(entity.getCanonicalName())
                .reasoning("Exact match on normalized name")
                .build();
    }

    /**
     * Creates a result for an exact match - simple version.
     */
    public static EntityResolutionResult exactMatch(Entity entity, List<Synonym> synonyms) {
        return builder()
                .canonicalEntity(entity)
                .entityReference(EntityReference.of(entity.getId(), entity.getType()))
                .synonyms(synonyms)
                .decision(MatchDecision.AUTO_MERGE)
                .confidence(1.0)
                .isNewEntity(false)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .matchedName(entity.getCanonicalName())
                .reasoning("Exact match on normalized name")
                .build();
    }

    /**
     * Creates a result for a synonym match.
     */
    public static EntityResolutionResult synonymMatch(Entity entity, List<Synonym> synonyms,
                                                       String matchedSynonym, String inputName,
                                                       Supplier<String> canonicalIdResolver) {
        return builder()
                .canonicalEntity(entity)
                .entityReference(EntityReference.withResolver(entity.getId(), entity.getType(), canonicalIdResolver))
                .synonyms(synonyms)
                .decision(MatchDecision.AUTO_MERGE)
                .confidence(1.0)
                .isNewEntity(false)
                .wasMerged(false)
                .wasMatchedViaSynonym(true)
                .wasNewSynonymCreated(false)
                .inputName(inputName)
                .matchedName(matchedSynonym)
                .reasoning("Matched via existing synonym: " + matchedSynonym)
                .build();
    }

    /**
     * Creates a result for a fuzzy match that was auto-merged.
     */
    public static EntityResolutionResult autoMerged(Entity canonicalEntity, List<Synonym> synonyms,
                                                     double confidence, String reasoning,
                                                     Supplier<String> canonicalIdResolver) {
        return builder()
                .canonicalEntity(canonicalEntity)
                .entityReference(EntityReference.withResolver(canonicalEntity.getId(), canonicalEntity.getType(), canonicalIdResolver))
                .synonyms(synonyms)
                .decision(MatchDecision.AUTO_MERGE)
                .confidence(confidence)
                .isNewEntity(false)
                .wasMerged(true)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(true)
                .matchedName(canonicalEntity.getCanonicalName())
                .reasoning(reasoning)
                .build();
    }

    /**
     * Creates a result for a match that created a synonym only.
     */
    public static EntityResolutionResult synonymCreated(Entity canonicalEntity, List<Synonym> synonyms,
                                                         double confidence, String reasoning,
                                                         String inputName,
                                                         Supplier<String> canonicalIdResolver) {
        return builder()
                .canonicalEntity(canonicalEntity)
                .entityReference(EntityReference.withResolver(canonicalEntity.getId(), canonicalEntity.getType(), canonicalIdResolver))
                .synonyms(synonyms)
                .decision(MatchDecision.SYNONYM_ONLY)
                .confidence(confidence)
                .isNewEntity(false)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(true)
                .inputName(inputName)
                .matchedName(canonicalEntity.getCanonicalName())
                .reasoning(reasoning)
                .build();
    }

    /**
     * Creates a result requiring manual review.
     */
    public static EntityResolutionResult requiresReview(Entity candidateEntity, double confidence,
                                                         String reasoning,
                                                         Supplier<String> canonicalIdResolver) {
        return builder()
                .canonicalEntity(candidateEntity)
                .entityReference(EntityReference.withResolver(candidateEntity.getId(), candidateEntity.getType(), canonicalIdResolver))
                .decision(MatchDecision.REVIEW)
                .confidence(confidence)
                .isNewEntity(false)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .matchedName(candidateEntity.getCanonicalName())
                .reasoning(reasoning)
                .build();
    }

    /**
     * Creates a result that was enriched by LLM.
     */
    public static EntityResolutionResult llmEnriched(Entity candidateEntity, MatchDecision decision,
                                                      double confidence, String reasoning,
                                                      Supplier<String> canonicalIdResolver) {
        return builder()
                .canonicalEntity(candidateEntity)
                .entityReference(EntityReference.withResolver(candidateEntity.getId(), candidateEntity.getType(), canonicalIdResolver))
                .decision(decision)
                .confidence(confidence)
                .isNewEntity(false)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .matchedName(candidateEntity.getCanonicalName())
                .reasoning("LLM enriched: " + reasoning)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "EntityResolutionResult{" +
                "entityId='" + (canonicalEntity != null ? canonicalEntity.getId() : "null") + '\'' +
                ", decision=" + decision +
                ", confidence=" + confidence +
                ", isNewEntity=" + isNewEntity +
                ", wasMerged=" + wasMerged +
                ", wasMatchedViaSynonym=" + wasMatchedViaSynonym +
                ", wasNewSynonymCreated=" + wasNewSynonymCreated +
                '}';
    }

    public static class Builder {
        private EntityReference entityReference;
        private Entity canonicalEntity;
        private List<Synonym> synonyms = List.of();
        private MatchDecision decision;
        private double confidence;
        private String reasoning;
        private boolean isNewEntity;
        private boolean wasMerged;
        private boolean wasMatchedViaSynonym;
        private boolean wasNewSynonymCreated;
        private String inputName;
        private String matchedName;

        public Builder entityReference(EntityReference entityReference) {
            this.entityReference = entityReference;
            return this;
        }

        public Builder canonicalEntity(Entity canonicalEntity) {
            this.canonicalEntity = canonicalEntity;
            return this;
        }

        public Builder synonyms(List<Synonym> synonyms) {
            this.synonyms = synonyms;
            return this;
        }

        public Builder decision(MatchDecision decision) {
            this.decision = decision;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder isNewEntity(boolean isNewEntity) {
            this.isNewEntity = isNewEntity;
            return this;
        }

        public Builder wasMerged(boolean wasMerged) {
            this.wasMerged = wasMerged;
            return this;
        }

        public Builder wasMatchedViaSynonym(boolean wasMatchedViaSynonym) {
            this.wasMatchedViaSynonym = wasMatchedViaSynonym;
            return this;
        }

        public Builder wasNewSynonymCreated(boolean wasNewSynonymCreated) {
            this.wasNewSynonymCreated = wasNewSynonymCreated;
            return this;
        }

        public Builder inputName(String inputName) {
            this.inputName = inputName;
            return this;
        }

        public Builder matchedName(String matchedName) {
            this.matchedName = matchedName;
            return this;
        }

        public EntityResolutionResult build() {
            return new EntityResolutionResult(this);
        }
    }
}
