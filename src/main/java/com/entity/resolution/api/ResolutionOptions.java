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
    private static final long DEFAULT_MAX_BATCH_MEMORY_BYTES = 100L * 1_000_000; // 100MB
    private static final int DEFAULT_CACHE_MAX_SIZE = 10_000;
    private static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    private static final long DEFAULT_LOCK_TIMEOUT_MS = 5_000;
    private static final long DEFAULT_ASYNC_TIMEOUT_MS = 30_000;
    private static final double DEFAULT_CONFIDENCE_DECAY_LAMBDA = 0.001;
    private static final double DEFAULT_REINFORCEMENT_CAP = 0.15;

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
    private final long maxBatchMemoryBytes;

    // Scalability options
    private final boolean cachingEnabled;
    private final int cacheMaxSize;
    private final int cacheTtlSeconds;
    private final long lockTimeoutMs;
    private final long asyncTimeoutMs;

    // Confidence decay options (v1.1)
    private final double confidenceDecayLambda;
    private final double reinforcementCap;

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
        this.maxBatchMemoryBytes = builder.maxBatchMemoryBytes;
        this.cachingEnabled = builder.cachingEnabled;
        this.cacheMaxSize = builder.cacheMaxSize;
        this.cacheTtlSeconds = builder.cacheTtlSeconds;
        this.lockTimeoutMs = builder.lockTimeoutMs;
        this.asyncTimeoutMs = builder.asyncTimeoutMs;
        this.confidenceDecayLambda = builder.confidenceDecayLambda;
        this.reinforcementCap = builder.reinforcementCap;
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

    public long getMaxBatchMemoryBytes() {
        return maxBatchMemoryBytes;
    }

    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public long getLockTimeoutMs() {
        return lockTimeoutMs;
    }

    public long getAsyncTimeoutMs() {
        return asyncTimeoutMs;
    }

    public double getConfidenceDecayLambda() {
        return confidenceDecayLambda;
    }

    public double getReinforcementCap() {
        return reinforcementCap;
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
        private long maxBatchMemoryBytes = DEFAULT_MAX_BATCH_MEMORY_BYTES;
        private boolean cachingEnabled = false;
        private int cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;
        private int cacheTtlSeconds = DEFAULT_CACHE_TTL_SECONDS;
        private long lockTimeoutMs = DEFAULT_LOCK_TIMEOUT_MS;
        private long asyncTimeoutMs = DEFAULT_ASYNC_TIMEOUT_MS;
        private double confidenceDecayLambda = DEFAULT_CONFIDENCE_DECAY_LAMBDA;
        private double reinforcementCap = DEFAULT_REINFORCEMENT_CAP;

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

        public Builder maxBatchMemoryBytes(long maxBatchMemoryBytes) {
            if (maxBatchMemoryBytes <= 0) {
                throw new IllegalArgumentException("maxBatchMemoryBytes must be positive");
            }
            this.maxBatchMemoryBytes = maxBatchMemoryBytes;
            return this;
        }

        public Builder cachingEnabled(boolean cachingEnabled) {
            this.cachingEnabled = cachingEnabled;
            return this;
        }

        public Builder cacheMaxSize(int cacheMaxSize) {
            if (cacheMaxSize <= 0) {
                throw new IllegalArgumentException("cacheMaxSize must be positive");
            }
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        public Builder cacheTtlSeconds(int cacheTtlSeconds) {
            if (cacheTtlSeconds <= 0) {
                throw new IllegalArgumentException("cacheTtlSeconds must be positive");
            }
            this.cacheTtlSeconds = cacheTtlSeconds;
            return this;
        }

        public Builder lockTimeoutMs(long lockTimeoutMs) {
            if (lockTimeoutMs <= 0) {
                throw new IllegalArgumentException("lockTimeoutMs must be positive");
            }
            this.lockTimeoutMs = lockTimeoutMs;
            return this;
        }

        public Builder asyncTimeoutMs(long asyncTimeoutMs) {
            if (asyncTimeoutMs <= 0) {
                throw new IllegalArgumentException("asyncTimeoutMs must be positive");
            }
            this.asyncTimeoutMs = asyncTimeoutMs;
            return this;
        }

        public Builder confidenceDecayLambda(double confidenceDecayLambda) {
            if (confidenceDecayLambda < 0.0) {
                throw new IllegalArgumentException("confidenceDecayLambda must be non-negative");
            }
            this.confidenceDecayLambda = confidenceDecayLambda;
            return this;
        }

        public Builder reinforcementCap(double reinforcementCap) {
            if (reinforcementCap < 0.0 || reinforcementCap > 1.0) {
                throw new IllegalArgumentException("reinforcementCap must be between 0.0 and 1.0");
            }
            this.reinforcementCap = reinforcementCap;
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
                ", maxBatchMemoryBytes=" + maxBatchMemoryBytes +
                ", cachingEnabled=" + cachingEnabled +
                ", cacheMaxSize=" + cacheMaxSize +
                ", cacheTtlSeconds=" + cacheTtlSeconds +
                ", lockTimeoutMs=" + lockTimeoutMs +
                ", asyncTimeoutMs=" + asyncTimeoutMs +
                ", confidenceDecayLambda=" + confidenceDecayLambda +
                ", reinforcementCap=" + reinforcementCap +
                '}';
    }
}
