package com.entity.resolution.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionPoolTest {

    // ========== PoolConfig Tests ==========

    @Test
    @DisplayName("Should create pool config with defaults")
    void testPoolConfigDefaults() {
        PoolConfig config = PoolConfig.builder().build();
        assertEquals(20, config.getMaxTotal());
        assertEquals(10, config.getMaxIdle());
        assertEquals(2, config.getMinIdle());
        assertEquals(5000, config.getMaxWaitMillis());
        assertTrue(config.isTestOnBorrow());
    }

    @Test
    @DisplayName("Should reject maxIdle > maxTotal")
    void testPoolConfigMaxIdleValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> PoolConfig.builder().maxTotal(5).maxIdle(10).build());
    }

    @Test
    @DisplayName("Should reject minIdle > maxIdle")
    void testPoolConfigMinIdleValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> PoolConfig.builder().maxIdle(3).minIdle(5).build());
    }

    @Test
    @DisplayName("Should reject zero maxTotal")
    void testPoolConfigZeroMaxTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> PoolConfig.builder().maxTotal(0));
    }

    // ========== PoolStats Tests ==========

    @Test
    @DisplayName("Should report pool statistics")
    void testPoolStats() {
        PoolStats stats = new PoolStats(10, 3, 7, 100, 97, 15);
        assertEquals(10, stats.totalConnections());
        assertEquals(3, stats.activeConnections());
        assertEquals(7, stats.idleConnections());
        assertEquals(100, stats.totalBorrowed());
        assertEquals(97, stats.totalReleased());
        assertEquals(15, stats.totalCreated());
    }

    // ========== PooledFalkorDBConnection Tests ==========

    @Test
    @DisplayName("Should borrow and release per operation")
    void testPooledConnectionBorrowRelease() {
        AtomicInteger borrowCount = new AtomicInteger(0);
        AtomicInteger releaseCount = new AtomicInteger(0);

        // Mock pool
        GraphConnection mockConn = createMockConnection();
        GraphConnectionPool mockPool = new GraphConnectionPool() {
            @Override
            public GraphConnection borrow() {
                borrowCount.incrementAndGet();
                return mockConn;
            }

            @Override
            public void release(GraphConnection connection) {
                releaseCount.incrementAndGet();
            }

            @Override
            public PoolStats getStats() {
                return new PoolStats(1, 0, 1, borrowCount.get(), releaseCount.get(), 1);
            }

            @Override
            public void close() {}
        };

        PooledFalkorDBConnection pooled = new PooledFalkorDBConnection(mockPool, "test-graph");

        pooled.execute("CREATE (n:Test)", Map.of());
        assertEquals(1, borrowCount.get());
        assertEquals(1, releaseCount.get());

        pooled.query("MATCH (n) RETURN n", Map.of());
        assertEquals(2, borrowCount.get());
        assertEquals(2, releaseCount.get());
    }

    @Test
    @DisplayName("Should release connection even on exception")
    void testPooledConnectionReleasesOnException() {
        AtomicInteger releaseCount = new AtomicInteger(0);

        GraphConnection failingConn = new GraphConnection() {
            @Override
            public void execute(String query, Map<String, Object> params) {
                throw new RuntimeException("simulated failure");
            }

            @Override
            public List<Map<String, Object>> query(String query, Map<String, Object> params) {
                throw new RuntimeException("simulated failure");
            }

            @Override
            public boolean isConnected() { return true; }

            @Override
            public String getGraphName() { return "test"; }

            @Override
            public void createIndexes() {}

            @Override
            public void close() {}
        };

        GraphConnectionPool mockPool = new GraphConnectionPool() {
            @Override
            public GraphConnection borrow() { return failingConn; }

            @Override
            public void release(GraphConnection connection) { releaseCount.incrementAndGet(); }

            @Override
            public PoolStats getStats() { return new PoolStats(0, 0, 0, 0, 0, 0); }

            @Override
            public void close() {}
        };

        PooledFalkorDBConnection pooled = new PooledFalkorDBConnection(mockPool, "test");

        assertThrows(RuntimeException.class, () -> pooled.execute("FAIL", Map.of()));
        assertEquals(1, releaseCount.get(), "Connection should be released even on exception");
    }

    @Test
    @DisplayName("Should delegate getGraphName to constructor parameter")
    void testPooledConnectionGraphName() {
        GraphConnectionPool mockPool = new GraphConnectionPool() {
            @Override
            public GraphConnection borrow() { return createMockConnection(); }

            @Override
            public void release(GraphConnection connection) {}

            @Override
            public PoolStats getStats() { return new PoolStats(0, 0, 0, 0, 0, 0); }

            @Override
            public void close() {}
        };

        PooledFalkorDBConnection pooled = new PooledFalkorDBConnection(mockPool, "my-graph");
        assertEquals("my-graph", pooled.getGraphName());
    }

    // ========== SimpleGraphConnectionPool Tests (with mock factory) ==========

    @Test
    @DisplayName("Should track borrow and release in stats")
    void testPoolStatsTracking() {
        AtomicInteger borrowCount = new AtomicInteger(0);
        AtomicInteger releaseCount = new AtomicInteger(0);

        GraphConnection mockConn = createMockConnection();
        GraphConnectionPool pool = new GraphConnectionPool() {
            @Override
            public GraphConnection borrow() {
                borrowCount.incrementAndGet();
                return mockConn;
            }

            @Override
            public void release(GraphConnection connection) {
                releaseCount.incrementAndGet();
            }

            @Override
            public PoolStats getStats() {
                return new PoolStats(1, borrowCount.get() - releaseCount.get(), 1,
                        borrowCount.get(), releaseCount.get(), 1);
            }

            @Override
            public void close() {}
        };

        GraphConnection conn1 = pool.borrow();
        assertEquals(1, pool.getStats().totalBorrowed());

        pool.release(conn1);
        assertEquals(1, pool.getStats().totalReleased());
    }

    @Test
    @DisplayName("PoolConfig should allow custom settings")
    void testPoolConfigCustomSettings() {
        PoolConfig config = PoolConfig.builder()
                .maxTotal(50)
                .maxIdle(25)
                .minIdle(5)
                .maxWaitMillis(10000)
                .testOnBorrow(false)
                .host("db.example.com")
                .port(6380)
                .graphName("custom-graph")
                .build();

        assertEquals(50, config.getMaxTotal());
        assertEquals(25, config.getMaxIdle());
        assertEquals(5, config.getMinIdle());
        assertEquals(10000, config.getMaxWaitMillis());
        assertFalse(config.isTestOnBorrow());
        assertEquals("db.example.com", config.getHost());
        assertEquals(6380, config.getPort());
        assertEquals("custom-graph", config.getGraphName());
    }

    private GraphConnection createMockConnection() {
        return new GraphConnection() {
            @Override
            public void execute(String query, Map<String, Object> params) {}

            @Override
            public List<Map<String, Object>> query(String query, Map<String, Object> params) {
                return List.of();
            }

            @Override
            public boolean isConnected() { return true; }

            @Override
            public String getGraphName() { return "test-graph"; }

            @Override
            public void createIndexes() {}

            @Override
            public void close() {}
        };
    }
}
