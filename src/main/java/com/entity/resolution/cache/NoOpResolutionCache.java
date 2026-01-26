package com.entity.resolution.cache;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.core.model.EntityType;

import java.util.Optional;

/**
 * No-op cache implementation. All operations are no-ops.
 * Used as the default when caching is disabled.
 */
public class NoOpResolutionCache implements ResolutionCache {

    @Override
    public Optional<EntityResolutionResult> get(String normalizedName, EntityType entityType) {
        return Optional.empty();
    }

    @Override
    public void put(String normalizedName, EntityType entityType, EntityResolutionResult result) {
        // no-op
    }

    @Override
    public void invalidate(String entityId) {
        // no-op
    }

    @Override
    public void invalidateAll() {
        // no-op
    }

    @Override
    public CacheStats getStats() {
        return CacheStats.empty();
    }
}
