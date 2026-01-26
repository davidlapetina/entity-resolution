package com.entity.resolution.health;

import com.entity.resolution.graph.GraphConnection;
import com.entity.resolution.graph.GraphConnectionPool;
import com.entity.resolution.graph.PoolStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Health Check Tests")
class HealthCheckTest {

    @Nested
    @DisplayName("HealthStatus")
    class HealthStatusTests {

        @Test
        @DisplayName("up() should create UP status")
        void upFactory() {
            HealthStatus status = HealthStatus.up();
            assertTrue(status.isUp());
            assertFalse(status.isDown());
            assertFalse(status.isDegraded());
            assertEquals("OK", status.message());
        }

        @Test
        @DisplayName("down() should create DOWN status with reason")
        void downFactory() {
            HealthStatus status = HealthStatus.down("Database unreachable");
            assertTrue(status.isDown());
            assertFalse(status.isUp());
            assertEquals("Database unreachable", status.message());
        }

        @Test
        @DisplayName("degraded() should create DEGRADED status with reason")
        void degradedFactory() {
            HealthStatus status = HealthStatus.degraded("High memory usage");
            assertTrue(status.isDegraded());
            assertFalse(status.isUp());
            assertFalse(status.isDown());
            assertEquals("High memory usage", status.message());
        }

        @Test
        @DisplayName("withDetail() should add key-value detail")
        void withDetail() {
            HealthStatus status = HealthStatus.up()
                    .withDetail("latencyMs", 42L)
                    .withDetail("version", "1.0");

            assertEquals(42L, status.details().get("latencyMs"));
            assertEquals("1.0", status.details().get("version"));
            assertTrue(status.isUp());
        }

        @Test
        @DisplayName("withDetail() should preserve existing details")
        void withDetailPreservesExisting() {
            HealthStatus status = HealthStatus.up()
                    .withDetail("key1", "value1")
                    .withDetail("key2", "value2");

            assertEquals(2, status.details().size());
            assertEquals("value1", status.details().get("key1"));
            assertEquals("value2", status.details().get("key2"));
        }

        @Test
        @DisplayName("details should be immutable")
        void detailsImmutable() {
            HealthStatus status = HealthStatus.up().withDetail("key", "value");
            assertThrows(UnsupportedOperationException.class,
                    () -> status.details().put("another", "value"));
        }
    }

    @Nested
    @DisplayName("MemoryHealthCheck")
    class MemoryHealthCheckTests {

        @Test
        @DisplayName("Should return UP in normal test conditions")
        void shouldReturnUp() {
            MemoryHealthCheck check = new MemoryHealthCheck();
            HealthStatus status = check.check();

            // In test conditions, heap should not be exhausted
            assertTrue(status.isUp() || status.isDegraded(),
                    "Expected UP or DEGRADED, got: " + status.status());
            assertEquals("memory", check.getName());
            assertTrue(status.details().containsKey("heapUsedMB"));
            assertTrue(status.details().containsKey("heapMaxMB"));
            assertTrue(status.details().containsKey("heapUsagePercent"));
        }
    }

    @Nested
    @DisplayName("FalkorDBHealthCheck")
    class FalkorDBHealthCheckTests {

        @Test
        @DisplayName("Should return UP when connection is healthy")
        void shouldReturnUpOnSuccess() {
            GraphConnection connection = createHealthyConnection("test-graph");

            FalkorDBHealthCheck check = new FalkorDBHealthCheck(connection);
            HealthStatus status = check.check();

            assertTrue(status.isUp());
            assertEquals("falkordb", check.getName());
            assertTrue(status.details().containsKey("latencyMs"));
            assertEquals("test-graph", status.details().get("graphName"));
        }

        @Test
        @DisplayName("Should return DOWN when connection fails")
        void shouldReturnDownOnException() {
            GraphConnection connection = createFailingConnection();

            FalkorDBHealthCheck check = new FalkorDBHealthCheck(connection);
            HealthStatus status = check.check();

            assertTrue(status.isDown());
            assertTrue(status.message().contains("Connection refused"));
        }

        private GraphConnection createHealthyConnection(String graphName) {
            return new GraphConnection() {
                @Override
                public void execute(String query, Map<String, Object> params) {}

                @Override
                public List<Map<String, Object>> query(String query, Map<String, Object> params) {
                    return List.of(Map.of("1", 1));
                }

                @Override
                public boolean isConnected() { return true; }

                @Override
                public String getGraphName() { return graphName; }

                @Override
                public void createIndexes() {}

                @Override
                public void close() {}
            };
        }

        private GraphConnection createFailingConnection() {
            return new GraphConnection() {
                @Override
                public void execute(String query, Map<String, Object> params) {
                    throw new RuntimeException("Connection refused");
                }

                @Override
                public List<Map<String, Object>> query(String query, Map<String, Object> params) {
                    throw new RuntimeException("Connection refused");
                }

                @Override
                public boolean isConnected() { return false; }

                @Override
                public String getGraphName() { return "test"; }

                @Override
                public void createIndexes() {}

                @Override
                public void close() {}
            };
        }
    }

    @Nested
    @DisplayName("ConnectionPoolHealthCheck")
    class ConnectionPoolHealthCheckTests {

        @Test
        @DisplayName("Should return UP when pool usage is low")
        void shouldReturnUpWhenLowUsage() {
            GraphConnectionPool pool = createPoolWithStats(new PoolStats(10, 2, 8, 100, 98, 10));

            ConnectionPoolHealthCheck check = new ConnectionPoolHealthCheck(pool);
            HealthStatus status = check.check();

            assertTrue(status.isUp());
            assertEquals("connectionPool", check.getName());
            assertEquals(10, status.details().get("totalConnections"));
            assertEquals(2, status.details().get("activeConnections"));
            assertEquals(8, status.details().get("idleConnections"));
        }

        @Test
        @DisplayName("Should return DEGRADED when pool usage exceeds 80%")
        void shouldReturnDegradedWhenHighUsage() {
            GraphConnectionPool pool = createPoolWithStats(new PoolStats(10, 9, 1, 100, 91, 10));

            ConnectionPoolHealthCheck check = new ConnectionPoolHealthCheck(pool);
            HealthStatus status = check.check();

            assertTrue(status.isDegraded());
        }

        @Test
        @DisplayName("Should return DOWN when all connections are active")
        void shouldReturnDownWhenExhausted() {
            GraphConnectionPool pool = createPoolWithStats(new PoolStats(10, 10, 0, 100, 90, 10));

            ConnectionPoolHealthCheck check = new ConnectionPoolHealthCheck(pool);
            HealthStatus status = check.check();

            assertTrue(status.isDown());
            assertTrue(status.message().contains("exhausted"));
        }

        @Test
        @DisplayName("Should return DOWN when stats retrieval fails")
        void shouldReturnDownOnException() {
            GraphConnectionPool pool = createFailingPool();

            ConnectionPoolHealthCheck check = new ConnectionPoolHealthCheck(pool);
            HealthStatus status = check.check();

            assertTrue(status.isDown());
            assertTrue(status.message().contains("Pool closed"));
        }

        private GraphConnectionPool createPoolWithStats(PoolStats stats) {
            return new GraphConnectionPool() {
                @Override
                public GraphConnection borrow() { return null; }

                @Override
                public void release(GraphConnection connection) {}

                @Override
                public PoolStats getStats() { return stats; }

                @Override
                public void close() {}
            };
        }

        private GraphConnectionPool createFailingPool() {
            return new GraphConnectionPool() {
                @Override
                public GraphConnection borrow() { return null; }

                @Override
                public void release(GraphConnection connection) {}

                @Override
                public PoolStats getStats() { throw new RuntimeException("Pool closed"); }

                @Override
                public void close() {}
            };
        }
    }

    @Nested
    @DisplayName("HealthCheckRegistry")
    class HealthCheckRegistryTests {

        @Test
        @DisplayName("Should return UP when no checks are registered")
        void noChecksRegistered() {
            HealthCheckRegistry registry = new HealthCheckRegistry();
            HealthStatus status = registry.checkAll();

            assertTrue(status.isUp());
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("Should return UP when all checks pass")
        void allChecksUp() {
            HealthCheckRegistry registry = new HealthCheckRegistry();
            registry.register(createCheck("check1", HealthStatus.up()));
            registry.register(createCheck("check2", HealthStatus.up()));

            HealthStatus status = registry.checkAll();
            assertTrue(status.isUp());
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("Should return DOWN when any check is DOWN")
        void anyCheckDown() {
            HealthCheckRegistry registry = new HealthCheckRegistry();
            registry.register(createCheck("check1", HealthStatus.up()));
            registry.register(createCheck("check2", HealthStatus.down("Failed")));
            registry.register(createCheck("check3", HealthStatus.degraded("Slow")));

            HealthStatus status = registry.checkAll();
            assertTrue(status.isDown());
        }

        @Test
        @DisplayName("Should return DEGRADED when any check is DEGRADED and none DOWN")
        void anyCheckDegraded() {
            HealthCheckRegistry registry = new HealthCheckRegistry();
            registry.register(createCheck("check1", HealthStatus.up()));
            registry.register(createCheck("check2", HealthStatus.degraded("Slow")));

            HealthStatus status = registry.checkAll();
            assertTrue(status.isDegraded());
        }

        @Test
        @DisplayName("Should include individual check results in details")
        void includesCheckDetails() {
            HealthCheckRegistry registry = new HealthCheckRegistry();
            registry.register(createCheck("db", HealthStatus.up().withDetail("latencyMs", 5L)));
            registry.register(createCheck("memory", HealthStatus.up()));

            HealthStatus status = registry.checkAll();
            assertTrue(status.details().containsKey("db"));
            assertTrue(status.details().containsKey("memory"));
        }

        @Test
        @DisplayName("Should ignore null registrations")
        void ignoresNull() {
            HealthCheckRegistry registry = new HealthCheckRegistry();
            registry.register(null);
            assertEquals(0, registry.size());
        }

        private HealthCheck createCheck(String name, HealthStatus result) {
            return new HealthCheck() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public HealthStatus check() {
                    return result;
                }
            };
        }
    }
}
