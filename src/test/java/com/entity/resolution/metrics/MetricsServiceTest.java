package com.entity.resolution.metrics;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetricsService Tests")
class MetricsServiceTest {

    @Nested
    @DisplayName("NoOpMetricsService")
    class NoOpTests {

        @Test
        @DisplayName("All methods should be callable without error")
        void allMethodsCallableWithoutError() {
            NoOpMetricsService noOp = new NoOpMetricsService();

            assertDoesNotThrow(() -> {
                noOp.recordResolutionDuration(EntityType.COMPANY, MatchDecision.AUTO_MERGE, Duration.ofMillis(100));
                noOp.incrementEntityCreated(EntityType.COMPANY);
                noOp.incrementEntityMerged(EntityType.PERSON);
                noOp.incrementSynonymMatched(EntityType.ORGANIZATION);
                noOp.recordSimilarityScore(0.85);
                noOp.recordBatchSize(50);
                noOp.recordCacheHit();
                noOp.recordCacheMiss();
            });
        }
    }

    @Nested
    @DisplayName("MicrometerMetricsService")
    class MicrometerTests {

        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final MicrometerMetricsService metrics = new MicrometerMetricsService(registry);

        @Test
        @DisplayName("Should record resolution duration as timer")
        void recordResolutionDuration() {
            metrics.recordResolutionDuration(EntityType.COMPANY, MatchDecision.AUTO_MERGE, Duration.ofMillis(150));
            metrics.recordResolutionDuration(EntityType.COMPANY, MatchDecision.AUTO_MERGE, Duration.ofMillis(250));

            Timer timer = registry.find("entity.resolution.duration")
                    .tag("entityType", "COMPANY")
                    .tag("decision", "AUTO_MERGE")
                    .timer();

            assertNotNull(timer);
            assertEquals(2, timer.count());
        }

        @Test
        @DisplayName("Should increment entity created counter")
        void incrementEntityCreated() {
            metrics.incrementEntityCreated(EntityType.COMPANY);
            metrics.incrementEntityCreated(EntityType.COMPANY);
            metrics.incrementEntityCreated(EntityType.PERSON);

            Counter companyCounter = registry.find("entity.created")
                    .tag("entityType", "COMPANY")
                    .counter();
            Counter personCounter = registry.find("entity.created")
                    .tag("entityType", "PERSON")
                    .counter();

            assertNotNull(companyCounter);
            assertEquals(2.0, companyCounter.count());
            assertNotNull(personCounter);
            assertEquals(1.0, personCounter.count());
        }

        @Test
        @DisplayName("Should increment entity merged counter")
        void incrementEntityMerged() {
            metrics.incrementEntityMerged(EntityType.COMPANY);

            Counter counter = registry.find("entity.merged")
                    .tag("entityType", "COMPANY")
                    .counter();

            assertNotNull(counter);
            assertEquals(1.0, counter.count());
        }

        @Test
        @DisplayName("Should increment synonym matched counter")
        void incrementSynonymMatched() {
            metrics.incrementSynonymMatched(EntityType.ORGANIZATION);

            Counter counter = registry.find("entity.matched.synonym")
                    .tag("entityType", "ORGANIZATION")
                    .counter();

            assertNotNull(counter);
            assertEquals(1.0, counter.count());
        }

        @Test
        @DisplayName("Should record similarity score in histogram")
        void recordSimilarityScore() {
            metrics.recordSimilarityScore(0.85);
            metrics.recordSimilarityScore(0.92);
            metrics.recordSimilarityScore(0.45);

            DistributionSummary summary = registry.find("entity.similarity.score")
                    .summary();

            assertNotNull(summary);
            assertEquals(3, summary.count());
        }

        @Test
        @DisplayName("Should record batch size")
        void recordBatchSize() {
            metrics.recordBatchSize(100);
            metrics.recordBatchSize(50);

            DistributionSummary summary = registry.find("entity.batch.size")
                    .summary();

            assertNotNull(summary);
            assertEquals(2, summary.count());
        }

        @Test
        @DisplayName("Should record cache hits and misses")
        void recordCacheHitAndMiss() {
            metrics.recordCacheHit();
            metrics.recordCacheHit();
            metrics.recordCacheMiss();

            Counter hitCounter = registry.find("entity.cache.hit").counter();
            Counter missCounter = registry.find("entity.cache.miss").counter();

            assertNotNull(hitCounter);
            assertEquals(2.0, hitCounter.count());
            assertNotNull(missCounter);
            assertEquals(1.0, missCounter.count());
        }

        @Test
        @DisplayName("Should create separate timers for different entity type and decision combinations")
        void separateTimersByTags() {
            metrics.recordResolutionDuration(EntityType.COMPANY, MatchDecision.AUTO_MERGE, Duration.ofMillis(100));
            metrics.recordResolutionDuration(EntityType.PERSON, MatchDecision.NO_MATCH, Duration.ofMillis(200));

            Timer companyTimer = registry.find("entity.resolution.duration")
                    .tag("entityType", "COMPANY")
                    .tag("decision", "AUTO_MERGE")
                    .timer();
            Timer personTimer = registry.find("entity.resolution.duration")
                    .tag("entityType", "PERSON")
                    .tag("decision", "NO_MATCH")
                    .timer();

            assertNotNull(companyTimer);
            assertNotNull(personTimer);
            assertEquals(1, companyTimer.count());
            assertEquals(1, personTimer.count());
        }
    }
}
