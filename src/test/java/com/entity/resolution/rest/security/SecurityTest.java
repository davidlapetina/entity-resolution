package com.entity.resolution.rest.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the security infrastructure: roles, config, auth filter, authorization filter,
 * CORS filter, and rate limit filter.
 */
@DisplayName("REST Security")
class SecurityTest {

    // ══════════════════════════════════════════════════════════
    //  SecurityRole
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SecurityRole")
    class SecurityRoleTest {

        @Test
        @DisplayName("ADMIN has permission for all roles")
        void adminHasAllPermissions() {
            assertTrue(SecurityRole.ADMIN.hasPermission(SecurityRole.READER));
            assertTrue(SecurityRole.ADMIN.hasPermission(SecurityRole.WRITER));
            assertTrue(SecurityRole.ADMIN.hasPermission(SecurityRole.ADMIN));
        }

        @Test
        @DisplayName("WRITER has permission for READER and WRITER but not ADMIN")
        void writerPermissions() {
            assertTrue(SecurityRole.WRITER.hasPermission(SecurityRole.READER));
            assertTrue(SecurityRole.WRITER.hasPermission(SecurityRole.WRITER));
            assertFalse(SecurityRole.WRITER.hasPermission(SecurityRole.ADMIN));
        }

        @Test
        @DisplayName("READER only has READER permission")
        void readerPermissions() {
            assertTrue(SecurityRole.READER.hasPermission(SecurityRole.READER));
            assertFalse(SecurityRole.READER.hasPermission(SecurityRole.WRITER));
            assertFalse(SecurityRole.READER.hasPermission(SecurityRole.ADMIN));
        }

        @Test
        @DisplayName("fromString parses case-insensitively")
        void fromStringCaseInsensitive() {
            assertEquals(SecurityRole.ADMIN, SecurityRole.fromString("admin"));
            assertEquals(SecurityRole.ADMIN, SecurityRole.fromString("ADMIN"));
            assertEquals(SecurityRole.ADMIN, SecurityRole.fromString("Admin"));
        }

        @Test
        @DisplayName("fromString rejects null and blank")
        void fromStringRejectsInvalid() {
            assertThrows(IllegalArgumentException.class, () -> SecurityRole.fromString(null));
            assertThrows(IllegalArgumentException.class, () -> SecurityRole.fromString(""));
            assertThrows(IllegalArgumentException.class, () -> SecurityRole.fromString("  "));
        }

        @Test
        @DisplayName("fromString rejects unknown role")
        void fromStringRejectsUnknown() {
            assertThrows(IllegalArgumentException.class, () -> SecurityRole.fromString("SUPERUSER"));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  SecurityConfig
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SecurityConfig")
    class SecurityConfigTest {

        @Test
        @DisplayName("builder creates config with keys and roles")
        void builderCreatesConfig() {
            SecurityConfig config = SecurityConfig.builder()
                    .enabled(true)
                    .apiKeyHeader("X-API-Key")
                    .addKey("admin-key", SecurityRole.ADMIN)
                    .addKey("writer-key", SecurityRole.WRITER)
                    .addKey("reader-key", SecurityRole.READER)
                    .build();

            assertTrue(config.isEnabled());
            assertEquals("X-API-Key", config.getApiKeyHeader());
            assertEquals(3, config.keyCount());
            assertEquals(SecurityRole.ADMIN, config.getRoleForKey("admin-key"));
            assertEquals(SecurityRole.WRITER, config.getRoleForKey("writer-key"));
            assertEquals(SecurityRole.READER, config.getRoleForKey("reader-key"));
        }

        @Test
        @DisplayName("getRoleForKey returns null for unknown key")
        void unknownKeyReturnsNull() {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("known-key", SecurityRole.ADMIN)
                    .build();

            assertNull(config.getRoleForKey("unknown-key"));
            assertNull(config.getRoleForKey(null));
            assertNull(config.getRoleForKey(""));
        }

        @Test
        @DisplayName("isValidKey checks key existence")
        void isValidKey() {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("valid-key", SecurityRole.READER)
                    .build();

            assertTrue(config.isValidKey("valid-key"));
            assertFalse(config.isValidKey("invalid-key"));
            assertFalse(config.isValidKey(null));
        }

        @Test
        @DisplayName("addKeys adds multiple keys with same role")
        void addMultipleKeys() {
            SecurityConfig config = SecurityConfig.builder()
                    .addKeys(List.of("key1", "key2", "key3"), SecurityRole.WRITER)
                    .build();

            assertEquals(3, config.keyCount());
            assertEquals(SecurityRole.WRITER, config.getRoleForKey("key1"));
            assertEquals(SecurityRole.WRITER, config.getRoleForKey("key2"));
            assertEquals(SecurityRole.WRITER, config.getRoleForKey("key3"));
        }

        @Test
        @DisplayName("disabled factory creates disabled config")
        void disabledConfig() {
            SecurityConfig config = SecurityConfig.disabled();
            assertFalse(config.isEnabled());
        }

        @Test
        @DisplayName("builder rejects null/blank key")
        void rejectsInvalidKey() {
            assertThrows(IllegalArgumentException.class,
                    () -> SecurityConfig.builder().addKey(null, SecurityRole.ADMIN));
            assertThrows(IllegalArgumentException.class,
                    () -> SecurityConfig.builder().addKey("", SecurityRole.ADMIN));
        }

        @Test
        @DisplayName("builder rejects null role")
        void rejectsNullRole() {
            assertThrows(IllegalArgumentException.class,
                    () -> SecurityConfig.builder().addKey("key", null));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ApiKeyAuthFilter
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ApiKeyAuthFilter")
    class ApiKeyAuthFilterTest {

        @Test
        @DisplayName("passes through when security is disabled")
        void passesWhenDisabled() {
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(SecurityConfig.disabled());
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
        }

        @Test
        @DisplayName("passes through OPTIONS preflight")
        void passesOptionsRequest() {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("key", SecurityRole.ADMIN)
                    .build();
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(config);
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);
            when(ctx.getMethod()).thenReturn("OPTIONS");

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
        }

        @Test
        @DisplayName("rejects request with missing API key")
        void rejectsMissingKey() {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("valid-key", SecurityRole.ADMIN)
                    .build();
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(config);
            ContainerRequestContext ctx = mockRequestContext("GET", null);

            filter.filter(ctx);

            verify(ctx).abortWith(argThat(response ->
                    response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()));
        }

        @Test
        @DisplayName("rejects request with invalid API key")
        void rejectsInvalidKey() {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("valid-key", SecurityRole.ADMIN)
                    .build();
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(config);
            ContainerRequestContext ctx = mockRequestContext("GET", "wrong-key");

            filter.filter(ctx);

            verify(ctx).abortWith(argThat(response ->
                    response.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()));
        }

        @Test
        @DisplayName("sets security context for valid key")
        void setsSecurityContext() {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("valid-key", SecurityRole.WRITER)
                    .build();
            ApiKeyAuthFilter filter = new ApiKeyAuthFilter(config);
            ContainerRequestContext ctx = mockRequestContext("GET", "valid-key");

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
            verify(ctx).setProperty(eq(ApiKeyAuthFilter.ROLE_PROPERTY), eq(SecurityRole.WRITER));
            verify(ctx).setSecurityContext(any(SecurityContext.class));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  RoleAuthorizationFilter
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RoleAuthorizationFilter")
    class RoleAuthorizationFilterTest {

        @Test
        @DisplayName("passes when security is disabled")
        void passesWhenDisabled() {
            RoleAuthorizationFilter filter = new RoleAuthorizationFilter(SecurityConfig.disabled());
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
        }

        @Test
        @DisplayName("passes when no RequiresRole annotation")
        void passesWithNoAnnotation() throws Exception {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("key", SecurityRole.ADMIN)
                    .build();
            ResourceInfo resourceInfo = mock(ResourceInfo.class);
            when(resourceInfo.getResourceMethod()).thenReturn(
                    UnprotectedEndpoint.class.getMethod("open"));
            when(resourceInfo.getResourceClass()).thenReturn((Class) UnprotectedEndpoint.class);

            RoleAuthorizationFilter filter = new RoleAuthorizationFilter(config, resourceInfo);
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);
            when(ctx.getMethod()).thenReturn("GET");

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
        }

        @Test
        @DisplayName("rejects when caller role is insufficient")
        void rejectsInsufficientRole() throws Exception {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("key", SecurityRole.ADMIN)
                    .build();
            ResourceInfo resourceInfo = mock(ResourceInfo.class);
            when(resourceInfo.getResourceMethod()).thenReturn(
                    ProtectedEndpoint.class.getMethod("adminOnly"));
            when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedEndpoint.class);

            RoleAuthorizationFilter filter = new RoleAuthorizationFilter(config, resourceInfo);
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);
            when(ctx.getMethod()).thenReturn("POST");
            when(ctx.getProperty(ApiKeyAuthFilter.ROLE_PROPERTY)).thenReturn(SecurityRole.READER);
            UriInfo uriInfo = mock(UriInfo.class);
            when(uriInfo.getPath()).thenReturn("/api/v1/reviews/123/approve");
            when(ctx.getUriInfo()).thenReturn(uriInfo);

            filter.filter(ctx);

            verify(ctx).abortWith(argThat(response ->
                    response.getStatus() == Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("passes when caller role is sufficient")
        void passesSufficientRole() throws Exception {
            SecurityConfig config = SecurityConfig.builder()
                    .addKey("key", SecurityRole.ADMIN)
                    .build();
            ResourceInfo resourceInfo = mock(ResourceInfo.class);
            when(resourceInfo.getResourceMethod()).thenReturn(
                    ProtectedEndpoint.class.getMethod("writerEndpoint"));
            when(resourceInfo.getResourceClass()).thenReturn((Class) ProtectedEndpoint.class);

            RoleAuthorizationFilter filter = new RoleAuthorizationFilter(config, resourceInfo);
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);
            when(ctx.getMethod()).thenReturn("POST");
            when(ctx.getProperty(ApiKeyAuthFilter.ROLE_PROPERTY)).thenReturn(SecurityRole.ADMIN);
            UriInfo uriInfo = mock(UriInfo.class);
            when(uriInfo.getPath()).thenReturn("/api/v1/entities/resolve");
            when(ctx.getUriInfo()).thenReturn(uriInfo);

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
        }

        // Helper classes for annotation resolution
        static class UnprotectedEndpoint {
            public void open() {}
        }

        static class ProtectedEndpoint {
            @RequiresRole(SecurityRole.ADMIN)
            public void adminOnly() {}

            @RequiresRole(SecurityRole.WRITER)
            public void writerEndpoint() {}
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CorsFilter
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CorsFilter")
    class CorsFilterTest {

        @Test
        @DisplayName("adds CORS headers to response when enabled")
        void addsCorsHeaders() {
            CorsFilter filter = new CorsFilter(CorsConfig.defaults());
            ContainerRequestContext requestCtx = mock(ContainerRequestContext.class);
            when(requestCtx.getMethod()).thenReturn("GET");
            ContainerResponseContext responseCtx = mock(ContainerResponseContext.class);
            MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
            when(responseCtx.getHeaders()).thenReturn(headers);

            filter.filter(requestCtx, responseCtx);

            assertEquals("*", headers.getFirst("Access-Control-Allow-Origin"));
            assertNotNull(headers.getFirst("Access-Control-Allow-Methods"));
            assertNotNull(headers.getFirst("Access-Control-Allow-Headers"));
        }

        @Test
        @DisplayName("does not add headers when disabled")
        void noHeadersWhenDisabled() {
            CorsFilter filter = new CorsFilter(CorsConfig.disabled());
            ContainerRequestContext requestCtx = mock(ContainerRequestContext.class);
            ContainerResponseContext responseCtx = mock(ContainerResponseContext.class);
            MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
            when(responseCtx.getHeaders()).thenReturn(headers);

            filter.filter(requestCtx, responseCtx);

            assertTrue(headers.isEmpty());
        }

        @Test
        @DisplayName("handles OPTIONS preflight by aborting with 200")
        void handlesPreflightRequest() {
            CorsFilter filter = new CorsFilter(CorsConfig.defaults());
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);
            when(ctx.getMethod()).thenReturn("OPTIONS");

            filter.filter(ctx);

            verify(ctx).abortWith(argThat(response ->
                    response.getStatus() == Response.Status.OK.getStatusCode()));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  RateLimitFilter
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RateLimitFilter")
    class RateLimitFilterTest {

        @Test
        @DisplayName("passes through when disabled")
        void passesWhenDisabled() {
            RateLimitFilter filter = new RateLimitFilter(RateLimitConfig.disabled());
            ContainerRequestContext ctx = mock(ContainerRequestContext.class);

            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
        }

        @Test
        @DisplayName("passes within rate limit")
        void passesWithinLimit() {
            RateLimitConfig config = new RateLimitConfig(true, 100, 100);
            RateLimitFilter filter = new RateLimitFilter(config);
            ContainerRequestContext ctx = mockRequestContext("GET", null);
            when(ctx.getHeaderString("X-API-Key")).thenReturn("test-key");

            // Should pass for first request
            filter.filter(ctx);

            verify(ctx, never()).abortWith(any());
        }

        @Test
        @DisplayName("rejects when rate limit exceeded")
        void rejectsWhenExceeded() {
            // Very low limit: 1 request per second, burst of 1
            RateLimitConfig config = new RateLimitConfig(true, 1, 1);
            RateLimitFilter filter = new RateLimitFilter(config);

            // First request should pass
            ContainerRequestContext ctx1 = mockRequestContext("GET", null);
            when(ctx1.getHeaderString("X-API-Key")).thenReturn("test-key");
            filter.filter(ctx1);
            verify(ctx1, never()).abortWith(any());

            // Second immediate request should be rejected (bucket exhausted)
            ContainerRequestContext ctx2 = mockRequestContext("GET", null);
            when(ctx2.getHeaderString("X-API-Key")).thenReturn("test-key");
            filter.filter(ctx2);
            verify(ctx2).abortWith(argThat(response -> response.getStatus() == 429));
        }

        @Test
        @DisplayName("different API keys have separate buckets")
        void separateBucketsPerKey() {
            RateLimitConfig config = new RateLimitConfig(true, 1, 1);
            RateLimitFilter filter = new RateLimitFilter(config);

            // Exhaust key1's bucket
            ContainerRequestContext ctx1 = mockRequestContext("GET", null);
            when(ctx1.getHeaderString("X-API-Key")).thenReturn("key1");
            filter.filter(ctx1);

            // key2 should still have tokens
            ContainerRequestContext ctx2 = mockRequestContext("GET", null);
            when(ctx2.getHeaderString("X-API-Key")).thenReturn("key2");
            filter.filter(ctx2);
            verify(ctx2, never()).abortWith(any());
        }

        @Test
        @DisplayName("token bucket refills over time")
        void bucketRefills() throws InterruptedException {
            // Use 10 tokens/sec so refill takes ~100ms per token (avoids timing flakes)
            RateLimitFilter.TokenBucket bucket = new RateLimitFilter.TokenBucket(1, 10);

            // Consume the only token
            assertTrue(bucket.tryConsume());
            assertFalse(bucket.tryConsume());

            // Wait for refill (10 tokens/sec = 100ms per token)
            Thread.sleep(200);

            // Should have refilled
            assertTrue(bucket.tryConsume());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CorsConfig & RateLimitConfig
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Config Records")
    class ConfigRecordTest {

        @Test
        @DisplayName("CorsConfig defaults")
        void corsDefaults() {
            CorsConfig config = CorsConfig.defaults();
            assertTrue(config.enabled());
            assertEquals("*", config.allowedOrigins());
            assertEquals(86400, config.maxAge());
        }

        @Test
        @DisplayName("CorsConfig applies defaults for null/blank values")
        void corsDefaultsForNulls() {
            CorsConfig config = new CorsConfig(true, null, null, null, -1);
            assertEquals("*", config.allowedOrigins());
            assertNotNull(config.allowedMethods());
            assertNotNull(config.allowedHeaders());
            assertEquals(86400, config.maxAge());
        }

        @Test
        @DisplayName("RateLimitConfig defaults")
        void rateLimitDefaults() {
            RateLimitConfig config = RateLimitConfig.defaults();
            assertTrue(config.enabled());
            assertEquals(100, config.requestsPerSecond());
            assertEquals(200, config.burstSize());
        }

        @Test
        @DisplayName("RateLimitConfig applies defaults for invalid values")
        void rateLimitDefaultsForInvalid() {
            RateLimitConfig config = new RateLimitConfig(true, -1, -1);
            assertEquals(100, config.requestsPerSecond());
            // burst defaults to requestsPerSecond * 2 = 200
            assertEquals(200, config.burstSize());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private static ContainerRequestContext mockRequestContext(String method, String apiKey) {
        ContainerRequestContext ctx = mock(ContainerRequestContext.class);
        when(ctx.getMethod()).thenReturn(method);
        when(ctx.getHeaderString("X-API-Key")).thenReturn(apiKey);
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("/api/v1/entities");
        when(ctx.getUriInfo()).thenReturn(uriInfo);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(ctx.getSecurityContext()).thenReturn(securityContext);
        return ctx;
    }
}
