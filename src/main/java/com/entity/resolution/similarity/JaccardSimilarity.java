package com.entity.resolution.similarity;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Jaccard similarity (token overlap).
 * Computes similarity as |intersection| / |union| of word tokens.
 */
public class JaccardSimilarity implements SimilarityAlgorithm {

    private final Pattern tokenPattern;

    public JaccardSimilarity() {
        this("\\s+");
    }

    public JaccardSimilarity(String tokenPattern) {
        this.tokenPattern = Pattern.compile(tokenPattern);
    }

    @Override
    public double compute(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }
        if (s1.equals(s2)) {
            return 1.0;
        }
        if (s1.isEmpty() || s2.isEmpty()) {
            return 0.0;
        }

        Set<String> tokens1 = tokenize(s1);
        Set<String> tokens2 = tokenize(s2);

        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 1.0;
        }
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0.0;
        }

        // Count intersection without creating a copy
        int intersectionSize = 0;
        for (String token : tokens1) {
            if (tokens2.contains(token)) {
                intersectionSize++;
            }
        }

        // |union| = |A| + |B| - |intersection|
        int unionSize = tokens1.size() + tokens2.size() - intersectionSize;

        return (double) intersectionSize / unionSize;
    }

    @Override
    public String getName() {
        return "Jaccard";
    }

    /**
     * Tokenizes a string into a set of tokens.
     */
    private Set<String> tokenize(String s) {
        String[] tokens = tokenPattern.split(s.toLowerCase());
        Set<String> tokenSet = new HashSet<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                tokenSet.add(trimmed);
            }
        }
        return tokenSet;
    }
}
