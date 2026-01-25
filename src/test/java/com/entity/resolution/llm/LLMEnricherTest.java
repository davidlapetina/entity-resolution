package com.entity.resolution.llm;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LLMEnricherTest {

    private LLMEnricher noOpEnricher;

    @BeforeEach
    void setUp() {
        noOpEnricher = new LLMEnricher(new NoOpLLMProvider());
    }

    @Test
    @DisplayName("NoOp provider should not be available")
    void testNoOpNotAvailable() {
        assertFalse(noOpEnricher.isAvailable());
        assertEquals("NoOp", noOpEnricher.getProviderName());
    }

    @Test
    @DisplayName("NoOp enrichment should return review decision")
    void testNoOpEnrichment() {
        MatchResult result = noOpEnricher.enrich(
                "Big Blue", "IBM Corporation",
                EntityType.COMPANY, "entity-123"
        );

        assertEquals(MatchDecision.REVIEW, result.decision());
        assertEquals(0.0, result.score());
        assertTrue(result.reasoning().contains("manual review"));
    }

    @Test
    @DisplayName("Should use configured confidence threshold")
    void testConfidenceThreshold() {
        LLMEnricher customThreshold = new LLMEnricher(new NoOpLLMProvider(), 0.90);
        assertEquals(0.90, customThreshold.getConfidenceThreshold());
    }

    @Test
    @DisplayName("Should reject invalid confidence threshold")
    void testInvalidThreshold() {
        assertThrows(IllegalArgumentException.class, () ->
                new LLMEnricher(new NoOpLLMProvider(), -0.1));

        assertThrows(IllegalArgumentException.class, () ->
                new LLMEnricher(new NoOpLLMProvider(), 1.5));
    }

    @Test
    @DisplayName("Mock provider should work correctly")
    void testMockProvider() {
        // Create a mock provider that returns a positive match
        LLMProvider mockProvider = new LLMProvider() {
            @Override
            public LLMEnrichmentResponse enrich(LLMEnrichmentRequest request) {
                return LLMEnrichmentResponse.sameEntity(
                        0.90,
                        "Both refer to International Business Machines",
                        List.of("Big Blue", "Blue Giant")
                );
            }

            @Override
            public String getProviderName() {
                return "MockProvider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        LLMEnricher enricher = new LLMEnricher(mockProvider);

        assertTrue(enricher.isAvailable());

        MatchResult result = enricher.enrich(
                "Big Blue", "IBM",
                EntityType.COMPANY, "entity-123"
        );

        // High confidence same entity should result in SYNONYM_ONLY (not AUTO_MERGE per PRD 7.2)
        assertEquals(MatchDecision.SYNONYM_ONLY, result.decision());
        assertEquals(0.90, result.score());
    }

    @Test
    @DisplayName("Low confidence same entity should require review")
    void testLowConfidenceReview() {
        LLMProvider lowConfidenceProvider = new LLMProvider() {
            @Override
            public LLMEnrichmentResponse enrich(LLMEnrichmentRequest request) {
                return LLMEnrichmentResponse.sameEntity(
                        0.70, // Below threshold
                        "Possibly the same entity",
                        List.of()
                );
            }

            @Override
            public String getProviderName() {
                return "LowConfidenceProvider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        LLMEnricher enricher = new LLMEnricher(lowConfidenceProvider);
        MatchResult result = enricher.enrich("A", "B", EntityType.COMPANY, "entity-123");

        // Low confidence should trigger review
        assertEquals(MatchDecision.REVIEW, result.decision());
    }

    @Test
    @DisplayName("Different entities should return NO_MATCH")
    void testDifferentEntities() {
        LLMProvider differentProvider = new LLMProvider() {
            @Override
            public LLMEnrichmentResponse enrich(LLMEnrichmentRequest request) {
                return LLMEnrichmentResponse.differentEntities(
                        0.95,
                        "These are clearly different companies"
                );
            }

            @Override
            public String getProviderName() {
                return "DifferentProvider";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        LLMEnricher enricher = new LLMEnricher(differentProvider);
        MatchResult result = enricher.enrich("Apple", "Microsoft", EntityType.COMPANY, "entity-123");

        assertEquals(MatchDecision.NO_MATCH, result.decision());
    }
}
