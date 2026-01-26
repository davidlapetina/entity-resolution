package com.entity.resolution.health;

import com.entity.resolution.graph.GraphConnectionPool;
import com.entity.resolution.graph.PoolStats;

/**
 * Health check for the graph connection pool.
 * Reports pool utilization and returns DEGRADED or DOWN at high usage thresholds.
 */
public class ConnectionPoolHealthCheck implements HealthCheck {

    private static final double DOWN_THRESHOLD = 1.0;
    private static final double DEGRADED_THRESHOLD = 0.80;

    private final GraphConnectionPool pool;

    public ConnectionPoolHealthCheck(GraphConnectionPool pool) {
        this.pool = pool;
    }

    @Override
    public String getName() {
        return "connectionPool";
    }

    @Override
    public HealthStatus check() {
        try {
            PoolStats stats = pool.getStats();
            int total = stats.totalConnections();
            int active = stats.activeConnections();
            int idle = stats.idleConnections();

            double usagePercent = total > 0 ? (double) active / total : 0.0;

            HealthStatus base;
            if (usagePercent >= DOWN_THRESHOLD) {
                base = HealthStatus.down("Connection pool exhausted: all connections active");
            } else if (usagePercent >= DEGRADED_THRESHOLD) {
                base = HealthStatus.degraded("Connection pool usage high: " +
                        String.format("%.0f%%", usagePercent * 100));
            } else {
                base = HealthStatus.up();
            }

            return base
                    .withDetail("totalConnections", total)
                    .withDetail("activeConnections", active)
                    .withDetail("idleConnections", idle)
                    .withDetail("totalBorrowed", stats.totalBorrowed())
                    .withDetail("totalReleased", stats.totalReleased());
        } catch (Exception e) {
            return HealthStatus.down("Connection pool check failed: " + e.getMessage());
        }
    }
}
