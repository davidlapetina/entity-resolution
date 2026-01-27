package com.entity.resolution.integration;

import com.entity.resolution.api.*;
import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for concurrent batch operations against a live FalkorDB instance.
 */
@Tag("integration")
class ConcurrentBatchIT extends AbstractFalkorDBIntegrationTest {

    private EntityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = createResolver("concurrent-batch");
    }

    @AfterEach
    void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
    }

    @Test
    @DisplayName("Parallel batches should not interfere with each other")
    void testParallelBatches() throws Exception {
        int batchCount = 5;
        int entitiesPerBatch = 20;
        AtomicInteger totalCreated = new AtomicInteger(0);

        try (var executor = Executors.newFixedThreadPool(batchCount)) {
            List<Future<BatchResult>> futures = new ArrayList<>();

            for (int b = 0; b < batchCount; b++) {
                final int batchId = b;
                futures.add(executor.submit(() -> {
                    try (BatchContext batch = resolver.beginBatch()) {
                        for (int i = 0; i < entitiesPerBatch; i++) {
                            String name = "ParallelBatch-" + batchId + "-Entity-" + i;
                            batch.resolve(name, EntityType.COMPANY);
                        }
                        return batch.commit();
                    }
                }));
            }

            for (Future<BatchResult> future : futures) {
                BatchResult result = future.get(30, TimeUnit.SECONDS);
                assertTrue(result.isSuccess(), "Batch should succeed");
                totalCreated.addAndGet(result.newEntitiesCreated());
            }
        }

        assertEquals(batchCount * entitiesPerBatch, totalCreated.get(),
                "All entities should be created (names are unique)");
    }

    @Test
    @DisplayName("Cross-referencing between batch entities should work")
    void testCrossReferences() {
        try (BatchContext batch = resolver.beginBatch()) {
            EntityResolutionResult parent = batch.resolve("Parent Corp", EntityType.COMPANY);
            EntityResolutionResult child1 = batch.resolve("Child One LLC", EntityType.COMPANY);
            EntityResolutionResult child2 = batch.resolve("Child Two LLC", EntityType.COMPANY);

            EntityReference parentRef = parent.getEntityReference();
            EntityReference child1Ref = child1.getEntityReference();
            EntityReference child2Ref = child2.getEntityReference();

            batch.createRelationship(child1Ref, parentRef, "SUBSIDIARY_OF");
            batch.createRelationship(child2Ref, parentRef, "SUBSIDIARY_OF");
            batch.createRelationship(child1Ref, child2Ref, "SIBLING");

            BatchResult result = batch.commit();
            assertTrue(result.isSuccess());
            assertEquals(3, result.relationshipsCreated());
        }
    }

    @Test
    @DisplayName("Large batch with 1000 entities should complete successfully")
    void testLargeBatch() {
        int entityCount = 1000;

        try (BatchContext batch = resolver.beginBatch()) {
            for (int i = 0; i < entityCount; i++) {
                batch.resolve("LargeBatchEntity-" + i, EntityType.COMPANY);
            }

            BatchResult result = batch.commit();
            assertTrue(result.isSuccess());
            assertEquals(entityCount, result.totalEntitiesResolved());
            assertEquals(entityCount, result.newEntitiesCreated());
        }
    }

    @Test
    @DisplayName("Concurrent resolution of same name should deduplicate")
    void testConcurrentSameNameDedup() throws Exception {
        int threadCount = 10;
        String entityName = "Shared Entity Name Corp";
        CountDownLatch latch = new CountDownLatch(threadCount);

        try (var executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<EntityResolutionResult>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    latch.countDown();
                    latch.await(5, TimeUnit.SECONDS);
                    return resolver.resolve(entityName, EntityType.COMPANY);
                }));
            }

            String canonicalId = null;
            for (Future<EntityResolutionResult> future : futures) {
                EntityResolutionResult result = future.get(30, TimeUnit.SECONDS);
                assertNotNull(result.canonicalEntity());

                if (canonicalId == null) {
                    canonicalId = result.canonicalEntity().getId();
                } else {
                    assertEquals(canonicalId, result.canonicalEntity().getId(),
                            "All threads should resolve to the same canonical entity");
                }
            }
        }
    }
}
