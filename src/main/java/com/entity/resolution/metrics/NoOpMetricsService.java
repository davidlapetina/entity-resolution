package com.entity.resolution.metrics;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;

import java.time.Duration;

/**
 * No-op implementation of {@link MetricsService}.
 * All methods are empty, ensuring the library works without any metrics dependencies.
 */
public class NoOpMetricsService implements MetricsService {

    @Override
    public void recordResolutionDuration(EntityType type, MatchDecision decision, Duration duration) {
    }

    @Override
    public void incrementEntityCreated(EntityType type) {
    }

    @Override
    public void incrementEntityMerged(EntityType type) {
    }

    @Override
    public void incrementSynonymMatched(EntityType type) {
    }

    @Override
    public void recordSimilarityScore(double score) {
    }

    @Override
    public void recordBatchSize(int size) {
    }

    @Override
    public void recordCacheHit() {
    }

    @Override
    public void recordCacheMiss() {
    }
}
