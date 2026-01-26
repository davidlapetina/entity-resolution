package com.entity.resolution.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InMemoryAuditRepository.
 */
class InMemoryAuditRepositoryTest {

    private InMemoryAuditRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuditRepository();
    }

    @Test
    void save_storesEntry() {
        AuditEntry entry = AuditEntry.builder()
                .action(AuditAction.ENTITY_CREATED)
                .entityId("e1")
                .actorId("actor1")
                .build();

        AuditEntry saved = repository.save(entry);

        assertEquals(entry, saved);
        assertEquals(1, repository.count());
    }

    @Test
    void findAll_returnsAllEntries() {
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_MERGED, "e2", "actor2"));

        List<AuditEntry> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findAll_returnsImmutableList() {
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        List<AuditEntry> all = repository.findAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(
                createEntry(AuditAction.ENTITY_CREATED, "e2", "actor2")));
    }

    @Test
    void findByEntityId_filtersCorrectly() {
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_MERGED, "e1", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e2", "actor1"));

        List<AuditEntry> entries = repository.findByEntityId("e1");
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> "e1".equals(e.entityId())));
    }

    @Test
    void findByAction_filtersCorrectly() {
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_MERGED, "e2", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e3", "actor2"));

        List<AuditEntry> entries = repository.findByAction(AuditAction.ENTITY_CREATED);
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> e.action() == AuditAction.ENTITY_CREATED));
    }

    @Test
    void findByActorId_filtersCorrectly() {
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_MERGED, "e2", "actor2"));
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e3", "actor1"));

        List<AuditEntry> entries = repository.findByActorId("actor1");
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> "actor1".equals(e.actorId())));
    }

    @Test
    void findBetween_filtersCorrectly() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant tomorrow = now.plus(1, ChronoUnit.DAYS);

        repository.save(createEntryWithTimestamp(AuditAction.ENTITY_CREATED, "e1", twoDaysAgo));
        repository.save(createEntryWithTimestamp(AuditAction.ENTITY_MERGED, "e2", yesterday));
        repository.save(createEntryWithTimestamp(AuditAction.ENTITY_CREATED, "e3", now));

        List<AuditEntry> entries = repository.findBetween(yesterday, tomorrow);
        assertEquals(2, entries.size());
    }

    @Test
    void count_returnsCorrectCount() {
        assertEquals(0, repository.count());
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        assertEquals(1, repository.count());
        repository.save(createEntry(AuditAction.ENTITY_MERGED, "e2", "actor1"));
        assertEquals(2, repository.count());
    }

    @Test
    void findRecent_returnsLimitedEntries() {
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_MERGED, "e2", "actor1"));
        repository.save(createEntry(AuditAction.SYNONYM_CREATED, "e3", "actor1"));

        List<AuditEntry> recent = repository.findRecent(2);
        assertEquals(2, recent.size());
    }

    @Test
    void findRecent_returnsAllWhenLimitExceedsSize() {
        repository.save(createEntry(AuditAction.ENTITY_CREATED, "e1", "actor1"));
        repository.save(createEntry(AuditAction.ENTITY_MERGED, "e2", "actor1"));

        List<AuditEntry> recent = repository.findRecent(10);
        assertEquals(2, recent.size());
    }

    private AuditEntry createEntry(AuditAction action, String entityId, String actorId) {
        return AuditEntry.builder()
                .action(action)
                .entityId(entityId)
                .actorId(actorId)
                .details(Map.of("key", "value"))
                .build();
    }

    private AuditEntry createEntryWithTimestamp(AuditAction action, String entityId, Instant timestamp) {
        return AuditEntry.builder()
                .action(action)
                .entityId(entityId)
                .actorId("actor")
                .timestamp(timestamp)
                .build();
    }
}
