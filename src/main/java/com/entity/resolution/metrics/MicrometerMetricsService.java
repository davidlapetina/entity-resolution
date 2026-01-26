package com.entity.resolution.metrics;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Micrometer-based implementation of {@link MetricsService}.
 * Requires {@code micrometer-core} on the classpath (optional dependency).
 *
 * <p>Recorded metrics:</p>
 * <ul>
 *   <li>{@code entity.resolution.duration} — Timer (tags: entityType, decision)</li>
 *   <li>{@code entity.created} — Counter (tag: entityType)</li>
 *   <li>{@code entity.merged} — Counter (tag: entityType)</li>
 *   <li>{@code entity.matched.synonym} — Counter (tag: entityType)</li>
 *   <li>{@code entity.similarity.score} — DistributionSummary</li>
 *   <li>{@code entity.batch.size} — DistributionSummary</li>
 *   <li>{@code entity.cache.hit} — Counter</li>
 *   <li>{@code entity.cache.miss} — Counter</li>
 * </ul>
 */
public class MicrometerMetricsService implements MetricsService {

    private final MeterRegistry registry;
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final DistributionSummary similarityScoreSummary;
    private final DistributionSummary batchSizeSummary;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public MicrometerMetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.similarityScoreSummary = DistributionSummary.builder("entity.similarity.score")
                .description("Distribution of similarity scores during matching")
                .register(registry);
        this.batchSizeSummary = DistributionSummary.builder("entity.batch.size")
                .description("Distribution of batch sizes on commit")
                .register(registry);
        this.cacheHitCounter = Counter.builder("entity.cache.hit")
                .description("Number of resolution cache hits")
                .register(registry);
        this.cacheMissCounter = Counter.builder("entity.cache.miss")
                .description("Number of resolution cache misses")
                .register(registry);
    }

    @Override
    public void recordResolutionDuration(EntityType type, MatchDecision decision, Duration duration) {
        String key = type.name() + ":" + decision.name();
        Timer timer = timerCache.computeIfAbsent(key, k ->
                Timer.builder("entity.resolution.duration")
                        .description("Duration of entity resolution operations")
                        .tag("entityType", type.name())
                        .tag("decision", decision.name())
                        .register(registry));
        timer.record(duration);
    }

    @Override
    public void incrementEntityCreated(EntityType type) {
        String key = "created:" + type.name();
        Counter counter = counterCache.computeIfAbsent(key, k ->
                Counter.builder("entity.created")
                        .description("Number of new entities created")
                        .tag("entityType", type.name())
                        .register(registry));
        counter.increment();
    }

    @Override
    public void incrementEntityMerged(EntityType type) {
        String key = "merged:" + type.name();
        Counter counter = counterCache.computeIfAbsent(key, k ->
                Counter.builder("entity.merged")
                        .description("Number of entities merged")
                        .tag("entityType", type.name())
                        .register(registry));
        counter.increment();
    }

    @Override
    public void incrementSynonymMatched(EntityType type) {
        String key = "synonym:" + type.name();
        Counter counter = counterCache.computeIfAbsent(key, k ->
                Counter.builder("entity.matched.synonym")
                        .description("Number of synonym matches")
                        .tag("entityType", type.name())
                        .register(registry));
        counter.increment();
    }

    @Override
    public void recordSimilarityScore(double score) {
        similarityScoreSummary.record(score);
    }

    @Override
    public void recordBatchSize(int size) {
        batchSizeSummary.record(size);
    }

    @Override
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }

    @Override
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }
}
