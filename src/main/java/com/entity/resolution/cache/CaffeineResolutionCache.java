package com.entity.resolution.cache;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.core.model.EntityType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Caffeine-backed resolution cache with entity ID index for targeted invalidation.
 * Implements {@link MergeListener} to auto-invalidate on merge events.
 */
public class CaffeineResolutionCache implements ResolutionCache, MergeListener {
    private static final Logger log = LoggerFactory.getLogger(CaffeineResolutionCache.class);

    private final Cache<CacheKey, EntityResolutionResult> cache;
    // Secondary index: entityId -> set of cache keys referencing that entity
    private final ConcurrentMap<String, Set<CacheKey>> entityIndex = new ConcurrentHashMap<>();

    public CaffeineResolutionCache(CacheConfig config) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.maxSize())
                .expireAfterWrite(Duration.ofSeconds(config.ttlSeconds()))
                .recordStats()
                .removalListener((key, value, cause) -> {
                    if (key instanceof CacheKey ck) {
                        removeFromIndex(ck);
                    }
                })
                .build();
        log.info("CaffeineResolutionCache initialized: maxSize={}, ttl={}s",
                config.maxSize(), config.ttlSeconds());
    }

    @Override
    public Optional<EntityResolutionResult> get(String normalizedName, EntityType entityType) {
        CacheKey key = new CacheKey(normalizedName, entityType);
        EntityResolutionResult result = cache.getIfPresent(key);
        return Optional.ofNullable(result);
    }

    @Override
    public void put(String normalizedName, EntityType entityType, EntityResolutionResult result) {
        CacheKey key = new CacheKey(normalizedName, entityType);
        cache.put(key, result);

        // Update secondary index
        if (result.getCanonicalEntity() != null) {
            String entityId = result.getCanonicalEntity().getId();
            entityIndex.computeIfAbsent(entityId, k -> ConcurrentHashMap.newKeySet())
                    .add(key);
        }
    }

    @Override
    public void invalidate(String entityId) {
        Set<CacheKey> keys = entityIndex.remove(entityId);
        if (keys != null) {
            keys.forEach(cache::invalidate);
            log.debug("Invalidated {} cache entries for entity {}", keys.size(), entityId);
        }
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
        entityIndex.clear();
        log.debug("Invalidated all cache entries");
    }

    @Override
    public CacheStats getStats() {
        com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = cache.stats();
        return new CacheStats(
                caffeineStats.hitCount(),
                caffeineStats.missCount(),
                caffeineStats.evictionCount(),
                cache.estimatedSize()
        );
    }

    @Override
    public void onMerge(String sourceEntityId, String targetEntityId) {
        invalidate(sourceEntityId);
        invalidate(targetEntityId);
        log.debug("Cache invalidated for merge: {} -> {}", sourceEntityId, targetEntityId);
    }

    private void removeFromIndex(CacheKey key) {
        // Clean up secondary index when entries are evicted
        entityIndex.values().forEach(keys -> keys.remove(key));
    }

    /**
     * Cache key combining normalized name and entity type.
     */
    record CacheKey(String normalizedName, EntityType entityType) {}
}
