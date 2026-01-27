package com.entity.resolution.rest.security;

/**
 * Security roles for API access control.
 * Roles are hierarchical: ADMIN > WRITER > READER.
 */
public enum SecurityRole {

    /** Read-only access: entity lookups, synonyms, audit trail, relationships. */
    READER,

    /** Read + write access: resolve, batch, create relationships. */
    WRITER,

    /** Full access: all operations including review approve/reject. */
    ADMIN;

    /**
     * Returns true if this role has sufficient privilege for the required role.
     */
    public boolean hasPermission(SecurityRole required) {
        return this.ordinal() >= required.ordinal();
    }

    /**
     * Parses a role from string, case-insensitive.
     *
     * @param value the role name
     * @return the matching SecurityRole
     * @throws IllegalArgumentException if the value doesn't match any role
     */
    public static SecurityRole fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Security role must not be null or blank");
        }
        return valueOf(value.trim().toUpperCase());
    }
}
