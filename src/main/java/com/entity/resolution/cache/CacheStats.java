package com.entity.resolution.cache;

/**
 * Cache metrics.
 *
 * @param hitCount    number of cache hits
 * @param missCount   number of cache misses
 * @param evictionCount number of evictions
 * @param size        current number of entries
 */
public record CacheStats(long hitCount, long missCount, long evictionCount, long size) {

    /**
     * Returns the hit rate (0.0 to 1.0).
     */
    public double hitRate() {
        long total = hitCount + missCount;
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    /**
     * Returns empty stats.
     */
    public static CacheStats empty() {
        return new CacheStats(0, 0, 0, 0);
    }
}
