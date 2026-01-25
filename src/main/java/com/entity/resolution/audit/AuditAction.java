package com.entity.resolution.audit;

/**
 * Types of auditable actions in the entity resolution system.
 */
public enum AuditAction {
    ENTITY_CREATED,
    ENTITY_UPDATED,
    ENTITY_MERGED,
    SYNONYM_CREATED,
    SYNONYM_UPDATED,
    DUPLICATE_CREATED,
    RELATIONSHIP_CREATED,
    RELATIONSHIPS_MIGRATED,
    MATCH_ATTEMPTED,
    LLM_ENRICHMENT_REQUESTED,
    LLM_ENRICHMENT_COMPLETED,
    MANUAL_REVIEW_REQUESTED,
    MANUAL_REVIEW_COMPLETED
}
