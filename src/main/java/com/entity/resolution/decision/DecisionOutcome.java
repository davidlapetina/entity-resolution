package com.entity.resolution.decision;

/**
 * Outcome of a match decision evaluation.
 * Maps to the action taken (or not taken) based on score thresholds.
 */
public enum DecisionOutcome {
    /** Score >= autoMergeThreshold: entities are automatically merged. */
    AUTO_MERGE,

    /** synonymThreshold <= score < autoMergeThreshold: synonym created. */
    SYNONYM,

    /** reviewThreshold <= score < synonymThreshold: queued for human review. */
    REVIEW,

    /** score < reviewThreshold: entities are distinct. */
    NO_MATCH
}
