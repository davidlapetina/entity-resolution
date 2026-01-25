package com.entity.resolution.core.model;

/**
 * Decision outcome from the matching process.
 * Determines the action to take based on confidence scores.
 */
public enum MatchDecision {
    /**
     * High confidence match (>= 0.92 by default).
     * Entities will be automatically merged.
     */
    AUTO_MERGE,

    /**
     * Medium confidence match.
     * Create synonym relationship but don't merge entities.
     */
    SYNONYM_ONLY,

    /**
     * Low confidence match requiring human review.
     */
    REVIEW,

    /**
     * Insufficient confidence from deterministic matching.
     * Escalate to LLM for semantic enrichment if enabled.
     */
    LLM_ENRICH,

    /**
     * No match found - entities are distinct.
     */
    NO_MATCH
}
