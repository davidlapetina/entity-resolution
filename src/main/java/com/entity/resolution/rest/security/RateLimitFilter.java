package com.entity.resolution.rest.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Jakarta RS filter implementing per-API-key rate limiting using a token bucket algorithm.
 *
 * <p>Each API key gets an independent bucket with a configurable fill rate
 * and burst size. When security is disabled, rate limiting is applied globally
 * (single bucket for all requests).</p>
 *
 * <p>Returns {@code 429 Too Many Requests} when the bucket is exhausted.</p>
 */
@Provider
@Priority(Priorities.USER - 100) // Run before application logic but after auth
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitConfig config;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!config.enabled()) {
            return;
        }

        // Allow preflight
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }

        // Use API key as bucket key, or "anonymous" if no auth
        String bucketKey = requestContext.getHeaderString("X-API-Key");
        if (bucketKey == null || bucketKey.isBlank()) {
            bucketKey = "__anonymous__";
        }

        TokenBucket bucket = buckets.computeIfAbsent(bucketKey,
                k -> new TokenBucket(config.burstSize(), config.requestsPerSecond()));

        if (!bucket.tryConsume()) {
            log.warn("rateLimit.exceeded key={} path={}",
                    maskKey(bucketKey), requestContext.getUriInfo().getPath());
            requestContext.abortWith(Response.status(429)
                    .header("Retry-After", "1")
                    .entity(new ApiKeyAuthFilter.ErrorBody("TOO_MANY_REQUESTS",
                            "Rate limit exceeded. Try again later."))
                    .build());
        }
    }

    private static String maskKey(String key) {
        if ("__anonymous__".equals(key)) return "anonymous";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****";
    }

    /**
     * Token bucket implementation for rate limiting.
     * Thread-safe using CAS operations.
     */
    static class TokenBucket {
        private final int maxTokens;
        private final double refillRate; // tokens per nanosecond
        private final AtomicLong tokens; // stored as fixed-point (tokens * 1000)
        private final AtomicLong lastRefillNanos;

        TokenBucket(int maxTokens, int tokensPerSecond) {
            this.maxTokens = maxTokens;
            this.refillRate = tokensPerSecond / 1_000_000_000.0;
            this.tokens = new AtomicLong((long) maxTokens * 1000);
            this.lastRefillNanos = new AtomicLong(System.nanoTime());
        }

        boolean tryConsume() {
            refill();
            while (true) {
                long current = tokens.get();
                if (current < 1000) { // less than 1 token
                    return false;
                }
                if (tokens.compareAndSet(current, current - 1000)) {
                    return true;
                }
                // CAS failed, retry
            }
        }

        private void refill() {
            long now = System.nanoTime();
            long last = lastRefillNanos.get();
            long elapsed = now - last;

            if (elapsed <= 0) return;

            long newTokens = (long) (elapsed * refillRate * 1000);
            if (newTokens <= 0) return;

            if (lastRefillNanos.compareAndSet(last, now)) {
                long current = tokens.get();
                long updated = Math.min((long) maxTokens * 1000, current + newTokens);
                tokens.set(updated);
            }
        }

        // Visible for testing
        long availableTokens() {
            refill();
            return tokens.get() / 1000;
        }
    }
}
