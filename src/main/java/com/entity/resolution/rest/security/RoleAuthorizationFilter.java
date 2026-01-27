package com.entity.resolution.rest.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Jakarta RS filter that enforces role-based access control.
 *
 * <p>Inspects the {@link RequiresRole} annotation on the matched resource method
 * (or its class) and compares it against the authenticated role set by
 * {@link ApiKeyAuthFilter}.</p>
 *
 * <p>Returns {@code 403 Forbidden} if the caller's role is insufficient.</p>
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class RoleAuthorizationFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RoleAuthorizationFilter.class);

    private final SecurityConfig securityConfig;

    @Context
    private ResourceInfo resourceInfo;

    public RoleAuthorizationFilter(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    /**
     * Constructor for CDI/framework injection where ResourceInfo is set via @Context.
     */
    public RoleAuthorizationFilter(SecurityConfig securityConfig, ResourceInfo resourceInfo) {
        this.securityConfig = securityConfig;
        this.resourceInfo = resourceInfo;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!securityConfig.isEnabled()) {
            return;
        }

        // Allow CORS preflight
        if ("OPTIONS".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }

        RequiresRole annotation = resolveAnnotation();
        if (annotation == null) {
            // No role requirement on this endpoint
            return;
        }

        SecurityRole requiredRole = annotation.value();
        SecurityRole callerRole = (SecurityRole) requestContext.getProperty(ApiKeyAuthFilter.ROLE_PROPERTY);

        if (callerRole == null) {
            // Auth filter didn't set a role (shouldn't happen if security is enabled)
            log.warn("authz.rejected reason=no_role path={}", requestContext.getUriInfo().getPath());
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity(new ApiKeyAuthFilter.ErrorBody("FORBIDDEN", "Access denied."))
                    .build());
            return;
        }

        if (!callerRole.hasPermission(requiredRole)) {
            log.warn("authz.rejected callerRole={} requiredRole={} path={}",
                    callerRole, requiredRole, requestContext.getUriInfo().getPath());
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity(new ApiKeyAuthFilter.ErrorBody("FORBIDDEN",
                            "Insufficient permissions. Required role: " + requiredRole))
                    .build());
            return;
        }

        log.debug("authz.success callerRole={} requiredRole={} path={}",
                callerRole, requiredRole, requestContext.getUriInfo().getPath());
    }

    /**
     * Resolves the RequiresRole annotation from the matched method or class.
     * Method-level annotations take precedence over class-level.
     */
    private RequiresRole resolveAnnotation() {
        if (resourceInfo == null) {
            return null;
        }

        // Check method first
        Method method = resourceInfo.getResourceMethod();
        if (method != null) {
            RequiresRole methodAnnotation = method.getAnnotation(RequiresRole.class);
            if (methodAnnotation != null) {
                return methodAnnotation;
            }
        }

        // Fall back to class
        Class<?> resourceClass = resourceInfo.getResourceClass();
        if (resourceClass != null) {
            return resourceClass.getAnnotation(RequiresRole.class);
        }

        return null;
    }
}
