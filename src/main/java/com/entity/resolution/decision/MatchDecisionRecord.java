package com.entity.resolution.decision;

import com.entity.resolution.core.model.EntityType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a match decision evaluation.
 * Every candidate comparison during resolution produces one of these,
 * including NO_MATCH outcomes. Persisted to the graph for full explainability.
 *
 * <p>This object captures the complete scoring breakdown, thresholds snapshot,
 * and final outcome so that any decision can be reconstructed after the fact.</p>
 */
public final class MatchDecisionRecord {

    private final String id;
    private final String inputEntityTempId;
    private final String candidateEntityId;
    private final EntityType entityType;

    // Individual algorithm scores
    private final double exactScore;
    private final double levenshteinScore;
    private final double jaroWinklerScore;
    private final double jaccardScore;
    private final Double llmScore;          // nullable
    private final Double graphContextScore; // nullable

    private final double finalScore;
    private final DecisionOutcome outcome;

    // Thresholds snapshot at evaluation time
    private final double autoMergeThreshold;
    private final double synonymThreshold;
    private final double reviewThreshold;

    // Metadata
    private final Instant evaluatedAt;
    private final String evaluator; // SYSTEM | LLM | HUMAN

    private MatchDecisionRecord(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.inputEntityTempId = Objects.requireNonNull(builder.inputEntityTempId, "inputEntityTempId is required");
        this.candidateEntityId = Objects.requireNonNull(builder.candidateEntityId, "candidateEntityId is required");
        this.entityType = Objects.requireNonNull(builder.entityType, "entityType is required");
        this.exactScore = builder.exactScore;
        this.levenshteinScore = builder.levenshteinScore;
        this.jaroWinklerScore = builder.jaroWinklerScore;
        this.jaccardScore = builder.jaccardScore;
        this.llmScore = builder.llmScore;
        this.graphContextScore = builder.graphContextScore;
        this.finalScore = builder.finalScore;
        this.outcome = Objects.requireNonNull(builder.outcome, "outcome is required");
        this.autoMergeThreshold = builder.autoMergeThreshold;
        this.synonymThreshold = builder.synonymThreshold;
        this.reviewThreshold = builder.reviewThreshold;
        this.evaluatedAt = builder.evaluatedAt != null ? builder.evaluatedAt : Instant.now();
        this.evaluator = builder.evaluator != null ? builder.evaluator : "SYSTEM";
    }

    public String getId() { return id; }
    public String getInputEntityTempId() { return inputEntityTempId; }
    public String getCandidateEntityId() { return candidateEntityId; }
    public EntityType getEntityType() { return entityType; }
    public double getExactScore() { return exactScore; }
    public double getLevenshteinScore() { return levenshteinScore; }
    public double getJaroWinklerScore() { return jaroWinklerScore; }
    public double getJaccardScore() { return jaccardScore; }
    public Double getLlmScore() { return llmScore; }
    public Double getGraphContextScore() { return graphContextScore; }
    public double getFinalScore() { return finalScore; }
    public DecisionOutcome getOutcome() { return outcome; }
    public double getAutoMergeThreshold() { return autoMergeThreshold; }
    public double getSynonymThreshold() { return synonymThreshold; }
    public double getReviewThreshold() { return reviewThreshold; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public String getEvaluator() { return evaluator; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MatchDecisionRecord that = (MatchDecisionRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MatchDecisionRecord{" +
                "id='" + id + '\'' +
                ", candidateEntityId='" + candidateEntityId + '\'' +
                ", finalScore=" + finalScore +
                ", outcome=" + outcome +
                ", evaluator='" + evaluator + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String inputEntityTempId;
        private String candidateEntityId;
        private EntityType entityType;
        private double exactScore;
        private double levenshteinScore;
        private double jaroWinklerScore;
        private double jaccardScore;
        private Double llmScore;
        private Double graphContextScore;
        private double finalScore;
        private DecisionOutcome outcome;
        private double autoMergeThreshold;
        private double synonymThreshold;
        private double reviewThreshold;
        private Instant evaluatedAt;
        private String evaluator;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder inputEntityTempId(String inputEntityTempId) { this.inputEntityTempId = inputEntityTempId; return this; }
        public Builder candidateEntityId(String candidateEntityId) { this.candidateEntityId = candidateEntityId; return this; }
        public Builder entityType(EntityType entityType) { this.entityType = entityType; return this; }
        public Builder exactScore(double exactScore) { this.exactScore = exactScore; return this; }
        public Builder levenshteinScore(double levenshteinScore) { this.levenshteinScore = levenshteinScore; return this; }
        public Builder jaroWinklerScore(double jaroWinklerScore) { this.jaroWinklerScore = jaroWinklerScore; return this; }
        public Builder jaccardScore(double jaccardScore) { this.jaccardScore = jaccardScore; return this; }
        public Builder llmScore(Double llmScore) { this.llmScore = llmScore; return this; }
        public Builder graphContextScore(Double graphContextScore) { this.graphContextScore = graphContextScore; return this; }
        public Builder finalScore(double finalScore) { this.finalScore = finalScore; return this; }
        public Builder outcome(DecisionOutcome outcome) { this.outcome = outcome; return this; }
        public Builder autoMergeThreshold(double autoMergeThreshold) { this.autoMergeThreshold = autoMergeThreshold; return this; }
        public Builder synonymThreshold(double synonymThreshold) { this.synonymThreshold = synonymThreshold; return this; }
        public Builder reviewThreshold(double reviewThreshold) { this.reviewThreshold = reviewThreshold; return this; }
        public Builder evaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; return this; }
        public Builder evaluator(String evaluator) { this.evaluator = evaluator; return this; }

        public MatchDecisionRecord build() {
            return new MatchDecisionRecord(this);
        }
    }
}
