package com.entity.resolution.llm;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.MatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates LLM calls for entity enrichment.
 * Enforces confidence thresholds as per PRD section 7.
 *
 * Important: Per PRD 7.2, LLMs never perform direct merges.
 * LLM results are used to:
 * - Suggest synonyms (which can be auto-approved above threshold)
 * - Escalate to manual review
 */
public class LLMEnricher {
    private static final Logger log = LoggerFactory.getLogger(LLMEnricher.class);

    private static final double DEFAULT_LLM_CONFIDENCE_THRESHOLD = 0.85;

    private final LLMProvider provider;
    private final double confidenceThreshold;

    public LLMEnricher(LLMProvider provider) {
        this(provider, DEFAULT_LLM_CONFIDENCE_THRESHOLD);
    }

    public LLMEnricher(LLMProvider provider, double confidenceThreshold) {
        this.provider = Objects.requireNonNull(provider, "provider is required");
        if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be between 0.0 and 1.0");
        }
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * Enriches a match result using LLM semantic analysis.
     *
     * @param entityName1 first entity name
     * @param entityName2 second entity name
     * @param entityType  type of entities being compared
     * @param entityId    ID of the candidate entity
     * @return enriched match result with LLM-derived confidence
     */
    public MatchResult enrich(String entityName1, String entityName2,
                               EntityType entityType, String entityId) {
        return enrich(entityName1, entityName2, entityType, entityId, List.of());
    }

    /**
     * Enriches a match result using LLM semantic analysis with additional context.
     */
    public MatchResult enrich(String entityName1, String entityName2,
                               EntityType entityType, String entityId,
                               List<String> additionalContext) {
        if (!provider.isAvailable()) {
            log.warn("LLM provider not available, returning review decision");
            return new MatchResult(0.0, MatchDecision.REVIEW, entityId, entityName2,
                    "LLM enrichment unavailable - manual review required");
        }

        LLMEnrichmentRequest request = LLMEnrichmentRequest.builder()
                .entityName1(entityName1)
                .entityName2(entityName2)
                .entityType(entityType)
                .additionalContext(additionalContext)
                .build();

        log.info("Requesting LLM enrichment for '{}' vs '{}'", entityName1, entityName2);

        LLMEnrichmentResponse response = provider.enrich(request);

        log.info("LLM response: confidence={}, areSameEntity={}, reasoning={}",
                response.confidence(), response.areSameEntity(), response.reasoning());

        return convertToMatchResult(response, entityId, entityName2);
    }

    /**
     * Converts LLM response to a match result with appropriate decision.
     * Per PRD 7.2, LLM results never directly trigger AUTO_MERGE.
     */
    private MatchResult convertToMatchResult(LLMEnrichmentResponse response,
                                              String entityId, String matchedName) {
        double confidence = response.confidence();
        MatchDecision decision;

        if (response.areSameEntity() && confidence >= confidenceThreshold) {
            // High confidence same entity - suggest synonym creation, but require review for merge
            decision = MatchDecision.SYNONYM_ONLY;
            log.debug("LLM suggests synonym creation (confidence {} >= threshold {})",
                    confidence, confidenceThreshold);
        } else if (response.areSameEntity()) {
            // Lower confidence same entity - needs manual review
            decision = MatchDecision.REVIEW;
            log.debug("LLM suggests review (confidence {} < threshold {})",
                    confidence, confidenceThreshold);
        } else {
            // Different entities
            decision = MatchDecision.NO_MATCH;
            log.debug("LLM determined entities are different");
        }

        return new MatchResult(confidence, decision, entityId, matchedName, response.reasoning());
    }

    /**
     * Checks if LLM enrichment is available.
     */
    public boolean isAvailable() {
        return provider.isAvailable();
    }

    /**
     * Gets the configured confidence threshold.
     */
    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    /**
     * Gets the provider name.
     */
    public String getProviderName() {
        return provider.getProviderName();
    }

    /**
     * Gets suggested synonyms from an LLM response if the provider returned any.
     */
    public List<String> getSuggestedSynonyms(String entityName1, String entityName2,
                                              EntityType entityType) {
        if (!provider.isAvailable()) {
            return List.of();
        }

        LLMEnrichmentRequest request = LLMEnrichmentRequest.builder()
                .entityName1(entityName1)
                .entityName2(entityName2)
                .entityType(entityType)
                .build();

        LLMEnrichmentResponse response = provider.enrich(request);
        return response.suggestedSynonyms();
    }
}
