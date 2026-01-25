package com.entity.resolution.similarity;

/**
 * Configuration for similarity algorithm weights in composite scoring.
 */
public record SimilarityWeights(
        double levenshteinWeight,
        double jaroWinklerWeight,
        double jaccardWeight
) {
    public SimilarityWeights {
        if (levenshteinWeight < 0 || jaroWinklerWeight < 0 || jaccardWeight < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        double sum = levenshteinWeight + jaroWinklerWeight + jaccardWeight;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalArgumentException("Weights must sum to 1.0, got " + sum);
        }
    }

    /**
     * Default weights giving equal importance to each algorithm.
     */
    public static SimilarityWeights defaultWeights() {
        return new SimilarityWeights(0.33, 0.34, 0.33);
    }

    /**
     * Weights favoring Jaro-Winkler (good for names with common prefixes).
     */
    public static SimilarityWeights jaroWinklerFocused() {
        return new SimilarityWeights(0.2, 0.5, 0.3);
    }

    /**
     * Weights favoring token overlap (good for multi-word names).
     */
    public static SimilarityWeights tokenFocused() {
        return new SimilarityWeights(0.2, 0.3, 0.5);
    }

    /**
     * Weights favoring edit distance (good for typo detection).
     */
    public static SimilarityWeights editDistanceFocused() {
        return new SimilarityWeights(0.5, 0.3, 0.2);
    }
}
