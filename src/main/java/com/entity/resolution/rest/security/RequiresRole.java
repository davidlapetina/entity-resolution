package com.entity.resolution.rest.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a REST endpoint or resource class with the minimum security role required.
 * Applied at method level to override class-level defaults.
 *
 * <p>Example:</p>
 * <pre>
 * &#64;RequiresRole(SecurityRole.WRITER)
 * &#64;POST
 * &#64;Path("/resolve")
 * public Response resolve(ResolveRequest request) { ... }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresRole {

    /**
     * The minimum role required to access this endpoint.
     */
    SecurityRole value();
}
