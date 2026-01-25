package com.entity.resolution.similarity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composite similarity scorer that combines multiple algorithms with configurable weights.
 * Formula: score = w1*levenshtein + w2*jaroWinkler + w3*jaccard
 */
public class CompositeSimilarityScorer implements SimilarityAlgorithm {
    private static final Logger log = LoggerFactory.getLogger(CompositeSimilarityScorer.class);

    private final LevenshteinSimilarity levenshtein;
    private final JaroWinklerSimilarity jaroWinkler;
    private final JaccardSimilarity jaccard;
    private final SimilarityWeights weights;

    public CompositeSimilarityScorer() {
        this(SimilarityWeights.defaultWeights());
    }

    public CompositeSimilarityScorer(SimilarityWeights weights) {
        this.levenshtein = new LevenshteinSimilarity();
        this.jaroWinkler = new JaroWinklerSimilarity();
        this.jaccard = new JaccardSimilarity();
        this.weights = weights;
    }

    @Override
    public double compute(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }

        double levScore = levenshtein.compute(s1, s2);
        double jwScore = jaroWinkler.compute(s1, s2);
        double jaccardScore = jaccard.compute(s1, s2);

        double compositeScore = weights.levenshteinWeight() * levScore
                + weights.jaroWinklerWeight() * jwScore
                + weights.jaccardWeight() * jaccardScore;

        log.debug("Similarity scores for '{}' vs '{}': Levenshtein={}, Jaro-Winkler={}, Jaccard={}, Composite={}",
                s1, s2, levScore, jwScore, jaccardScore, compositeScore);

        return compositeScore;
    }

    @Override
    public String getName() {
        return "Composite";
    }

    /**
     * Computes detailed similarity breakdown.
     */
    public SimilarityBreakdown computeWithBreakdown(String s1, String s2) {
        double levScore = levenshtein.compute(s1, s2);
        double jwScore = jaroWinkler.compute(s1, s2);
        double jaccardScore = jaccard.compute(s1, s2);
        double compositeScore = weights.levenshteinWeight() * levScore
                + weights.jaroWinklerWeight() * jwScore
                + weights.jaccardWeight() * jaccardScore;

        return new SimilarityBreakdown(levScore, jwScore, jaccardScore, compositeScore, weights);
    }

    /**
     * Gets the current weights configuration.
     */
    public SimilarityWeights getWeights() {
        return weights;
    }

    /**
     * Creates a new scorer with updated weights.
     */
    public CompositeSimilarityScorer withWeights(SimilarityWeights newWeights) {
        return new CompositeSimilarityScorer(newWeights);
    }

    /**
     * Detailed breakdown of similarity scores from each algorithm.
     */
    public record SimilarityBreakdown(
            double levenshteinScore,
            double jaroWinklerScore,
            double jaccardScore,
            double compositeScore,
            SimilarityWeights weights
    ) {
        @Override
        public String toString() {
            return String.format(
                    "SimilarityBreakdown{levenshtein=%.4f (w=%.2f), jaroWinkler=%.4f (w=%.2f), jaccard=%.4f (w=%.2f), composite=%.4f}",
                    levenshteinScore, weights.levenshteinWeight(),
                    jaroWinklerScore, weights.jaroWinklerWeight(),
                    jaccardScore, weights.jaccardWeight(),
                    compositeScore
            );
        }
    }
}
