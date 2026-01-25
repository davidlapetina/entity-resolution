package com.entity.resolution.llm;

import java.util.List;
import java.util.Objects;

/**
 * Response from LLM enrichment.
 * Contains confidence assessment and suggested synonyms.
 * Per PRD 7.2, LLMs never perform direct merges but can suggest synonyms.
 */
public record LLMEnrichmentResponse(
        double confidence,
        boolean areSameEntity,
        String reasoning,
        List<String> suggestedSynonyms,
        List<String> relatedEntities
) {
    public LLMEnrichmentResponse {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
        Objects.requireNonNull(reasoning, "reasoning is required");
        suggestedSynonyms = suggestedSynonyms != null ? List.copyOf(suggestedSynonyms) : List.of();
        relatedEntities = relatedEntities != null ? List.copyOf(relatedEntities) : List.of();
    }

    /**
     * Creates a response indicating the entities are the same.
     */
    public static LLMEnrichmentResponse sameEntity(double confidence, String reasoning,
                                                    List<String> suggestedSynonyms) {
        return new LLMEnrichmentResponse(confidence, true, reasoning, suggestedSynonyms, List.of());
    }

    /**
     * Creates a response indicating the entities are different.
     */
    public static LLMEnrichmentResponse differentEntities(double confidence, String reasoning) {
        return new LLMEnrichmentResponse(confidence, false, reasoning, List.of(), List.of());
    }

    /**
     * Creates an uncertain response.
     */
    public static LLMEnrichmentResponse uncertain(String reasoning) {
        return new LLMEnrichmentResponse(0.5, false, reasoning, List.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double confidence;
        private boolean areSameEntity;
        private String reasoning;
        private List<String> suggestedSynonyms;
        private List<String> relatedEntities;

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder areSameEntity(boolean areSameEntity) {
            this.areSameEntity = areSameEntity;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder suggestedSynonyms(List<String> suggestedSynonyms) {
            this.suggestedSynonyms = suggestedSynonyms;
            return this;
        }

        public Builder relatedEntities(List<String> relatedEntities) {
            this.relatedEntities = relatedEntities;
            return this;
        }

        public LLMEnrichmentResponse build() {
            return new LLMEnrichmentResponse(confidence, areSameEntity, reasoning,
                    suggestedSynonyms, relatedEntities);
        }
    }
}
