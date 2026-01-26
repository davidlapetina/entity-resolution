package com.entity.resolution.api;

import com.entity.resolution.similarity.SimilarityWeights;

/**
 * Options for entity resolution operations.
 * Configures thresholds, LLM usage, and similarity weights.
 */
public class ResolutionOptions {

    private static final double DEFAULT_AUTO_MERGE_THRESHOLD = 0.92;
    private static final double DEFAULT_SYNONYM_THRESHOLD = 0.80;
    private static final double DEFAULT_REVIEW_THRESHOLD = 0.60;
    private static final double DEFAULT_LLM_CONFIDENCE_THRESHOLD = 0.85;
    private static final int DEFAULT_MAX_BATCH_SIZE = 10_000;
    private static final int DEFAULT_BATCH_COMMIT_CHUNK_SIZE = 1_000;

    private final boolean useLLM;
    private final double autoMergeThreshold;
    private final double synonymThreshold;
    private final double reviewThreshold;
    private final double llmConfidenceThreshold;
    private final SimilarityWeights similarityWeights;
    private final String sourceSystem;
    private final boolean autoMergeEnabled;
    private final int maxBatchSize;
    private final int batchCommitChunkSize;

    private ResolutionOptions(Builder builder) {
        this.useLLM = builder.useLLM;
        this.autoMergeThreshold = builder.autoMergeThreshold;
        this.synonymThreshold = builder.synonymThreshold;
        this.reviewThreshold = builder.reviewThreshold;
        this.llmConfidenceThreshold = builder.llmConfidenceThreshold;
        this.similarityWeights = builder.similarityWeights;
        this.sourceSystem = builder.sourceSystem;
        this.autoMergeEnabled = builder.autoMergeEnabled;
        this.maxBatchSize = builder.maxBatchSize;
        this.batchCommitChunkSize = builder.batchCommitChunkSize;
    }

    public boolean isUseLLM() {
        return useLLM;
    }

    public double getAutoMergeThreshold() {
        return autoMergeThreshold;
    }

    public double getSynonymThreshold() {
        return synonymThreshold;
    }

    public double getReviewThreshold() {
        return reviewThreshold;
    }

    public double getLlmConfidenceThreshold() {
        return llmConfidenceThreshold;
    }

    public SimilarityWeights getSimilarityWeights() {
        return similarityWeights;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public boolean isAutoMergeEnabled() {
        return autoMergeEnabled;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public int getBatchCommitChunkSize() {
        return batchCommitChunkSize;
    }

    /**
     * Creates default options.
     */
    public static ResolutionOptions defaults() {
        return builder().build();
    }

    /**
     * Creates options with LLM enrichment enabled.
     */
    public static ResolutionOptions withLLM() {
        return builder().useLLM(true).build();
    }

    /**
     * Creates conservative options (higher thresholds, no auto-merge).
     */
    public static ResolutionOptions conservative() {
        return builder()
                .autoMergeThreshold(0.98)
                .synonymThreshold(0.90)
                .reviewThreshold(0.70)
                .autoMergeEnabled(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean useLLM = false;
        private double autoMergeThreshold = DEFAULT_AUTO_MERGE_THRESHOLD;
        private double synonymThreshold = DEFAULT_SYNONYM_THRESHOLD;
        private double reviewThreshold = DEFAULT_REVIEW_THRESHOLD;
        private double llmConfidenceThreshold = DEFAULT_LLM_CONFIDENCE_THRESHOLD;
        private SimilarityWeights similarityWeights = SimilarityWeights.defaultWeights();
        private String sourceSystem = "SYSTEM";
        private boolean autoMergeEnabled = true;
        private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
        private int batchCommitChunkSize = DEFAULT_BATCH_COMMIT_CHUNK_SIZE;

        public Builder useLLM(boolean useLLM) {
            this.useLLM = useLLM;
            return this;
        }

        public Builder autoMergeThreshold(double autoMergeThreshold) {
            validateThreshold(autoMergeThreshold, "autoMergeThreshold");
            this.autoMergeThreshold = autoMergeThreshold;
            return this;
        }

        public Builder synonymThreshold(double synonymThreshold) {
            validateThreshold(synonymThreshold, "synonymThreshold");
            this.synonymThreshold = synonymThreshold;
            return this;
        }

        public Builder reviewThreshold(double reviewThreshold) {
            validateThreshold(reviewThreshold, "reviewThreshold");
            this.reviewThreshold = reviewThreshold;
            return this;
        }

        public Builder llmConfidenceThreshold(double llmConfidenceThreshold) {
            validateThreshold(llmConfidenceThreshold, "llmConfidenceThreshold");
            this.llmConfidenceThreshold = llmConfidenceThreshold;
            return this;
        }

        public Builder similarityWeights(SimilarityWeights similarityWeights) {
            this.similarityWeights = similarityWeights;
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        public Builder autoMergeEnabled(boolean autoMergeEnabled) {
            this.autoMergeEnabled = autoMergeEnabled;
            return this;
        }

        public Builder maxBatchSize(int maxBatchSize) {
            if (maxBatchSize <= 0) {
                throw new IllegalArgumentException("maxBatchSize must be positive");
            }
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        public Builder batchCommitChunkSize(int batchCommitChunkSize) {
            if (batchCommitChunkSize <= 0) {
                throw new IllegalArgumentException("batchCommitChunkSize must be positive");
            }
            this.batchCommitChunkSize = batchCommitChunkSize;
            return this;
        }

        public ResolutionOptions build() {
            // Validate threshold ordering
            if (autoMergeThreshold < synonymThreshold) {
                throw new IllegalArgumentException(
                        "autoMergeThreshold must be >= synonymThreshold");
            }
            if (synonymThreshold < reviewThreshold) {
                throw new IllegalArgumentException(
                        "synonymThreshold must be >= reviewThreshold");
            }
            return new ResolutionOptions(this);
        }

        private void validateThreshold(double value, String name) {
            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
            }
        }
    }

    @Override
    public String toString() {
        return "ResolutionOptions{" +
                "useLLM=" + useLLM +
                ", autoMergeThreshold=" + autoMergeThreshold +
                ", synonymThreshold=" + synonymThreshold +
                ", reviewThreshold=" + reviewThreshold +
                ", llmConfidenceThreshold=" + llmConfidenceThreshold +
                ", autoMergeEnabled=" + autoMergeEnabled +
                ", sourceSystem='" + sourceSystem + '\'' +
                ", maxBatchSize=" + maxBatchSize +
                ", batchCommitChunkSize=" + batchCommitChunkSize +
                '}';
    }
}
