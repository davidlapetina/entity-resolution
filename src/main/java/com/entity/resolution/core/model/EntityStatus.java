package com.entity.resolution.core.model;

/**
 * Status of an entity in the resolution system.
 * Per PRD section 9, nodes are never physically deleted.
 */
public enum EntityStatus {
    /**
     * Active entity that has not been merged into another.
     */
    ACTIVE,

    /**
     * Entity has been merged into a canonical entity.
     * The entity is preserved for audit trail but is no longer the primary reference.
     */
    MERGED
}
