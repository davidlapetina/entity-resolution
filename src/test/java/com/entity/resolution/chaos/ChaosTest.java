package com.entity.resolution.chaos;

import com.entity.resolution.api.*;
import com.entity.resolution.benchmark.BenchmarkMockGraphConnection;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.health.HealthStatus;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chaos tests validating the library's resilience to connection failures,
 * partial failures, and degraded conditions. Uses in-memory mock wrapped
 * in {@link ChaosGraphConnection} - does NOT require Docker.
 */
class ChaosTest {

    private BenchmarkMockGraphConnection baseMock;
    private ChaosGraphConnection chaosConnection;
    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        baseMock = new BenchmarkMockGraphConnection("chaos-test");
        chaosConnection = new ChaosGraphConnection(baseMock);
        resolver = EntityResolver.builder()
                .graphConnection(chaosConnection)
                .createIndexes(false)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
    }

    @Test
    @DisplayName("Should throw on connection failure during resolve")
    void testConnectionFailureDuringResolve() {
        chaosConnection.setFailOnQuery(true);

        assertThrows(RuntimeException.class, () ->
                resolver.resolve("Test Company", EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should handle batch partial failure gracefully")
    void testBatchPartialFailure() {
        // Allow first few operations, then fail
        chaosConnection.setFailAfterNOperations(5);

        assertThrows(RuntimeException.class, () -> {
            try (BatchContext batch = resolver.beginBatch()) {
                for (int i = 0; i < 20; i++) {
                    batch.resolve("FailBatchEntity-" + i, EntityType.COMPANY);
                }
                batch.commit();
            }
        });
    }

    @Test
    @DisplayName("Should recover after connection failure is resolved")
    void testConnectionRecovery() {
        // First, create an entity successfully
        EntityResolutionResult initial = resolver.resolve("PreFailure Corp", EntityType.COMPANY);
        assertTrue(initial.isNewEntity());

        // Simulate failure
        chaosConnection.setFailOnQuery(true);
        assertThrows(RuntimeException.class, () ->
                resolver.resolve("During Failure Corp", EntityType.COMPANY));

        // Recover
        chaosConnection.reset();
        EntityResolutionResult recovered = resolver.resolve("PostRecovery Corp", EntityType.COMPANY);
        assertNotNull(recovered.canonicalEntity());
    }

    @Test
    @DisplayName("Should report health as DOWN when queries fail")
    void testHealthCheckReportsDown() {
        // Health check runs connection.query("RETURN 1"), so fail queries
        chaosConnection.setFailOnQuery(true);

        HealthStatus status = resolver.health();
        assertNotNull(status);
        assertTrue(status.isDown() || status.isDegraded(),
                "Health should be DOWN or DEGRADED when queries fail, got: " + status.status());
    }

    @Test
    @DisplayName("Should fail mid-workflow when operation limit is exceeded")
    void testFailAfterNOperations() {
        // Create an entity successfully first
        EntityResolutionResult e1 = resolver.resolve("ExistingCorp", EntityType.COMPANY);
        assertTrue(e1.isNewEntity());

        // Allow only a few more operations, then fail
        // This simulates a connection that dies during a multi-step workflow
        chaosConnection.setFailAfterNOperations(2);

        // Second resolve will exhaust the operation limit during its query/write cycle
        assertThrows(RuntimeException.class, () ->
                resolver.resolve("AnotherNewCorp", EntityType.COMPANY));

        // After resetting, the original entity should still be intact
        chaosConnection.reset();
        EntityResolutionResult check = resolver.resolve("ExistingCorp", EntityType.COMPANY);
        assertNotNull(check.canonicalEntity());
        assertEquals(e1.canonicalEntity().getId(), check.canonicalEntity().getId());
    }

    @Test
    @DisplayName("Should tolerate slow connections with injected delay")
    void testSlowConnectionTolerance() {
        chaosConnection.setInjectDelayMs(50);

        long start = System.nanoTime();
        EntityResolutionResult result = resolver.resolve("SlowCompany Corp", EntityType.COMPANY);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(result.canonicalEntity());
        assertTrue(elapsedMs >= 50, "Should take at least 50ms due to injected delay");
    }

    @Test
    @DisplayName("Should handle intermittent failures across multiple operations")
    void testIntermittentFailures() {
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < 10; i++) {
            // Alternate between working and failing
            if (i % 3 == 2) {
                chaosConnection.setFailOnQuery(true);
            } else {
                chaosConnection.setFailOnQuery(false);
            }

            try {
                EntityResolutionResult result = resolver.resolve(
                        "IntermittentEntity-" + i, EntityType.COMPANY);
                assertNotNull(result);
                successCount++;
            } catch (RuntimeException e) {
                failureCount++;
            }
        }

        assertTrue(successCount > 0, "Some operations should succeed");
        assertTrue(failureCount > 0, "Some operations should fail");
        assertEquals(10, successCount + failureCount, "All operations should complete");
    }
}
