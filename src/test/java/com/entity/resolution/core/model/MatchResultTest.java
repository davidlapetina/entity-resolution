package com.entity.resolution.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatchResultTest {

    @Test
    @DisplayName("Should create no-match result")
    void testNoMatch() {
        MatchResult result = MatchResult.noMatch();

        assertEquals(0.0, result.score());
        assertEquals(MatchDecision.NO_MATCH, result.decision());
        assertNull(result.candidateEntityId());
        assertFalse(result.hasMatch());
        assertFalse(result.shouldAutoMerge());
        assertFalse(result.shouldCreateSynonym());
        assertFalse(result.requiresReview());
    }

    @Test
    @DisplayName("Should calculate AUTO_MERGE decision for high scores")
    void testAutoMergeDecision() {
        MatchResult result = MatchResult.of(0.95, "entity-1", "Test Corp",
                0.92, 0.80, 0.60);

        assertEquals(MatchDecision.AUTO_MERGE, result.decision());
        assertTrue(result.hasMatch());
        assertTrue(result.shouldAutoMerge());
        assertTrue(result.shouldCreateSynonym());
    }

    @Test
    @DisplayName("Should calculate SYNONYM_ONLY decision for medium scores")
    void testSynonymOnlyDecision() {
        MatchResult result = MatchResult.of(0.85, "entity-1", "Test Corp",
                0.92, 0.80, 0.60);

        assertEquals(MatchDecision.SYNONYM_ONLY, result.decision());
        assertTrue(result.hasMatch());
        assertFalse(result.shouldAutoMerge());
        assertTrue(result.shouldCreateSynonym());
    }

    @Test
    @DisplayName("Should calculate REVIEW decision for low-medium scores")
    void testReviewDecision() {
        MatchResult result = MatchResult.of(0.70, "entity-1", "Test Corp",
                0.92, 0.80, 0.60);

        assertEquals(MatchDecision.REVIEW, result.decision());
        assertTrue(result.hasMatch());
        assertTrue(result.requiresReview());
        assertFalse(result.shouldAutoMerge());
    }

    @Test
    @DisplayName("Should calculate LLM_ENRICH decision for low scores")
    void testLLMEnrichDecision() {
        MatchResult result = MatchResult.of(0.50, "entity-1", "Test Corp",
                0.92, 0.80, 0.60);

        assertEquals(MatchDecision.LLM_ENRICH, result.decision());
        assertTrue(result.shouldEnrichWithLLM());
    }

    @Test
    @DisplayName("Should reject invalid scores")
    void testInvalidScores() {
        assertThrows(IllegalArgumentException.class, () ->
                new MatchResult(-0.1, MatchDecision.NO_MATCH, null, null, null));

        assertThrows(IllegalArgumentException.class, () ->
                new MatchResult(1.1, MatchDecision.AUTO_MERGE, null, null, null));
    }

    @Test
    @DisplayName("Should require decision")
    void testRequiredDecision() {
        assertThrows(NullPointerException.class, () ->
                new MatchResult(0.5, null, null, null, null));
    }
}
