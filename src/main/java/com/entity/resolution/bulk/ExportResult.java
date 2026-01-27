package com.entity.resolution.bulk;

/**
 * Result of a bulk export operation.
 *
 * @param totalEntities total number of entities exported
 * @param totalSynonyms total number of synonyms exported
 * @param totalRelationships total number of relationships exported
 */
public record ExportResult(
        long totalEntities,
        long totalSynonyms,
        long totalRelationships
) {
    @Override
    public String toString() {
        return "ExportResult{entities=" + totalEntities +
                ", synonyms=" + totalSynonyms +
                ", relationships=" + totalRelationships + '}';
    }
}
