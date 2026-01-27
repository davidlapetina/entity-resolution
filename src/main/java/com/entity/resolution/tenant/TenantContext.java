package com.entity.resolution.tenant;

/**
 * Provides tenant identification for multi-tenant deployments.
 * Uses ThreadLocal for transparent tenant propagation through the call stack.
 *
 * <p>Usage:</p>
 * <pre>
 * TenantContext.setTenant("tenant-123");
 * try {
 *     resolver.resolve("Acme Corp", EntityType.COMPANY);
 * } finally {
 *     TenantContext.clear();
 * }
 * </pre>
 *
 * <p>Or using the scoped helper:</p>
 * <pre>
 * try (var scope = TenantContext.scoped("tenant-123")) {
 *     resolver.resolve("Acme Corp", EntityType.COMPANY);
 * }
 * </pre>
 */
public final class TenantContext {

    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    /**
     * Sets the current tenant for this thread.
     *
     * @param tenantId the tenant identifier
     */
    public static void setTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        currentTenant.set(tenantId);
    }

    /**
     * Gets the current tenant for this thread.
     *
     * @return the tenant identifier, or null if not set
     */
    public static String getTenant() {
        return currentTenant.get();
    }

    /**
     * Gets the current tenant, throwing if not set.
     *
     * @return the tenant identifier
     * @throws IllegalStateException if no tenant is set
     */
    public static String requireTenant() {
        String tenant = currentTenant.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant set in TenantContext");
        }
        return tenant;
    }

    /**
     * Returns true if a tenant is set for the current thread.
     */
    public static boolean hasTenant() {
        return currentTenant.get() != null;
    }

    /**
     * Clears the tenant context for the current thread.
     */
    public static void clear() {
        currentTenant.remove();
    }

    /**
     * Creates a scoped tenant context that clears on close.
     *
     * @param tenantId the tenant identifier
     * @return an AutoCloseable that clears the context on close
     */
    public static TenantScope scoped(String tenantId) {
        setTenant(tenantId);
        return new TenantScope();
    }

    /**
     * AutoCloseable that clears the TenantContext on close.
     */
    public static class TenantScope implements AutoCloseable {
        @Override
        public void close() {
            TenantContext.clear();
        }
    }
}
