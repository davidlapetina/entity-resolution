package com.entity.resolution.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-operation LLM provider for when LLM integration is disabled or unavailable.
 * Returns uncertain responses that will trigger manual review.
 */
public class NoOpLLMProvider implements LLMProvider {
    private static final Logger log = LoggerFactory.getLogger(NoOpLLMProvider.class);

    @Override
    public LLMEnrichmentResponse enrich(LLMEnrichmentRequest request) {
        log.debug("NoOp LLM provider called for entities: '{}' and '{}'",
                request.entityName1(), request.entityName2());

        return LLMEnrichmentResponse.uncertain(
                "LLM enrichment not available - NoOp provider in use. Manual review recommended."
        );
    }

    @Override
    public String getProviderName() {
        return "NoOp";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
