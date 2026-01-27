package com.entity.resolution.bulk;

import java.util.List;

/**
 * Result of a bulk import operation.
 *
 * @param totalRecords   total number of records in the input
 * @param entitiesCreated  number of new entities created
 * @param entitiesMatched  number of entities matched to existing
 * @param entitiesMerged   number of entities that were auto-merged
 * @param reviewsCreated   number of review items created for uncertain matches
 * @param errors         list of error messages for failed records
 */
public record ImportResult(
        long totalRecords,
        long entitiesCreated,
        long entitiesMatched,
        long entitiesMerged,
        long reviewsCreated,
        List<ImportError> errors
) {
    public ImportResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public long successCount() {
        return entitiesCreated + entitiesMatched + entitiesMerged;
    }

    public long errorCount() {
        return errors.size();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Represents an error that occurred during import of a specific record.
     *
     * @param lineNumber the line number in the input (1-based)
     * @param inputName  the entity name that failed
     * @param message    the error message
     */
    public record ImportError(long lineNumber, String inputName, String message) {}

    @Override
    public String toString() {
        return "ImportResult{total=" + totalRecords +
                ", created=" + entitiesCreated +
                ", matched=" + entitiesMatched +
                ", merged=" + entitiesMerged +
                ", reviews=" + reviewsCreated +
                ", errors=" + errors.size() + '}';
    }
}
