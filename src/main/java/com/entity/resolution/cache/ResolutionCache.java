package com.entity.resolution.cache;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.core.model.EntityType;

import java.util.Optional;

/**
 * Cache interface for entity resolution results.
 * Keyed by normalized name + entity type.
 */
public interface ResolutionCache {

    /**
     * Gets a cached resolution result.
     *
     * @param normalizedName the normalized entity name
     * @param entityType     the entity type
     * @return the cached result, or empty if not cached
     */
    Optional<EntityResolutionResult> get(String normalizedName, EntityType entityType);

    /**
     * Caches a resolution result.
     *
     * @param normalizedName the normalized entity name
     * @param entityType     the entity type
     * @param result         the resolution result to cache
     */
    void put(String normalizedName, EntityType entityType, EntityResolutionResult result);

    /**
     * Invalidates all cache entries associated with the given entity ID.
     *
     * @param entityId the entity ID whose entries should be invalidated
     */
    void invalidate(String entityId);

    /**
     * Invalidates all cache entries.
     */
    void invalidateAll();

    /**
     * Returns cache statistics.
     */
    CacheStats getStats();
}
