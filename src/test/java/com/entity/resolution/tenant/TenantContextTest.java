package com.entity.resolution.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should set and get tenant")
    void testSetAndGet() {
        TenantContext.setTenant("tenant-123");
        assertEquals("tenant-123", TenantContext.getTenant());
    }

    @Test
    @DisplayName("Should return null when not set")
    void testGetWhenNotSet() {
        assertNull(TenantContext.getTenant());
        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should clear tenant")
    void testClear() {
        TenantContext.setTenant("tenant-123");
        TenantContext.clear();
        assertNull(TenantContext.getTenant());
        assertFalse(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should report hasTenant correctly")
    void testHasTenant() {
        assertFalse(TenantContext.hasTenant());
        TenantContext.setTenant("tenant-123");
        assertTrue(TenantContext.hasTenant());
    }

    @Test
    @DisplayName("Should require tenant when set")
    void testRequireTenantWhenSet() {
        TenantContext.setTenant("tenant-123");
        assertEquals("tenant-123", TenantContext.requireTenant());
    }

    @Test
    @DisplayName("Should throw when requireTenant called without tenant")
    void testRequireTenantWithoutTenant() {
        assertThrows(IllegalStateException.class, TenantContext::requireTenant);
    }

    @Test
    @DisplayName("Should reject null tenant")
    void testNullTenant() {
        assertThrows(IllegalArgumentException.class, () -> TenantContext.setTenant(null));
    }

    @Test
    @DisplayName("Should reject blank tenant")
    void testBlankTenant() {
        assertThrows(IllegalArgumentException.class, () -> TenantContext.setTenant("  "));
    }

    @Test
    @DisplayName("Scoped context should auto-clear")
    void testScopedContext() {
        try (var scope = TenantContext.scoped("tenant-456")) {
            assertEquals("tenant-456", TenantContext.getTenant());
        }
        assertNull(TenantContext.getTenant());
    }

    @Test
    @DisplayName("Should isolate tenants between threads")
    void testThreadIsolation() throws Exception {
        TenantContext.setTenant("main-tenant");

        Thread thread = new Thread(() -> {
            assertNull(TenantContext.getTenant());
            TenantContext.setTenant("thread-tenant");
            assertEquals("thread-tenant", TenantContext.getTenant());
        });
        thread.start();
        thread.join();

        assertEquals("main-tenant", TenantContext.getTenant());
    }
}
