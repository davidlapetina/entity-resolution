package com.entity.resolution.lock;

/**
 * Configuration for distributed lock implementations.
 *
 * @param timeoutMs      maximum time to wait for lock acquisition
 * @param maxRetries     maximum number of retry attempts
 * @param retryDelayMs   delay between retry attempts in milliseconds
 * @param lockTtlSeconds time-to-live for locks (for graph-based locks)
 */
public record LockConfig(long timeoutMs, int maxRetries, long retryDelayMs, int lockTtlSeconds) {

    public LockConfig {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be > 0");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (retryDelayMs <= 0) {
            throw new IllegalArgumentException("retryDelayMs must be > 0");
        }
        if (lockTtlSeconds <= 0) {
            throw new IllegalArgumentException("lockTtlSeconds must be > 0");
        }
    }

    /**
     * Default configuration: 5s timeout, 3 retries, 100ms delay, 30s TTL.
     */
    public static LockConfig defaults() {
        return new LockConfig(5000, 3, 100, 30);
    }
}
