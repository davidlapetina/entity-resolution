package com.entity.resolution.metrics;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;

import java.time.Duration;

/**
 * Interface for recording entity resolution metrics.
 * Implementations can integrate with Micrometer, Prometheus, or other metrics systems.
 * The default {@link NoOpMetricsService} does nothing, ensuring the library works
 * without any metrics dependencies on the classpath.
 */
public interface MetricsService {

    void recordResolutionDuration(EntityType type, MatchDecision decision, Duration duration);

    void incrementEntityCreated(EntityType type);

    void incrementEntityMerged(EntityType type);

    void incrementSynonymMatched(EntityType type);

    void recordSimilarityScore(double score);

    void recordBatchSize(int size);

    void recordCacheHit();

    void recordCacheMiss();
}
