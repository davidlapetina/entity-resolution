package com.entity.resolution.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditServiceTest {

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService();
    }

    @Test
    @DisplayName("Should record audit entries")
    void testRecordEntry() {
        AuditEntry entry = auditService.record(
                AuditAction.ENTITY_CREATED,
                "entity-123",
                "system",
                Map.of("name", "Test Corp")
        );

        assertNotNull(entry.id());
        assertEquals(AuditAction.ENTITY_CREATED, entry.action());
        assertEquals("entity-123", entry.entityId());
        assertEquals("system", entry.actorId());
        assertEquals("Test Corp", entry.details().get("name"));
    }

    @Test
    @DisplayName("Should retrieve all entries")
    void testGetAllEntries() {
        auditService.record(AuditAction.ENTITY_CREATED, "e1", "sys");
        auditService.record(AuditAction.ENTITY_MERGED, "e2", "sys");

        assertEquals(2, auditService.size());
        assertEquals(2, auditService.getAllEntries().size());
    }

    @Test
    @DisplayName("Should filter by entity ID")
    void testFilterByEntityId() {
        auditService.record(AuditAction.ENTITY_CREATED, "entity-1", "sys");
        auditService.record(AuditAction.ENTITY_UPDATED, "entity-1", "sys");
        auditService.record(AuditAction.ENTITY_CREATED, "entity-2", "sys");

        var entries = auditService.getEntriesForEntity("entity-1");
        assertEquals(2, entries.size());
        assertTrue(entries.stream().allMatch(e -> "entity-1".equals(e.entityId())));
    }

    @Test
    @DisplayName("Should filter by action type")
    void testFilterByAction() {
        auditService.record(AuditAction.ENTITY_CREATED, "e1", "sys");
        auditService.record(AuditAction.ENTITY_MERGED, "e2", "sys");
        auditService.record(AuditAction.ENTITY_CREATED, "e3", "sys");

        var entries = auditService.getEntriesByAction(AuditAction.ENTITY_CREATED);
        assertEquals(2, entries.size());
    }

    @Test
    @DisplayName("Should filter by actor")
    void testFilterByActor() {
        auditService.record(AuditAction.ENTITY_CREATED, "e1", "user-1");
        auditService.record(AuditAction.ENTITY_CREATED, "e2", "user-2");
        auditService.record(AuditAction.ENTITY_CREATED, "e3", "user-1");

        var entries = auditService.getEntriesByActor("user-1");
        assertEquals(2, entries.size());
    }

    @Test
    @DisplayName("Should get recent entries")
    void testGetRecentEntries() {
        for (int i = 0; i < 10; i++) {
            auditService.record(AuditAction.ENTITY_CREATED, "e" + i, "sys");
        }

        var recent = auditService.getRecentEntries(3);
        assertEquals(3, recent.size());
    }

    @Test
    @DisplayName("Should return immutable lists")
    void testImmutableLists() {
        auditService.record(AuditAction.ENTITY_CREATED, "e1", "sys");

        var entries = auditService.getAllEntries();
        assertThrows(UnsupportedOperationException.class, () ->
                entries.add(AuditEntry.builder()
                        .action(AuditAction.ENTITY_CREATED)
                        .build())
        );
    }
}
