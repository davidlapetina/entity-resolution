package com.entity.resolution.llm;

import com.entity.resolution.core.model.EntityType;

import java.util.List;
import java.util.Objects;

/**
 * Request for LLM enrichment of entity matching.
 * Contains entity names for semantic comparison.
 */
public record LLMEnrichmentRequest(
        String entityName1,
        String entityName2,
        EntityType entityType,
        List<String> additionalContext
) {
    public LLMEnrichmentRequest {
        Objects.requireNonNull(entityName1, "entityName1 is required");
        Objects.requireNonNull(entityName2, "entityName2 is required");
        Objects.requireNonNull(entityType, "entityType is required");
        additionalContext = additionalContext != null ? List.copyOf(additionalContext) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String entityName1;
        private String entityName2;
        private EntityType entityType;
        private List<String> additionalContext;

        public Builder entityName1(String entityName1) {
            this.entityName1 = entityName1;
            return this;
        }

        public Builder entityName2(String entityName2) {
            this.entityName2 = entityName2;
            return this;
        }

        public Builder entityType(EntityType entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder additionalContext(List<String> additionalContext) {
            this.additionalContext = additionalContext;
            return this;
        }

        public LLMEnrichmentRequest build() {
            return new LLMEnrichmentRequest(entityName1, entityName2, entityType, additionalContext);
        }
    }
}
