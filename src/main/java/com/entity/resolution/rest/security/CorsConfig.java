package com.entity.resolution.rest.security;

import java.util.List;

/**
 * Configuration for Cross-Origin Resource Sharing (CORS).
 *
 * <p>In Quarkus, populated from {@code application.yaml}:</p>
 * <pre>
 * entity-resolution:
 *   cors:
 *     enabled: true
 *     allowed-origins: "*"
 *     allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"
 *     allowed-headers: "Content-Type,X-API-Key,Authorization"
 *     max-age: 86400
 * </pre>
 *
 * @param enabled        whether CORS filtering is enabled
 * @param allowedOrigins comma-separated allowed origins ("*" for all)
 * @param allowedMethods comma-separated allowed HTTP methods
 * @param allowedHeaders comma-separated allowed headers
 * @param maxAge         preflight cache duration in seconds
 */
public record CorsConfig(
        boolean enabled,
        String allowedOrigins,
        String allowedMethods,
        String allowedHeaders,
        long maxAge
) {

    public CorsConfig {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = "*";
        }
        if (allowedMethods == null || allowedMethods.isBlank()) {
            allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
        }
        if (allowedHeaders == null || allowedHeaders.isBlank()) {
            allowedHeaders = "Content-Type,X-API-Key,Authorization";
        }
        if (maxAge < 0) {
            maxAge = 86400;
        }
    }

    /**
     * Default CORS configuration: all origins, standard methods, 24h cache.
     */
    public static CorsConfig defaults() {
        return new CorsConfig(true, "*", "GET,POST,PUT,DELETE,OPTIONS",
                "Content-Type,X-API-Key,Authorization", 86400);
    }

    /**
     * Disabled CORS configuration.
     */
    public static CorsConfig disabled() {
        return new CorsConfig(false, "*", "GET,POST,PUT,DELETE,OPTIONS",
                "Content-Type,X-API-Key,Authorization", 86400);
    }
}
