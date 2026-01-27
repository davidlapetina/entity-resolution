package com.entity.resolution.rest.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * Jakarta RS filter that authenticates requests using API keys.
 *
 * <p>Reads the API key from the configured header (default: {@code X-API-Key}),
 * validates it against {@link SecurityConfig}, and sets the {@link SecurityContext}
 * with the associated {@link SecurityRole}.</p>
 *
 * <p>Returns {@code 401 Unauthorized} for missing or invalid keys when security is enabled.</p>
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyAuthFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    /** Context property key for the authenticated API key's role. */
    public static final String ROLE_PROPERTY = "entity-resolution.security.role";

    private final SecurityConfig securityConfig;

    public ApiKeyAuthFilter(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!securityConfig.isEnabled()) {
            return;
        }

        // Allow CORS preflight through without auth
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }

        String apiKey = requestContext.getHeaderString(securityConfig.getApiKeyHeader());

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("auth.rejected reason=missing_api_key path={}", requestContext.getUriInfo().getPath());
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorBody("UNAUTHORIZED",
                            "Missing API key. Provide a valid key in the '"
                                    + securityConfig.getApiKeyHeader() + "' header."))
                    .build());
            return;
        }

        SecurityRole role = securityConfig.getRoleForKey(apiKey);

        if (role == null) {
            log.warn("auth.rejected reason=invalid_api_key path={}", requestContext.getUriInfo().getPath());
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorBody("UNAUTHORIZED", "Invalid API key."))
                    .build());
            return;
        }

        // Store role for downstream authorization filter
        requestContext.setProperty(ROLE_PROPERTY, role);

        // Set security context
        final String maskedKey = maskKey(apiKey);
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return () -> maskedKey;
            }

            @Override
            public boolean isUserInRole(String roleName) {
                try {
                    SecurityRole required = SecurityRole.fromString(roleName);
                    return role.hasPermission(required);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }

            @Override
            public boolean isSecure() {
                return requestContext.getSecurityContext().isSecure();
            }

            @Override
            public String getAuthenticationScheme() {
                return "API-KEY";
            }
        });

        log.debug("auth.success role={} path={}", role, requestContext.getUriInfo().getPath());
    }

    private static String maskKey(String key) {
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /**
     * Minimal error body for auth failures.
     */
    public record ErrorBody(String error, String message) {}
}
