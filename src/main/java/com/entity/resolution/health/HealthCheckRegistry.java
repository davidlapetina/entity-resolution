package com.entity.resolution.health;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of health checks that can be queried for aggregate system health.
 * Individual checks are run and their results combined into an overall status.
 *
 * <p>Aggregation logic:</p>
 * <ul>
 *   <li>Any DOWN check → overall status is DOWN</li>
 *   <li>Any DEGRADED check (and none DOWN) → overall status is DEGRADED</li>
 *   <li>All UP → overall status is UP</li>
 * </ul>
 */
public class HealthCheckRegistry {

    private final List<HealthCheck> checks = new ArrayList<>();

    public void register(HealthCheck check) {
        if (check != null) {
            checks.add(check);
        }
    }

    /**
     * Runs all registered health checks and returns an aggregate status.
     */
    public HealthStatus checkAll() {
        if (checks.isEmpty()) {
            return HealthStatus.up("No health checks registered");
        }

        Map<String, Object> checkResults = new LinkedHashMap<>();
        HealthStatus.Status worstStatus = HealthStatus.Status.UP;
        String worstMessage = "OK";

        for (HealthCheck check : checks) {
            HealthStatus result = check.check();
            checkResults.put(check.getName(), Map.of(
                    "status", result.status().name(),
                    "message", result.message(),
                    "details", result.details()
            ));

            if (result.status().ordinal() > worstStatus.ordinal()) {
                worstStatus = result.status();
                worstMessage = check.getName() + ": " + result.message();
            }
        }

        HealthStatus aggregate = new HealthStatus(worstStatus, worstMessage, Map.of());
        for (Map.Entry<String, Object> entry : checkResults.entrySet()) {
            aggregate = aggregate.withDetail(entry.getKey(), entry.getValue());
        }

        return aggregate;
    }

    /**
     * Returns the number of registered health checks.
     */
    public int size() {
        return checks.size();
    }
}
