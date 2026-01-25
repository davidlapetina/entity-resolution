package com.entity.resolution.similarity;

/**
 * Interface for similarity computation algorithms.
 * All implementations should return a score between 0.0 (no similarity) and 1.0 (identical).
 */
public interface SimilarityAlgorithm {

    /**
     * Computes the similarity between two strings.
     *
     * @param s1 first string
     * @param s2 second string
     * @return similarity score between 0.0 and 1.0
     */
    double compute(String s1, String s2);

    /**
     * Returns the name of this algorithm.
     */
    String getName();
}
