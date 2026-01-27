package com.entity.resolution.retention;

/**
 * Result of a retention cleanup operation.
 *
 * @param mergedEntitiesDeleted number of merged entities deleted (or soft-deleted)
 * @param auditEntriesDeleted   number of audit entries deleted
 * @param reviewItemsDeleted    number of completed review items deleted
 */
public record RetentionResult(
        long mergedEntitiesDeleted,
        long auditEntriesDeleted,
        long reviewItemsDeleted
) {
    /**
     * Returns the total number of records affected.
     */
    public long totalDeleted() {
        return mergedEntitiesDeleted + auditEntriesDeleted + reviewItemsDeleted;
    }

    /**
     * Returns an empty result (no deletions).
     */
    public static RetentionResult empty() {
        return new RetentionResult(0, 0, 0);
    }

    @Override
    public String toString() {
        return "RetentionResult{merged=" + mergedEntitiesDeleted +
                ", audit=" + auditEntriesDeleted +
                ", reviews=" + reviewItemsDeleted +
                ", total=" + totalDeleted() + '}';
    }
}
