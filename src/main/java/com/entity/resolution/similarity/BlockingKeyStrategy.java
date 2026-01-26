package com.entity.resolution.similarity;

import java.util.Set;

/**
 * Strategy interface for generating blocking keys from entity names.
 * Blocking keys are used to narrow the candidate set for fuzzy matching,
 * avoiding expensive O(n) full-scan comparisons.
 *
 * <p>Entities that share at least one blocking key are considered potential
 * candidates for matching. The goal is to generate keys that are coarse enough
 * to capture likely matches while fine enough to exclude obvious non-matches.</p>
 */
public interface BlockingKeyStrategy {

    /**
     * Generates a set of blocking keys for a normalized entity name.
     *
     * @param normalizedName the normalized entity name
     * @return set of blocking keys (never null, may be empty)
     */
    Set<String> generateKeys(String normalizedName);
}
