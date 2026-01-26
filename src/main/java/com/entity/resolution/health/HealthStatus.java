package com.entity.resolution.health;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the health status of a component or the overall system.
 * Includes a status, optional message, and arbitrary detail key-value pairs.
 */
public record HealthStatus(Status status, String message, Map<String, Object> details) {

    public enum Status { UP, DEGRADED, DOWN }

    public HealthStatus {
        details = details != null ? Collections.unmodifiableMap(new LinkedHashMap<>(details)) : Map.of();
    }

    public static HealthStatus up() {
        return new HealthStatus(Status.UP, "OK", Map.of());
    }

    public static HealthStatus up(String message) {
        return new HealthStatus(Status.UP, message, Map.of());
    }

    public static HealthStatus down(String reason) {
        return new HealthStatus(Status.DOWN, reason, Map.of());
    }

    public static HealthStatus degraded(String reason) {
        return new HealthStatus(Status.DEGRADED, reason, Map.of());
    }

    public HealthStatus withDetail(String key, Object value) {
        Map<String, Object> newDetails = new LinkedHashMap<>(this.details);
        newDetails.put(key, value);
        return new HealthStatus(this.status, this.message, newDetails);
    }

    public boolean isUp() {
        return status == Status.UP;
    }

    public boolean isDown() {
        return status == Status.DOWN;
    }

    public boolean isDegraded() {
        return status == Status.DEGRADED;
    }
}
