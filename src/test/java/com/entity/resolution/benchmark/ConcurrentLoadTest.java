package com.entity.resolution.benchmark;

import com.entity.resolution.api.*;
import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrent load tests using virtual threads.
 * Validates thread safety and collects latency percentiles (p50/p95/p99).
 * Not a JMH benchmark; uses JUnit 5 for assertion-based validation.
 */
class ConcurrentLoadTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentLoadTest.class);

    private static final int THREAD_COUNT = 100;
    private static final int OPS_PER_THREAD = 100;

    private EntityResolver resolver;
    private BenchmarkMockGraphConnection connection;

    @BeforeEach
    void setUp() {
        connection = new BenchmarkMockGraphConnection("concurrent-test");
        connection.prePopulate(1000, "Company");
        resolver = EntityResolver.builder()
                .graphConnection(connection)
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
    @DisplayName("Concurrent resolution should be thread-safe")
    void testConcurrentResolution() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < THREAD_COUNT; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        long start = System.nanoTime();
                        try {
                            String name = "ConcurrentCompany-" + threadId + "-" + i;
                            EntityResolutionResult result = resolver.resolve(name, EntityType.COMPANY);
                            assertNotNull(result);
                            assertNotNull(result.canonicalEntity());
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        } finally {
                            latencies.add(System.nanoTime() - start);
                        }
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        }

        int totalOps = THREAD_COUNT * OPS_PER_THREAD;
        assertEquals(totalOps, successCount.get() + errorCount.get(),
                "All operations should complete");
        assertEquals(0, errorCount.get(), "No errors expected");

        logLatencyPercentiles("Concurrent Resolution", latencies);
    }

    @Test
    @DisplayName("Concurrent batch operations should be thread-safe")
    void testConcurrentBatches() throws Exception {
        int batchThreads = 20;
        int entitiesPerBatch = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < batchThreads; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    long start = System.nanoTime();
                    try (BatchContext batch = resolver.beginBatch()) {
                        EntityReference previous = null;
                        for (int i = 0; i < entitiesPerBatch; i++) {
                            String name = "BatchConcurrent-" + threadId + "-" + i;
                            EntityResolutionResult result = batch.resolve(name, EntityType.COMPANY);
                            EntityReference current = result.getEntityReference();

                            if (previous != null) {
                                batch.createRelationship(previous, current, "PARTNER");
                            }
                            previous = current;
                        }
                        BatchResult batchResult = batch.commit();
                        assertNotNull(batchResult);
                        assertTrue(batchResult.isSuccess());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latencies.add(System.nanoTime() - start);
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        }

        assertEquals(batchThreads, successCount.get() + errorCount.get(),
                "All batch operations should complete");
        assertEquals(0, errorCount.get(), "No batch errors expected");

        logLatencyPercentiles("Concurrent Batches", latencies);
    }

    @Test
    @DisplayName("Mixed read/write operations should be thread-safe")
    void testMixedReadWriteOps() throws Exception {
        AtomicInteger writeSuccess = new AtomicInteger(0);
        AtomicInteger readSuccess = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Long> writeLatencies = Collections.synchronizedList(new ArrayList<>());
        List<Long> readLatencies = Collections.synchronizedList(new ArrayList<>());

        // First create some entities to read
        for (int i = 0; i < 100; i++) {
            resolver.resolve("ExistingCompany-" + i, EntityType.COMPANY);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();

            // Writer threads
            for (int t = 0; t < THREAD_COUNT / 2; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        long start = System.nanoTime();
                        try {
                            String name = "NewMixedCompany-" + threadId + "-" + i;
                            EntityResolutionResult result = resolver.resolve(name, EntityType.COMPANY);
                            assertNotNull(result);
                            writeSuccess.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        } finally {
                            writeLatencies.add(System.nanoTime() - start);
                        }
                    }
                }));
            }

            // Reader threads - resolve existing entities (should find matches)
            for (int t = 0; t < THREAD_COUNT / 2; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        long start = System.nanoTime();
                        try {
                            int idx = (threadId * OPS_PER_THREAD + i) % 100;
                            String name = "ExistingCompany-" + idx;
                            EntityResolutionResult result = resolver.resolve(name, EntityType.COMPANY);
                            assertNotNull(result);
                            readSuccess.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        } finally {
                            readLatencies.add(System.nanoTime() - start);
                        }
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        }

        assertEquals(0, errorCount.get(), "No errors expected in mixed operations");

        logLatencyPercentiles("Mixed Writes", writeLatencies);
        logLatencyPercentiles("Mixed Reads", readLatencies);
    }

    private void logLatencyPercentiles(String label, List<Long> latenciesNanos) {
        if (latenciesNanos.isEmpty()) {
            log.info("{}: no data", label);
            return;
        }

        long[] sorted = latenciesNanos.stream().mapToLong(Long::longValue).sorted().toArray();
        double p50 = percentile(sorted, 50) / 1_000_000.0;
        double p95 = percentile(sorted, 95) / 1_000_000.0;
        double p99 = percentile(sorted, 99) / 1_000_000.0;
        double avg = LongStream.of(sorted).average().orElse(0) / 1_000_000.0;

        log.info("{} latencies (ms): avg={}, p50={}, p95={}, p99={}",
                label,
                String.format("%.2f", avg),
                String.format("%.2f", p50),
                String.format("%.2f", p95),
                String.format("%.2f", p99));
    }

    private long percentile(long[] sorted, int p) {
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
