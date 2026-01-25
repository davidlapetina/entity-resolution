package com.entity.resolution.similarity;

/**
 * Levenshtein distance-based similarity.
 * Computes similarity as 1 - (edit_distance / max_length).
 */
public class LevenshteinSimilarity implements SimilarityAlgorithm {

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

        int distance = levenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        return 1.0 - ((double) distance / maxLength);
    }

    @Override
    public String getName() {
        return "Levenshtein";
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     * Uses Wagner-Fischer algorithm with O(min(m,n)) space optimization.
     */
    private int levenshteinDistance(String s1, String s2) {
        // Ensure s1 is the shorter string for space optimization
        if (s1.length() > s2.length()) {
            String temp = s1;
            s1 = s2;
            s2 = temp;
        }

        int m = s1.length();
        int n = s2.length();

        int[] previousRow = new int[m + 1];
        int[] currentRow = new int[m + 1];

        // Initialize first row
        for (int i = 0; i <= m; i++) {
            previousRow[i] = i;
        }

        for (int j = 1; j <= n; j++) {
            currentRow[0] = j;

            for (int i = 1; i <= m; i++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                currentRow[i] = Math.min(
                        Math.min(currentRow[i - 1] + 1, previousRow[i] + 1),
                        previousRow[i - 1] + cost
                );
            }

            // Swap rows
            int[] temp = previousRow;
            previousRow = currentRow;
            currentRow = temp;
        }

        return previousRow[m];
    }
}
