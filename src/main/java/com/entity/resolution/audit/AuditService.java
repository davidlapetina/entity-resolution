package com.entity.resolution.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Service for recording and querying audit entries.
 * Provides append-only storage for all auditable operations.
 */
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final List<AuditEntry> entries;

    public AuditService() {
        this.entries = new CopyOnWriteArrayList<>();
    }

    /**
     * Records an audit entry.
     */
    public AuditEntry record(AuditEntry entry) {
        entries.add(entry);
        log.debug("Audit entry recorded: {} for entity {} by {}",
                entry.action(), entry.entityId(), entry.actorId());
        return entry;
    }

    /**
     * Records an audit entry with builder pattern.
     */
    public AuditEntry record(AuditAction action, String entityId, String actorId, Map<String, Object> details) {
        AuditEntry entry = AuditEntry.builder()
                .action(action)
                .entityId(entityId)
                .actorId(actorId)
                .details(details)
                .build();
        return record(entry);
    }

    /**
     * Records a simple audit entry without details.
     */
    public AuditEntry record(AuditAction action, String entityId, String actorId) {
        return record(action, entityId, actorId, null);
    }

    /**
     * Gets all audit entries (immutable view).
     */
    public List<AuditEntry> getAllEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Gets audit entries for a specific entity.
     */
    public List<AuditEntry> getEntriesForEntity(String entityId) {
        return entries.stream()
                .filter(e -> entityId.equals(e.entityId()))
                .collect(Collectors.toList());
    }

    /**
     * Gets audit entries by action type.
     */
    public List<AuditEntry> getEntriesByAction(AuditAction action) {
        return entries.stream()
                .filter(e -> e.action() == action)
                .collect(Collectors.toList());
    }

    /**
     * Gets audit entries by actor.
     */
    public List<AuditEntry> getEntriesByActor(String actorId) {
        return entries.stream()
                .filter(e -> actorId.equals(e.actorId()))
                .collect(Collectors.toList());
    }

    /**
     * Gets audit entries within a time range.
     */
    public List<AuditEntry> getEntriesBetween(Instant start, Instant end) {
        return entries.stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    /**
     * Gets the total number of audit entries.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Gets the most recent entries, up to the specified limit.
     */
    public List<AuditEntry> getRecentEntries(int limit) {
        int size = entries.size();
        if (size <= limit) {
            return getAllEntries();
        }
        return Collections.unmodifiableList(
                new ArrayList<>(entries.subList(size - limit, size))
        );
    }
}
