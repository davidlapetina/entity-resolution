package com.entity.resolution.health;

import com.entity.resolution.graph.GraphConnection;

/**
 * Health check for FalkorDB connectivity.
 * Executes a simple query and measures latency.
 */
public class FalkorDBHealthCheck implements HealthCheck {

    private final GraphConnection connection;

    public FalkorDBHealthCheck(GraphConnection connection) {
        this.connection = connection;
    }

    @Override
    public String getName() {
        return "falkordb";
    }

    @Override
    public HealthStatus check() {
        try {
            long startMs = System.currentTimeMillis();
            connection.query("RETURN 1");
            long latencyMs = System.currentTimeMillis() - startMs;

            return HealthStatus.up()
                    .withDetail("latencyMs", latencyMs)
                    .withDetail("graphName", connection.getGraphName());
        } catch (Exception e) {
            return HealthStatus.down("FalkorDB connection failed: " + e.getMessage())
                    .withDetail("error", e.getClass().getSimpleName());
        }
    }
}
