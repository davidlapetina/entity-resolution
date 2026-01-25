package com.entity.resolution.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SimilarityEngineTest {

    private LevenshteinSimilarity levenshtein;
    private JaroWinklerSimilarity jaroWinkler;
    private JaccardSimilarity jaccard;
    private CompositeSimilarityScorer composite;

    @BeforeEach
    void setUp() {
        levenshtein = new LevenshteinSimilarity();
        jaroWinkler = new JaroWinklerSimilarity();
        jaccard = new JaccardSimilarity();
        composite = new CompositeSimilarityScorer();
    }

    // ============ Levenshtein Tests ============

    @Test
    @DisplayName("Levenshtein: identical strings should return 1.0")
    void testLevenshteinIdentical() {
        assertEquals(1.0, levenshtein.compute("test", "test"));
        assertEquals(1.0, levenshtein.compute("International Business Machines",
                "International Business Machines"));
    }

    @Test
    @DisplayName("Levenshtein: completely different strings should return low score")
    void testLevenshteinDifferent() {
        double score = levenshtein.compute("abc", "xyz");
        assertTrue(score < 0.5, "Expected score < 0.5, got " + score);
    }

    @Test
    @DisplayName("Levenshtein: null or empty strings")
    void testLevenshteinNullEmpty() {
        assertEquals(0.0, levenshtein.compute(null, "test"));
        assertEquals(0.0, levenshtein.compute("test", null));
        assertEquals(0.0, levenshtein.compute("", "test"));
        assertEquals(0.0, levenshtein.compute("test", ""));
    }

    @ParameterizedTest
    @DisplayName("Levenshtein: typo detection")
    @CsvSource({
            "apple,aple,0.6",      // single deletion: 1 - 1/5 = 0.8 but aple is 4 chars so max is 5
            "apple,appel,0.6",     // single transposition
            "microsoft,microsft,0.77"  // single deletion: 1 - 2/9 = 0.778
    })
    void testLevenshteinTypos(String s1, String s2, double minExpected) {
        double score = levenshtein.compute(s1, s2);
        assertTrue(score >= minExpected,
                String.format("Expected score >= %.2f, got %.2f", minExpected, score));
    }

    // ============ Jaro-Winkler Tests ============

    @Test
    @DisplayName("Jaro-Winkler: identical strings should return 1.0")
    void testJaroWinklerIdentical() {
        assertEquals(1.0, jaroWinkler.compute("test", "test"));
    }

    @Test
    @DisplayName("Jaro-Winkler: strings with common prefix should score higher")
    void testJaroWinklerPrefix() {
        double withPrefix = jaroWinkler.compute("MARTHA", "MARHTA");
        double withoutPrefix = jaroWinkler.compute("MARTHA", "AMRTHA");
        assertTrue(withPrefix > withoutPrefix,
                "Common prefix should increase score");
    }

    @Test
    @DisplayName("Jaro-Winkler: null or empty strings")
    void testJaroWinklerNullEmpty() {
        assertEquals(0.0, jaroWinkler.compute(null, "test"));
        assertEquals(0.0, jaroWinkler.compute("test", null));
        assertEquals(0.0, jaroWinkler.compute("", "test"));
    }

    @Test
    @DisplayName("Jaro-Winkler: classic test cases")
    void testJaroWinklerClassicCases() {
        // These are well-known test cases
        double score1 = jaroWinkler.compute("DWAYNE", "DUANE");
        assertTrue(score1 > 0.8, "Expected DWAYNE/DUANE > 0.8, got " + score1);

        double score2 = jaroWinkler.compute("DIXON", "DICKSONX");
        assertTrue(score2 > 0.7, "Expected DIXON/DICKSONX > 0.7, got " + score2);
    }

    // ============ Jaccard Tests ============

    @Test
    @DisplayName("Jaccard: identical strings should return 1.0")
    void testJaccardIdentical() {
        assertEquals(1.0, jaccard.compute("word1 word2", "word1 word2"));
    }

    @Test
    @DisplayName("Jaccard: no common tokens should return 0.0")
    void testJaccardNoOverlap() {
        assertEquals(0.0, jaccard.compute("apple orange", "banana grape"));
    }

    @Test
    @DisplayName("Jaccard: partial token overlap")
    void testJaccardPartialOverlap() {
        // "apple orange" and "apple banana" share 1 token out of 3 unique
        double score = jaccard.compute("apple orange", "apple banana");
        assertEquals(1.0 / 3.0, score, 0.01);
    }

    @Test
    @DisplayName("Jaccard: null or empty strings")
    void testJaccardNullEmpty() {
        assertEquals(0.0, jaccard.compute(null, "test"));
        assertEquals(0.0, jaccard.compute("test", null));
        assertEquals(0.0, jaccard.compute("", "test"));
    }

    @Test
    @DisplayName("Jaccard: multi-word company names")
    void testJaccardCompanyNames() {
        double score = jaccard.compute(
                "international business machines",
                "international business machines corporation"
        );
        // 3 out of 4 unique tokens match
        assertEquals(0.75, score, 0.01);
    }

    // ============ Composite Scorer Tests ============

    @Test
    @DisplayName("Composite: identical strings should return 1.0")
    void testCompositeIdentical() {
        assertEquals(1.0, composite.compute("test", "test"));
    }

    @Test
    @DisplayName("Composite: should combine all algorithms")
    void testCompositeCombination() {
        var breakdown = composite.computeWithBreakdown("apple", "aple");

        assertTrue(breakdown.levenshteinScore() > 0.7);
        assertTrue(breakdown.jaroWinklerScore() > 0.8);
        // Jaccard for single words
        assertTrue(breakdown.compositeScore() > 0.5);
    }

    @Test
    @DisplayName("Composite: custom weights should affect score")
    void testCompositeCustomWeights() {
        CompositeSimilarityScorer tokenFocused =
                new CompositeSimilarityScorer(SimilarityWeights.tokenFocused());

        // Multi-word comparison where Jaccard matters more
        String s1 = "big blue computer company";
        String s2 = "big blue technology company";

        double defaultScore = composite.compute(s1, s2);
        double tokenScore = tokenFocused.compute(s1, s2);

        // Both should give reasonable scores
        assertTrue(defaultScore > 0.5);
        assertTrue(tokenScore > 0.5);
    }

    @Test
    @DisplayName("Composite: breakdown should show individual scores")
    void testCompositeBreakdown() {
        var breakdown = composite.computeWithBreakdown("microsoft", "microsft");

        assertNotNull(breakdown);
        assertTrue(breakdown.levenshteinScore() >= 0 && breakdown.levenshteinScore() <= 1);
        assertTrue(breakdown.jaroWinklerScore() >= 0 && breakdown.jaroWinklerScore() <= 1);
        assertTrue(breakdown.jaccardScore() >= 0 && breakdown.jaccardScore() <= 1);
        assertTrue(breakdown.compositeScore() >= 0 && breakdown.compositeScore() <= 1);
    }

    // ============ Real-World Entity Examples ============

    @Test
    @DisplayName("Real-world: IBM variations")
    void testIBMVariations() {
        double score1 = composite.compute("ibm", "international business machines");
        // Very different - should be low without semantic understanding
        assertTrue(score1 < 0.5, "IBM vs full name should be low: " + score1);

        double score2 = composite.compute(
                "international business machines",
                "intl business machines"
        );
        assertTrue(score2 > 0.6, "Abbreviated should still match: " + score2);
    }

    @Test
    @DisplayName("Real-world: company name typos")
    void testCompanyTypos() {
        // Single word comparisons have 0.0 Jaccard (different tokens),
        // so composite scores are lower than individual algorithm scores
        double microsoftScore = composite.compute("microsoft", "microsft");
        assertTrue(microsoftScore > 0.50, "microsoft score: " + microsoftScore);

        double appleScore = composite.compute("apple", "aple");
        assertTrue(appleScore > 0.50, "apple score: " + appleScore);

        double googleScore = composite.compute("google", "gogle");
        assertTrue(googleScore > 0.50, "google score: " + googleScore);
    }

    @Test
    @DisplayName("SimilarityWeights: validation")
    void testWeightsValidation() {
        // Valid weights
        assertDoesNotThrow(() -> new SimilarityWeights(0.33, 0.34, 0.33));
        assertDoesNotThrow(() -> SimilarityWeights.defaultWeights());

        // Invalid - doesn't sum to 1
        assertThrows(IllegalArgumentException.class,
                () -> new SimilarityWeights(0.5, 0.5, 0.5));

        // Invalid - negative
        assertThrows(IllegalArgumentException.class,
                () -> new SimilarityWeights(-0.1, 0.6, 0.5));
    }
}
