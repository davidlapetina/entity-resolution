package com.entity.resolution.audit;

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
     * Gets the total number of audit entries.
     */
    int count();

    /**
     * Gets the most recent entries, up to the specified limit.
     */
    List<AuditEntry> findRecent(int limit);
}
