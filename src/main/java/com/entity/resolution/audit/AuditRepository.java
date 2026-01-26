package com.entity.resolution.audit;

import com.entity.resolution.api.CursorPage;

import java.time.Instant;
import java.util.List;

/**
 * Repository interface for audit entry persistence.
 * Implementations provide different storage backends (in-memory, graph DB, etc.).
 */
public interface AuditRepository {

    /**
     * Saves an audit entry.
     */
    AuditEntry save(AuditEntry entry);

    /**
     * Gets all audit entries.
     */
    List<AuditEntry> findAll();

    /**
     * Gets audit entries for a specific entity.
     */
    List<AuditEntry> findByEntityId(String entityId);

    /**
     * Gets audit entries by action type.
     */
    List<AuditEntry> findByAction(AuditAction action);

    /**
     * Gets audit entries by actor.
     */
    List<AuditEntry> findByActorId(String actorId);

    /**
     * Gets audit entries within a time range.
     */
    List<AuditEntry> findBetween(Instant start, Instant end);

    /**
     * Gets audit entries for a specific entity within a time range.
     */
    List<AuditEntry> findByEntityIdBetween(String entityId, Instant start, Instant end);

    /**
     * Gets the total number of audit entries.
     */
    int count();

    /**
     * Gets the most recent entries, up to the specified limit.
     */
    List<AuditEntry> findRecent(int limit);

    /**
     * Gets audit entries for an entity using cursor-based pagination.
     * The cursor is an ISO-8601 timestamp; entries after the cursor are returned.
     *
     * @param entityId the entity ID to query
     * @param cursor   ISO-8601 timestamp cursor, or null for the first page
     * @param limit    maximum number of entries to return
     * @return a cursor page of audit entries
     */
    default CursorPage<AuditEntry> findByEntityIdAfter(String entityId, String cursor, int limit) {
        // Default implementation for backward compatibility - delegates to findByEntityId
        List<AuditEntry> all = findByEntityId(entityId);
        Instant cursorInstant = (cursor != null && !cursor.isEmpty()) ? Instant.parse(cursor) : null;
        List<AuditEntry> filtered = all.stream()
                .filter(e -> cursorInstant == null || e.timestamp().isAfter(cursorInstant))
                .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
                .limit(limit + 1)
                .toList();
        boolean hasMore = filtered.size() > limit;
        List<AuditEntry> content = hasMore ? filtered.subList(0, limit) : filtered;
        String nextCursor = hasMore ? content.get(content.size() - 1).timestamp().toString() : null;
        return new CursorPage<>(content, nextCursor, hasMore);
    }
}
