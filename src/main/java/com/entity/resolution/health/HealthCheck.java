package com.entity.resolution.health;

/**
 * Interface for individual health checks.
 * Implementations check a specific component (database, memory, pool, etc.)
 * and return a {@link HealthStatus} indicating the component's current state.
 */
public interface HealthCheck {

    /**
     * Returns the name of this health check.
     */
    String getName();

    /**
     * Performs the health check and returns the current status.
     */
    HealthStatus check();
}
