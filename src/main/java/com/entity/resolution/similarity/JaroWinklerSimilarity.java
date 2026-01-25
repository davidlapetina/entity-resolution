package com.entity.resolution.similarity;

/**
 * Jaro-Winkler similarity algorithm.
 * Gives higher scores to strings that match from the beginning.
 */
public class JaroWinklerSimilarity implements SimilarityAlgorithm {

    private static final double DEFAULT_SCALING_FACTOR = 0.1;
    private static final int MAX_PREFIX_LENGTH = 4;

    private final double scalingFactor;

    public JaroWinklerSimilarity() {
        this(DEFAULT_SCALING_FACTOR);
    }

    public JaroWinklerSimilarity(double scalingFactor) {
        if (scalingFactor < 0 || scalingFactor > 0.25) {
            throw new IllegalArgumentException("Scaling factor must be between 0 and 0.25");
        }
        this.scalingFactor = scalingFactor;
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

        double jaroSimilarity = computeJaro(s1, s2);

        // Calculate common prefix length (up to MAX_PREFIX_LENGTH)
        int prefixLength = 0;
        int maxPrefixLength = Math.min(MAX_PREFIX_LENGTH, Math.min(s1.length(), s2.length()));
        while (prefixLength < maxPrefixLength && s1.charAt(prefixLength) == s2.charAt(prefixLength)) {
            prefixLength++;
        }

        // Jaro-Winkler formula: jw = jaro + (prefix * scalingFactor * (1 - jaro))
        return jaroSimilarity + (prefixLength * scalingFactor * (1.0 - jaroSimilarity));
    }

    @Override
    public String getName() {
        return "Jaro-Winkler";
    }

    /**
     * Computes the Jaro similarity between two strings.
     */
    private double computeJaro(String s1, String s2) {
        int s1Length = s1.length();
        int s2Length = s2.length();

        // Calculate match window
        int matchWindow = Math.max(0, Math.max(s1Length, s2Length) / 2 - 1);

        boolean[] s1Matches = new boolean[s1Length];
        boolean[] s2Matches = new boolean[s2Length];

        int matches = 0;
        int transpositions = 0;

        // Find matches
        for (int i = 0; i < s1Length; i++) {
            int start = Math.max(0, i - matchWindow);
            int end = Math.min(i + matchWindow + 1, s2Length);

            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) {
            return 0.0;
        }

        // Count transpositions
        int k = 0;
        for (int i = 0; i < s1Length; i++) {
            if (!s1Matches[i]) {
                continue;
            }
            while (!s2Matches[k]) {
                k++;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                transpositions++;
            }
            k++;
        }

        // Jaro formula: (m/|s1| + m/|s2| + (m-t/2)/m) / 3
        double m = matches;
        double t = transpositions / 2.0;
        return ((m / s1Length) + (m / s2Length) + ((m - t) / m)) / 3.0;
    }
}
