package com.entity.resolution.cache;

/**
 * Configuration for the resolution cache.
 *
 * @param maxSize    maximum number of entries
 * @param ttlSeconds time-to-live in seconds for each entry
 * @param enabled    whether caching is enabled
 */
public record CacheConfig(int maxSize, int ttlSeconds, boolean enabled) {

    public CacheConfig {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be > 0");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be > 0");
        }
    }

    /**
     * Default cache configuration: 10,000 entries, 300s TTL, enabled.
     */
    public static CacheConfig defaults() {
        return new CacheConfig(10_000, 300, true);
    }

    /**
     * Disabled cache configuration.
     */
    public static CacheConfig disabled() {
        return new CacheConfig(1, 1, false);
    }
}
