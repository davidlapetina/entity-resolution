package com.entity.resolution.tenant;

import java.util.concurrent.Callable;

/**
 * Provides tenant identification for multi-tenant deployments.
 * Uses {@link InheritableThreadLocal} for transparent tenant propagation
 * through the call stack, including child threads.
 *
 * <h2>Standard usage (request threads):</h2>
 * <pre>
 * TenantContext.setTenant("tenant-123");
 * try {
 *     resolver.resolve("Acme Corp", EntityType.COMPANY);
 * } finally {
 *     TenantContext.clear();
 * }
 * </pre>
 *
 * <h2>Scoped helper (auto-cleanup):</h2>
 * <pre>
 * try (var scope = TenantContext.scoped("tenant-123")) {
 *     resolver.resolve("Acme Corp", EntityType.COMPANY);
 * }
 * </pre>
 *
 * <h2>Virtual thread / executor usage:</h2>
 * <p>When submitting work to executors or virtual thread pools, the tenant
 * context must be explicitly propagated because executor-managed threads
 * do not inherit {@link InheritableThreadLocal} values:</p>
 * <pre>
 * TenantContext.setTenant("tenant-123");
 *
 * // Wrap tasks for context propagation
 * executor.submit(TenantContext.propagate(() -&gt; {
 *     // tenant is available here
 *     resolver.resolve("Acme Corp", EntityType.COMPANY);
 * }));
 *
 * // Or with Callable
 * Future&lt;Result&gt; future = executor.submit(TenantContext.propagate(() -&gt; {
 *     return resolver.resolve("Acme Corp", EntityType.COMPANY);
 * }));
 * </pre>
 */
public final class TenantContext {

    private static final InheritableThreadLocal<String> currentTenant = new InheritableThreadLocal<>();

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
     * Captures the current tenant context and returns a {@link Runnable} that
     * restores it on the executing thread. Use this when submitting work to
     * executors or virtual thread pools.
     *
     * <pre>
     * TenantContext.setTenant("tenant-123");
     * executor.submit(TenantContext.propagate(() -&gt; {
     *     // tenant-123 is available here
     *     doWork();
     * }));
     * </pre>
     *
     * @param task the task to wrap with tenant context propagation
     * @return a Runnable that sets/clears tenant context around the task
     */
    public static Runnable propagate(Runnable task) {
        String captured = getTenant();
        return () -> {
            String previous = getTenant();
            if (captured != null) {
                setTenant(captured);
            }
            try {
                task.run();
            } finally {
                if (previous != null) {
                    setTenant(previous);
                } else {
                    clear();
                }
            }
        };
    }

    /**
     * Captures the current tenant context and returns a {@link Callable} that
     * restores it on the executing thread.
     *
     * @param task the callable to wrap with tenant context propagation
     * @param <T> the return type
     * @return a Callable that sets/clears tenant context around the task
     */
    public static <T> Callable<T> propagate(Callable<T> task) {
        String captured = getTenant();
        return () -> {
            String previous = getTenant();
            if (captured != null) {
                setTenant(captured);
            }
            try {
                return task.call();
            } finally {
                if (previous != null) {
                    setTenant(previous);
                } else {
                    clear();
                }
            }
        };
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
