package com.entity.resolution.core.model;

import java.util.Objects;

/**
 * Result of a match comparison between two entities or an entity and a name.
 * Contains the similarity score and the decision based on configured thresholds.
 */
public record MatchResult(
        double score,
        MatchDecision decision,
        String candidateEntityId,
        String matchedName,
        String reasoning
) {
    public MatchResult {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(decision, "decision is required");
    }

    /**
     * Creates a no-match result.
     */
    public static MatchResult noMatch() {
        return new MatchResult(0.0, MatchDecision.NO_MATCH, null, null, "No matching entity found");
    }

    /**
     * Creates a match result with score and auto-calculated decision.
     */
    public static MatchResult of(double score, String candidateEntityId, String matchedName,
                                  double autoMergeThreshold, double synonymThreshold, double reviewThreshold) {
        MatchDecision decision;
        if (score >= autoMergeThreshold) {
            decision = MatchDecision.AUTO_MERGE;
        } else if (score >= synonymThreshold) {
            decision = MatchDecision.SYNONYM_ONLY;
        } else if (score >= reviewThreshold) {
            decision = MatchDecision.REVIEW;
        } else {
            decision = MatchDecision.LLM_ENRICH;
        }
        return new MatchResult(score, decision, candidateEntityId, matchedName, null);
    }

    /**
     * Returns true if this result indicates a match was found.
     */
    public boolean hasMatch() {
        return decision != MatchDecision.NO_MATCH;
    }

    /**
     * Returns true if this result indicates entities should be automatically merged.
     */
    public boolean shouldAutoMerge() {
        return decision == MatchDecision.AUTO_MERGE;
    }

    /**
     * Returns true if this result indicates a synonym should be created.
     */
    public boolean shouldCreateSynonym() {
        return decision == MatchDecision.SYNONYM_ONLY || decision == MatchDecision.AUTO_MERGE;
    }

    /**
     * Returns true if this result requires human review.
     */
    public boolean requiresReview() {
        return decision == MatchDecision.REVIEW;
    }

    /**
     * Returns true if this result should be escalated to LLM enrichment.
     */
    public boolean shouldEnrichWithLLM() {
        return decision == MatchDecision.LLM_ENRICH;
    }
}
