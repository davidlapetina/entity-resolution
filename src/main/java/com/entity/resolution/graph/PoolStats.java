package com.entity.resolution.graph;

/**
 * Statistics for a {@link GraphConnectionPool}.
 *
 * @param totalConnections  total number of managed connections (active + idle)
 * @param activeConnections connections currently borrowed
 * @param idleConnections   connections available for borrowing
 * @param totalBorrowed     cumulative borrow count since pool creation
 * @param totalReleased     cumulative release count since pool creation
 * @param totalCreated      cumulative connection creation count
 */
public record PoolStats(
        int totalConnections,
        int activeConnections,
        int idleConnections,
        long totalBorrowed,
        long totalReleased,
        long totalCreated
) {
}
