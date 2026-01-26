package com.entity.resolution.health;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Health check for JVM memory usage.
 * Reports heap utilization and returns DEGRADED or DOWN at high usage thresholds.
 */
public class MemoryHealthCheck implements HealthCheck {

    private static final double DOWN_THRESHOLD = 0.95;
    private static final double DEGRADED_THRESHOLD = 0.80;

    @Override
    public String getName() {
        return "memory";
    }

    @Override
    public HealthStatus check() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long usedMB = heapUsage.getUsed() / (1024 * 1024);
        long maxMB = heapUsage.getMax() / (1024 * 1024);
        double usagePercent = maxMB > 0 ? (double) usedMB / maxMB : 0.0;

        HealthStatus base;
        if (usagePercent >= DOWN_THRESHOLD) {
            base = HealthStatus.down("Heap usage critical: " + String.format("%.1f%%", usagePercent * 100));
        } else if (usagePercent >= DEGRADED_THRESHOLD) {
            base = HealthStatus.degraded("Heap usage high: " + String.format("%.1f%%", usagePercent * 100));
        } else {
            base = HealthStatus.up();
        }

        return base
                .withDetail("heapUsedMB", usedMB)
                .withDetail("heapMaxMB", maxMB)
                .withDetail("heapUsagePercent", Math.round(usagePercent * 1000.0) / 10.0);
    }
}
