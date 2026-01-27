package com.entity.resolution.rest.security;

/**
 * Configuration for API rate limiting using a token bucket algorithm.
 *
 * <p>In Quarkus, populated from {@code application.yaml}:</p>
 * <pre>
 * entity-resolution:
 *   rate-limit:
 *     enabled: true
 *     requests-per-second: 100
 *     burst-size: 200
 * </pre>
 *
 * @param enabled           whether rate limiting is active
 * @param requestsPerSecond sustained request rate per API key
 * @param burstSize         maximum burst capacity (tokens in bucket)
 */
public record RateLimitConfig(
        boolean enabled,
        int requestsPerSecond,
        int burstSize
) {

    public RateLimitConfig {
        if (requestsPerSecond <= 0) {
            requestsPerSecond = 100;
        }
        if (burstSize <= 0) {
            burstSize = requestsPerSecond * 2;
        }
    }

    /**
     * Default rate limit: 100 req/s, burst of 200.
     */
    public static RateLimitConfig defaults() {
        return new RateLimitConfig(true, 100, 200);
    }

    /**
     * Disabled rate limiting.
     */
    public static RateLimitConfig disabled() {
        return new RateLimitConfig(false, 100, 200);
    }
}
