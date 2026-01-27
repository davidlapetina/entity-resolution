package com.entity.resolution.rest.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Jakarta RS filter that handles CORS headers.
 *
 * <p>Implements both {@link ContainerRequestFilter} (for preflight OPTIONS handling)
 * and {@link ContainerResponseFilter} (for adding CORS headers to all responses).</p>
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private final CorsConfig corsConfig;

    public CorsFilter(CorsConfig corsConfig) {
        this.corsConfig = corsConfig;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!corsConfig.enabled()) {
            return;
        }

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            requestContext.abortWith(
                    Response.ok()
                            .header("Access-Control-Allow-Origin", corsConfig.allowedOrigins())
                            .header("Access-Control-Allow-Methods", corsConfig.allowedMethods())
                            .header("Access-Control-Allow-Headers", corsConfig.allowedHeaders())
                            .header("Access-Control-Max-Age", String.valueOf(corsConfig.maxAge()))
                            .build()
            );
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!corsConfig.enabled()) {
            return;
        }

        responseContext.getHeaders().putSingle("Access-Control-Allow-Origin", corsConfig.allowedOrigins());
        responseContext.getHeaders().putSingle("Access-Control-Allow-Methods", corsConfig.allowedMethods());
        responseContext.getHeaders().putSingle("Access-Control-Allow-Headers", corsConfig.allowedHeaders());
        responseContext.getHeaders().putSingle("Access-Control-Max-Age", String.valueOf(corsConfig.maxAge()));
    }
}
