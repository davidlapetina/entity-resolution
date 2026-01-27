package com.entity.resolution.retention;

import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.graph.GraphConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for applying data retention policies.
 * Cleans up expired merged entities, audit entries, and completed review items
 * based on configured retention periods.
 *
 * <p>Supports both hard delete and soft delete (setting a deletedAt timestamp).
 * All retention operations are recorded in the audit trail.</p>
 */
public class RetentionService {
    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final GraphConnection connection;
    private final AuditService auditService;
    private final int batchSize;

    public RetentionService(GraphConnection connection, AuditService auditService) {
        this(connection, auditService, DEFAULT_BATCH_SIZE);
    }

    public RetentionService(GraphConnection connection, AuditService auditService, int batchSize) {
        this.connection = connection;
        this.auditService = auditService;
        this.batchSize = batchSize;
    }

    /**
     * Applies the retention policy, cleaning up expired data.
     * Processes in batches to avoid long-running transactions.
     *
     * @param policy the retention policy to apply
     * @return the result of the cleanup operation
     */
    public RetentionResult applyRetention(RetentionPolicy policy) {
        log.info("retention.starting policy={}", policy);
        Instant now = Instant.now();

        long mergedDeleted = cleanupMergedEntities(policy, now);
        long auditDeleted = cleanupAuditEntries(policy, now);
        long reviewsDeleted = cleanupReviewItems(policy, now);

        RetentionResult result = new RetentionResult(mergedDeleted, auditDeleted, reviewsDeleted);

        // Record retention operation in audit trail
        auditService.record(AuditAction.ENTITY_UPDATED, "RETENTION", "RETENTION_SERVICE", Map.of(
                "mergedEntitiesDeleted", mergedDeleted,
                "auditEntriesDeleted", auditDeleted,
                "reviewItemsDeleted", reviewsDeleted,
                "totalDeleted", result.totalDeleted(),
                "softDeleteEnabled", policy.softDeleteEnabled()
        ));

        log.info("retention.completed result={}", result);
        return result;
    }

    /**
     * Cleans up merged entities that have exceeded their retention period.
     */
    private long cleanupMergedEntities(RetentionPolicy policy, Instant now) {
        Instant cutoff = now.minus(policy.mergedEntityRetention());
        String cutoffStr = cutoff.toString();

        if (policy.softDeleteEnabled()) {
            return softDeleteMergedEntities(cutoffStr);
        } else {
            return hardDeleteMergedEntities(cutoffStr);
        }
    }

    private long softDeleteMergedEntities(String cutoffStr) {
        long totalDeleted = 0;
        long deleted;
        do {
            String query = """
                    MATCH (e:Entity)
                    WHERE e.status = 'MERGED'
                      AND e.updatedAt <= $cutoff
                      AND e.deletedAt IS NULL
                    WITH e LIMIT $batchSize
                    SET e.deletedAt = $now
                    RETURN count(e) as deleted
                    """;
            List<Map<String, Object>> results = connection.query(query, Map.of(
                    "cutoff", cutoffStr,
                    "batchSize", batchSize,
                    "now", Instant.now().toString()
            ));
            deleted = extractCount(results);
            totalDeleted += deleted;
            if (deleted > 0) {
                log.debug("retention.softDeletedMergedEntities batch={} total={}", deleted, totalDeleted);
            }
        } while (deleted >= batchSize);

        return totalDeleted;
    }

    private long hardDeleteMergedEntities(String cutoffStr) {
        long totalDeleted = 0;
        long deleted;
        do {
            // Delete relationships first, then the entity
            String query = """
                    MATCH (e:Entity)
                    WHERE e.status = 'MERGED'
                      AND e.updatedAt <= $cutoff
                    WITH e LIMIT $batchSize
                    OPTIONAL MATCH (e)-[r]-()
                    DELETE r, e
                    RETURN count(DISTINCT e) as deleted
                    """;
            List<Map<String, Object>> results = connection.query(query, Map.of(
                    "cutoff", cutoffStr,
                    "batchSize", batchSize
            ));
            deleted = extractCount(results);
            totalDeleted += deleted;
            if (deleted > 0) {
                log.debug("retention.hardDeletedMergedEntities batch={} total={}", deleted, totalDeleted);
            }
        } while (deleted >= batchSize);

        return totalDeleted;
    }

    /**
     * Cleans up audit entries that have exceeded their retention period.
     */
    private long cleanupAuditEntries(RetentionPolicy policy, Instant now) {
        Instant cutoff = now.minus(policy.auditEntryRetention());
        String cutoffStr = cutoff.toString();

        long totalDeleted = 0;
        long deleted;
        do {
            String query = """
                    MATCH (a:AuditEntry)
                    WHERE a.timestamp <= $cutoff
                    WITH a LIMIT $batchSize
                    DELETE a
                    RETURN count(a) as deleted
                    """;
            List<Map<String, Object>> results = connection.query(query, Map.of(
                    "cutoff", cutoffStr,
                    "batchSize", batchSize
            ));
            deleted = extractCount(results);
            totalDeleted += deleted;
            if (deleted > 0) {
                log.debug("retention.deletedAuditEntries batch={} total={}", deleted, totalDeleted);
            }
        } while (deleted >= batchSize);

        return totalDeleted;
    }

    /**
     * Cleans up completed review items that have exceeded their retention period.
     */
    private long cleanupReviewItems(RetentionPolicy policy, Instant now) {
        Instant cutoff = now.minus(policy.reviewItemRetention());
        String cutoffStr = cutoff.toString();

        long totalDeleted = 0;
        long deleted;
        do {
            String query = """
                    MATCH (ri:ReviewItem)
                    WHERE ri.status IN ['APPROVED', 'REJECTED']
                      AND ri.reviewedAt <= $cutoff
                    WITH ri LIMIT $batchSize
                    DELETE ri
                    RETURN count(ri) as deleted
                    """;
            List<Map<String, Object>> results = connection.query(query, Map.of(
                    "cutoff", cutoffStr,
                    "batchSize", batchSize
            ));
            deleted = extractCount(results);
            totalDeleted += deleted;
            if (deleted > 0) {
                log.debug("retention.deletedReviewItems batch={} total={}", deleted, totalDeleted);
            }
        } while (deleted >= batchSize);

        return totalDeleted;
    }

    /**
     * Soft-deletes an entity by setting the deletedAt timestamp.
     * The entity will be filtered from queries but can be restored within a grace period.
     *
     * @param entityId the entity to soft-delete
     */
    public void softDelete(String entityId) {
        String query = """
                MATCH (e:Entity {id: $id})
                SET e.deletedAt = $now
                """;
        connection.execute(query, Map.of(
                "id", entityId,
                "now", Instant.now().toString()
        ));
        log.info("retention.softDeleted entityId={}", entityId);
    }

    /**
     * Restores a soft-deleted entity by clearing the deletedAt timestamp.
     *
     * @param entityId the entity to restore
     */
    public void restore(String entityId) {
        String query = """
                MATCH (e:Entity {id: $id})
                WHERE e.deletedAt IS NOT NULL
                REMOVE e.deletedAt
                """;
        connection.execute(query, Map.of("id", entityId));
        log.info("retention.restored entityId={}", entityId);
    }

    /**
     * Counts the number of soft-deleted entities.
     */
    public long countSoftDeleted() {
        String query = """
                MATCH (e:Entity)
                WHERE e.deletedAt IS NOT NULL
                RETURN count(e) as total
                """;
        List<Map<String, Object>> results = connection.query(query);
        return extractCount(results);
    }

    /**
     * Permanently removes all soft-deleted entities that have been deleted
     * longer than the specified grace period.
     *
     * @param gracePeriod the minimum time since soft-deletion before permanent removal
     * @return the number of entities permanently removed
     */
    public long purgeSoftDeleted(java.time.Duration gracePeriod) {
        Instant cutoff = Instant.now().minus(gracePeriod);
        String cutoffStr = cutoff.toString();

        long totalPurged = 0;
        long purged;
        do {
            String query = """
                    MATCH (e:Entity)
                    WHERE e.deletedAt IS NOT NULL
                      AND e.deletedAt <= $cutoff
                    WITH e LIMIT $batchSize
                    OPTIONAL MATCH (e)-[r]-()
                    DELETE r, e
                    RETURN count(DISTINCT e) as deleted
                    """;
            List<Map<String, Object>> results = connection.query(query, Map.of(
                    "cutoff", cutoffStr,
                    "batchSize", batchSize
            ));
            purged = extractCount(results);
            totalPurged += purged;
        } while (purged >= batchSize);

        log.info("retention.purgedSoftDeleted count={}", totalPurged);
        return totalPurged;
    }

    private long extractCount(List<Map<String, Object>> results) {
        if (results.isEmpty()) return 0;
        Object val = results.get(0).get("deleted");
        if (val == null) {
            val = results.get(0).get("total");
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        return 0;
    }
}
