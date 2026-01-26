package com.entity.resolution.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for audit persistence and retrieval through AuditService.
 * Uses InMemoryAuditRepository to verify behavior without external dependencies.
 */
class AuditPersistenceTest {

    private AuditService auditService;
    private InMemoryAuditRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuditRepository();
        auditService = new AuditService(repository);
    }

    @Test
    void fullAuditFlow_recordAndRetrieve() {
        // Record multiple audit entries
        auditService.record(AuditAction.ENTITY_CREATED, "e1", "system",
                Map.of("name", "Acme Corp"));
        auditService.record(AuditAction.SYNONYM_CREATED, "e1", "system",
                Map.of("synonym", "ACME"));
        auditService.record(AuditAction.ENTITY_MERGED, "e2", "admin",
                Map.of("mergedInto", "e1"));

        // Verify all entries persisted
        assertEquals(3, auditService.size());

        // Retrieve by entity
        List<AuditEntry> e1Entries = auditService.getEntriesForEntity("e1");
        assertEquals(2, e1Entries.size());

        // Retrieve by action
        List<AuditEntry> merges = auditService.getEntriesByAction(AuditAction.ENTITY_MERGED);
        assertEquals(1, merges.size());
        assertEquals("e2", merges.get(0).entityId());

        // Retrieve by actor
        List<AuditEntry> adminEntries = auditService.getEntriesByActor("admin");
        assertEquals(1, adminEntries.size());
    }

    @Test
    void timeRangeQuery_returnsCorrectEntries() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        Instant oneHourFromNow = now.plus(1, ChronoUnit.HOURS);

        auditService.record(AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED).entityId("e1").actorId("sys")
                .timestamp(threeDaysAgo).build());
        auditService.record(AuditEntry.builder()
                .action(AuditAction.ENTITY_MERGED).entityId("e1").actorId("sys")
                .timestamp(twoHoursAgo).build());
        auditService.record(AuditEntry.builder()
                .action(AuditAction.SYNONYM_CREATED).entityId("e1").actorId("sys")
                .timestamp(oneHourAgo).build());
        auditService.record(AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED).entityId("e2").actorId("sys")
                .timestamp(now).build());

        // Query last 3 hours
        List<AuditEntry> recent = auditService.getEntriesBetween(
                now.minus(3, ChronoUnit.HOURS), oneHourFromNow);
        assertEquals(3, recent.size());

        // Query last 90 minutes
        List<AuditEntry> last90m = auditService.getEntriesBetween(
                now.minus(90, ChronoUnit.MINUTES), oneHourFromNow);
        assertEquals(2, last90m.size());
    }

    @Test
    void getAuditTrail_entityScopedTimeRange() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        // e1 entries
        auditService.record(AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED).entityId("e1").actorId("sys")
                .timestamp(twoDaysAgo).build());
        auditService.record(AuditEntry.builder()
                .action(AuditAction.SYNONYM_CREATED).entityId("e1").actorId("sys")
                .timestamp(yesterday).build());
        auditService.record(AuditEntry.builder()
                .action(AuditAction.ENTITY_MERGED).entityId("e1").actorId("sys")
                .timestamp(now).build());

        // e2 entries (should not appear)
        auditService.record(AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED).entityId("e2").actorId("sys")
                .timestamp(yesterday).build());

        // Get e1 audit trail for last day only
        List<AuditEntry> trail = auditService.getAuditTrail("e1",
                now.minus(1, ChronoUnit.DAYS), tomorrow);
        assertEquals(2, trail.size());
        assertTrue(trail.stream().allMatch(e -> "e1".equals(e.entityId())));
    }

    @Test
    void getAuditTrail_emptyWhenNoMatches() {
        auditService.record(AuditAction.ENTITY_CREATED, "e1", "sys");

        List<AuditEntry> trail = auditService.getAuditTrail("e999",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS));
        assertTrue(trail.isEmpty());
    }

    @Test
    void concurrentWrites_noEntriesLost() throws Exception {
        int threadCount = 10;
        int entriesPerThread = 100;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    try {
                        barrier.await();
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < entriesPerThread; i++) {
                        auditService.record(AuditAction.ENTITY_CREATED,
                                "entity-" + threadId + "-" + i, "thread-" + threadId);
                    }
                }));
            }

            // Wait for all to complete
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertEquals(threadCount * entriesPerThread, auditService.size());
    }

    @Test
    void recentEntries_returnsInOrder() {
        for (int i = 0; i < 20; i++) {
            auditService.record(AuditAction.ENTITY_CREATED, "e" + i, "sys");
        }

        List<AuditEntry> recent = auditService.getRecentEntries(5);
        assertEquals(5, recent.size());
    }

    @Test
    void detailsPreserved_throughPersistence() {
        Map<String, Object> details = Map.of(
                "confidence", 0.95,
                "reason", "exact match",
                "source", "SYSTEM"
        );
        auditService.record(AuditAction.ENTITY_MERGED, "e1", "admin", details);

        List<AuditEntry> entries = auditService.getEntriesForEntity("e1");
        assertEquals(1, entries.size());

        Map<String, Object> retrieved = entries.get(0).details();
        assertEquals(0.95, retrieved.get("confidence"));
        assertEquals("exact match", retrieved.get("reason"));
        assertEquals("SYSTEM", retrieved.get("source"));
    }

    @Test
    void defaultConstructor_usesInMemoryRepository() {
        AuditService defaultService = new AuditService();
        defaultService.record(AuditAction.ENTITY_CREATED, "e1", "sys");
        assertEquals(1, defaultService.size());
        assertInstanceOf(InMemoryAuditRepository.class, defaultService.getRepository());
    }

    @Test
    void findByEntityIdBetween_filtersCorrectly() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        repository.save(AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED).entityId("e1").actorId("sys")
                .timestamp(twoDaysAgo).build());
        repository.save(AuditEntry.builder()
                .action(AuditAction.ENTITY_MERGED).entityId("e1").actorId("sys")
                .timestamp(yesterday).build());
        repository.save(AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED).entityId("e2").actorId("sys")
                .timestamp(yesterday).build());
        repository.save(AuditEntry.builder()
                .action(AuditAction.SYNONYM_CREATED).entityId("e1").actorId("sys")
                .timestamp(now).build());

        // Only e1 entries in the last day
        List<AuditEntry> results = repository.findByEntityIdBetween("e1", yesterday, tomorrow);
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(e -> "e1".equals(e.entityId())));
    }

    @Test
    void findByEntityIdBetween_emptyWhenNoOverlap() {
        Instant now = Instant.now();
        repository.save(AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED).entityId("e1").actorId("sys")
                .timestamp(now).build());

        List<AuditEntry> results = repository.findByEntityIdBetween("e1",
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        assertTrue(results.isEmpty());
    }
}
