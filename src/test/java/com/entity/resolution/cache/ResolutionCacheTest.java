package com.entity.resolution.cache;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionCacheTest {

    private EntityResolutionResult createTestResult(String entityId, String name, EntityType type) {
        Entity entity = Entity.builder()
                .id(entityId)
                .canonicalName(name)
                .normalizedName(name.toLowerCase())
                .type(type)
                .build();
        return EntityResolutionResult.builder()
                .canonicalEntity(entity)
                .entityReference(EntityReference.of(entityId, type))
                .decision(MatchDecision.NO_MATCH)
                .confidence(1.0)
                .isNewEntity(true)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .inputName(name)
                .matchedName(name)
                .reasoning("test")
                .build();
    }

    @Nested
    @DisplayName("NoOpResolutionCache")
    class NoOpTests {

        @Test
        @DisplayName("Should always return empty on get")
        void testGetAlwaysEmpty() {
            NoOpResolutionCache cache = new NoOpResolutionCache();
            cache.put("test", EntityType.COMPANY, createTestResult("id1", "Test", EntityType.COMPANY));
            assertTrue(cache.get("test", EntityType.COMPANY).isEmpty());
        }

        @Test
        @DisplayName("Should return empty stats")
        void testEmptyStats() {
            NoOpResolutionCache cache = new NoOpResolutionCache();
            CacheStats stats = cache.getStats();
            assertEquals(0, stats.hitCount());
            assertEquals(0, stats.missCount());
            assertEquals(0, stats.size());
        }
    }

    @Nested
    @DisplayName("CaffeineResolutionCache")
    class CaffeineTests {

        @Test
        @DisplayName("Should cache and retrieve results")
        void testPutAndGet() {
            CaffeineResolutionCache cache = new CaffeineResolutionCache(CacheConfig.defaults());
            EntityResolutionResult result = createTestResult("id1", "Acme Corp", EntityType.COMPANY);

            cache.put("acme corp", EntityType.COMPANY, result);
            Optional<EntityResolutionResult> cached = cache.get("acme corp", EntityType.COMPANY);

            assertTrue(cached.isPresent());
            assertEquals("id1", cached.get().getCanonicalEntity().getId());
        }

        @Test
        @DisplayName("Should return empty for cache miss")
        void testCacheMiss() {
            CaffeineResolutionCache cache = new CaffeineResolutionCache(CacheConfig.defaults());
            assertTrue(cache.get("nonexistent", EntityType.COMPANY).isEmpty());
        }

        @Test
        @DisplayName("Should separate by entity type")
        void testSeparateByEntityType() {
            CaffeineResolutionCache cache = new CaffeineResolutionCache(CacheConfig.defaults());
            EntityResolutionResult companyResult = createTestResult("c1", "Smith", EntityType.COMPANY);
            EntityResolutionResult personResult = createTestResult("p1", "Smith", EntityType.PERSON);

            cache.put("smith", EntityType.COMPANY, companyResult);
            cache.put("smith", EntityType.PERSON, personResult);

            assertEquals("c1", cache.get("smith", EntityType.COMPANY).get().getCanonicalEntity().getId());
            assertEquals("p1", cache.get("smith", EntityType.PERSON).get().getCanonicalEntity().getId());
        }

        @Test
        @DisplayName("Should invalidate by entity ID")
        void testInvalidateByEntityId() {
            CaffeineResolutionCache cache = new CaffeineResolutionCache(CacheConfig.defaults());
            cache.put("acme", EntityType.COMPANY, createTestResult("id1", "Acme", EntityType.COMPANY));
            cache.put("beta", EntityType.COMPANY, createTestResult("id2", "Beta", EntityType.COMPANY));

            cache.invalidate("id1");

            assertTrue(cache.get("acme", EntityType.COMPANY).isEmpty());
            assertTrue(cache.get("beta", EntityType.COMPANY).isPresent());
        }

        @Test
        @DisplayName("Should invalidate all entries")
        void testInvalidateAll() {
            CaffeineResolutionCache cache = new CaffeineResolutionCache(CacheConfig.defaults());
            cache.put("acme", EntityType.COMPANY, createTestResult("id1", "Acme", EntityType.COMPANY));
            cache.put("beta", EntityType.COMPANY, createTestResult("id2", "Beta", EntityType.COMPANY));

            cache.invalidateAll();

            assertTrue(cache.get("acme", EntityType.COMPANY).isEmpty());
            assertTrue(cache.get("beta", EntityType.COMPANY).isEmpty());
        }

        @Test
        @DisplayName("Should auto-invalidate on merge event")
        void testMergeListenerInvalidation() {
            CaffeineResolutionCache cache = new CaffeineResolutionCache(CacheConfig.defaults());
            cache.put("acme", EntityType.COMPANY, createTestResult("source-id", "Acme", EntityType.COMPANY));
            cache.put("target-name", EntityType.COMPANY, createTestResult("target-id", "Target", EntityType.COMPANY));

            // Simulate merge event
            cache.onMerge("source-id", "target-id");

            assertTrue(cache.get("acme", EntityType.COMPANY).isEmpty());
            assertTrue(cache.get("target-name", EntityType.COMPANY).isEmpty());
        }

        @Test
        @DisplayName("Should report cache stats")
        void testCacheStats() {
            CaffeineResolutionCache cache = new CaffeineResolutionCache(CacheConfig.defaults());
            cache.put("acme", EntityType.COMPANY, createTestResult("id1", "Acme", EntityType.COMPANY));

            cache.get("acme", EntityType.COMPANY); // hit
            cache.get("missing", EntityType.COMPANY); // miss

            CacheStats stats = cache.getStats();
            assertEquals(1, stats.hitCount());
            assertEquals(1, stats.missCount());
            assertEquals(0.5, stats.hitRate(), 0.01);
        }
    }

    @Nested
    @DisplayName("CacheConfig")
    class CacheConfigTests {

        @Test
        @DisplayName("Should create default config")
        void testDefaults() {
            CacheConfig config = CacheConfig.defaults();
            assertEquals(10_000, config.maxSize());
            assertEquals(300, config.ttlSeconds());
            assertTrue(config.enabled());
        }

        @Test
        @DisplayName("Should create disabled config")
        void testDisabled() {
            CacheConfig config = CacheConfig.disabled();
            assertFalse(config.enabled());
        }

        @Test
        @DisplayName("Should reject invalid maxSize")
        void testInvalidMaxSize() {
            assertThrows(IllegalArgumentException.class, () -> new CacheConfig(0, 300, true));
        }

        @Test
        @DisplayName("Should reject invalid ttlSeconds")
        void testInvalidTtl() {
            assertThrows(IllegalArgumentException.class, () -> new CacheConfig(1000, 0, true));
        }
    }

    @Nested
    @DisplayName("CacheStats")
    class CacheStatsTests {

        @Test
        @DisplayName("Should calculate hit rate")
        void testHitRate() {
            CacheStats stats = new CacheStats(80, 20, 5, 100);
            assertEquals(0.8, stats.hitRate(), 0.01);
        }

        @Test
        @DisplayName("Should return 0.0 hit rate with no requests")
        void testZeroHitRate() {
            CacheStats stats = CacheStats.empty();
            assertEquals(0.0, stats.hitRate());
        }
    }
}
