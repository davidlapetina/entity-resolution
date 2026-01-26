package com.entity.resolution.audit;

import com.entity.resolution.api.CursorPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for recording and querying audit entries.
 * Delegates to an {@link AuditRepository} for persistence.
 * The no-arg constructor creates an {@link InMemoryAuditRepository} for backward compatibility.
 */
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditRepository repository;

    /**
     * Creates an AuditService with in-memory storage (backward compatible).
     */
    public AuditService() {
        this(new InMemoryAuditRepository());
    }

    /**
     * Creates an AuditService with the specified repository.
     */
    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Records an audit entry.
     */
    public AuditEntry record(AuditEntry entry) {
        AuditEntry saved = repository.save(entry);
        log.debug("Audit entry recorded: {} for entity {} by {}",
                entry.action(), entry.entityId(), entry.actorId());
        return saved;
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
        return repository.findAll();
    }

    /**
     * Gets audit entries for a specific entity.
     */
    public List<AuditEntry> getEntriesForEntity(String entityId) {
        return repository.findByEntityId(entityId);
    }

    /**
     * Gets audit entries by action type.
     */
    public List<AuditEntry> getEntriesByAction(AuditAction action) {
        return repository.findByAction(action);
    }

    /**
     * Gets audit entries by actor.
     */
    public List<AuditEntry> getEntriesByActor(String actorId) {
        return repository.findByActorId(actorId);
    }

    /**
     * Gets audit entries within a time range.
     */
    public List<AuditEntry> getEntriesBetween(Instant start, Instant end) {
        return repository.findBetween(start, end);
    }

    /**
     * Gets the audit trail for a specific entity within a time range.
     * Supports compliance reporting by filtering on entity ID and date range.
     */
    public List<AuditEntry> getAuditTrail(String entityId, Instant from, Instant to) {
        return repository.findByEntityIdBetween(entityId, from, to);
    }

    /**
     * Gets the audit trail for a specific entity using cursor-based pagination.
     *
     * @param entityId the entity ID
     * @param cursor   ISO-8601 timestamp cursor, or null for the first page
     * @param limit    maximum number of entries to return
     * @return a cursor page of audit entries
     */
    public CursorPage<AuditEntry> getAuditTrailPaginated(String entityId, String cursor, int limit) {
        return repository.findByEntityIdAfter(entityId, cursor, limit);
    }

    /**
     * Gets the total number of audit entries.
     */
    public int size() {
        return repository.count();
    }

    /**
     * Gets the most recent entries, up to the specified limit.
     */
    public List<AuditEntry> getRecentEntries(int limit) {
        return repository.findRecent(limit);
    }

    /**
     * Gets the underlying repository.
     */
    public AuditRepository getRepository() {
        return repository;
    }
}
