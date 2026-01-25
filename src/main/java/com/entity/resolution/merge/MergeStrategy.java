package com.entity.resolution.merge;

/**
 * Strategies for resolving conflicts during entity merges.
 */
public enum MergeStrategy {
    /**
     * Keep the target entity's values for any conflicts.
     */
    KEEP_TARGET,

    /**
     * Keep the source entity's values for any conflicts.
     */
    KEEP_SOURCE,

    /**
     * Use the value with the higher confidence score.
     */
    HIGHEST_CONFIDENCE,

    /**
     * Use the most recently updated value.
     */
    MOST_RECENT
}
