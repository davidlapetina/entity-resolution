package com.entity.resolution.api;

import com.entity.resolution.similarity.SimilarityWeights;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionOptionsTest {

    @Test
    @DisplayName("Should create default options")
    void testDefaultOptions() {
        ResolutionOptions options = ResolutionOptions.defaults();

        assertFalse(options.isUseLLM());
        assertEquals(0.92, options.getAutoMergeThreshold());
        assertEquals(0.80, options.getSynonymThreshold());
        assertEquals(0.60, options.getReviewThreshold());
        assertEquals(0.85, options.getLlmConfidenceThreshold());
        assertTrue(options.isAutoMergeEnabled());
        assertNotNull(options.getSimilarityWeights());
    }

    @Test
    @DisplayName("Should create options with LLM enabled")
    void testWithLLM() {
        ResolutionOptions options = ResolutionOptions.withLLM();

        assertTrue(options.isUseLLM());
    }

    @Test
    @DisplayName("Should create conservative options")
    void testConservative() {
        ResolutionOptions options = ResolutionOptions.conservative();

        assertEquals(0.98, options.getAutoMergeThreshold());
        assertEquals(0.90, options.getSynonymThreshold());
        assertEquals(0.70, options.getReviewThreshold());
        assertFalse(options.isAutoMergeEnabled());
    }

    @Test
    @DisplayName("Should allow custom thresholds")
    void testCustomThresholds() {
        ResolutionOptions options = ResolutionOptions.builder()
                .autoMergeThreshold(0.95)
                .synonymThreshold(0.85)
                .reviewThreshold(0.65)
                .build();

        assertEquals(0.95, options.getAutoMergeThreshold());
        assertEquals(0.85, options.getSynonymThreshold());
        assertEquals(0.65, options.getReviewThreshold());
    }

    @Test
    @DisplayName("Should reject invalid threshold values")
    void testInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () ->
                ResolutionOptions.builder().autoMergeThreshold(-0.1).build());

        assertThrows(IllegalArgumentException.class, () ->
                ResolutionOptions.builder().autoMergeThreshold(1.5).build());
    }

    @Test
    @DisplayName("Should enforce threshold ordering")
    void testThresholdOrdering() {
        // autoMerge must be >= synonym
        assertThrows(IllegalArgumentException.class, () ->
                ResolutionOptions.builder()
                        .autoMergeThreshold(0.70)
                        .synonymThreshold(0.80)
                        .reviewThreshold(0.60)
                        .build());

        // synonym must be >= review
        assertThrows(IllegalArgumentException.class, () ->
                ResolutionOptions.builder()
                        .autoMergeThreshold(0.90)
                        .synonymThreshold(0.50)
                        .reviewThreshold(0.60)
                        .build());
    }

    @Test
    @DisplayName("Should allow custom similarity weights")
    void testCustomSimilarityWeights() {
        SimilarityWeights weights = SimilarityWeights.tokenFocused();
        ResolutionOptions options = ResolutionOptions.builder()
                .similarityWeights(weights)
                .build();

        assertEquals(weights, options.getSimilarityWeights());
    }

    @Test
    @DisplayName("Should allow custom source system")
    void testSourceSystem() {
        ResolutionOptions options = ResolutionOptions.builder()
                .sourceSystem("CRM_IMPORT")
                .build();

        assertEquals("CRM_IMPORT", options.getSourceSystem());
    }
}
