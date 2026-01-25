package com.entity.resolution.llm;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for LLM integration.
 * Implementations provide semantic entity comparison capabilities.
 *
 * Per PRD section 7, LLMs are used for:
 * - Semantic similarity assessment
 * - Synonym suggestion
 * - Entity disambiguation
 *
 * Per PRD 7.2, LLMs NEVER perform direct merges.
 */
public interface LLMProvider {

    /**
     * Performs semantic enrichment to determine if two entity names refer to the same entity.
     *
     * @param request the enrichment request with entity names and context
     * @return enrichment response with confidence and suggestions
     */
    LLMEnrichmentResponse enrich(LLMEnrichmentRequest request);

    /**
     * Performs semantic enrichment asynchronously.
     *
     * @param request the enrichment request
     * @return future containing the enrichment response
     */
    default CompletableFuture<LLMEnrichmentResponse> enrichAsync(LLMEnrichmentRequest request) {
        return CompletableFuture.supplyAsync(() -> enrich(request));
    }

    /**
     * Returns the name/identifier of this LLM provider.
     */
    String getProviderName();

    /**
     * Checks if the provider is available and configured.
     */
    boolean isAvailable();

    /**
     * Gets the default confidence threshold for this provider.
     * Per PRD, default is 0.85.
     */
    default double getDefaultConfidenceThreshold() {
        return 0.85;
    }
}
