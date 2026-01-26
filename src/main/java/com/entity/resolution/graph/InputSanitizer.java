package com.entity.resolution.graph;

/**
 * Input validation utility for entity resolution operations.
 * Provides validation and sanitization methods to prevent injection attacks
 * and enforce data quality constraints.
 */
public final class InputSanitizer {

    /** Maximum allowed length for entity names. */
    public static final int MAX_ENTITY_NAME_LENGTH = 1000;

    /** Maximum allowed length for Cypher string values. */
    public static final int MAX_CYPHER_VALUE_LENGTH = 4000;

    private InputSanitizer() {
        // utility class
    }

    /**
     * Validates an entity name for resolution.
     * Rejects null, blank, overly long, or control-character-containing names.
     *
     * @param name the entity name to validate
     * @throws IllegalArgumentException if the name is invalid
     */
    public static void validateEntityName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Entity name must not be null or blank");
        }
        if (name.length() > MAX_ENTITY_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "Entity name exceeds maximum length of " + MAX_ENTITY_NAME_LENGTH +
                            " characters (was " + name.length() + ")");
        }
        if (containsControlCharacters(name)) {
            throw new IllegalArgumentException("Entity name must not contain control characters");
        }
    }

    /**
     * Validates a relationship type string.
     * Only alphanumeric characters and underscores are allowed.
     *
     * @param relationshipType the relationship type to validate
     * @throws IllegalArgumentException if the type is invalid
     */
    public static void validateRelationshipType(String relationshipType) {
        if (relationshipType == null || relationshipType.isBlank()) {
            throw new IllegalArgumentException("Relationship type must not be null or blank");
        }
        if (!relationshipType.matches("^[A-Za-z0-9_]+$")) {
            throw new IllegalArgumentException(
                    "Relationship type must contain only alphanumeric characters and underscores, " +
                            "got: '" + relationshipType + "'");
        }
    }

    /**
     * Validates a string value for safe use in Cypher queries.
     * Enforces maximum length to prevent oversized payloads.
     *
     * @param value the string value to sanitize
     * @throws IllegalArgumentException if the value exceeds the maximum length
     */
    public static void sanitizeForCypher(String value) {
        if (value != null && value.length() > MAX_CYPHER_VALUE_LENGTH) {
            throw new IllegalArgumentException(
                    "Value exceeds maximum Cypher string length of " + MAX_CYPHER_VALUE_LENGTH +
                            " characters (was " + value.length() + ")");
        }
    }

    /**
     * Checks whether a string contains ASCII control characters (0x00-0x1F, 0x7F),
     * excluding common whitespace characters (tab, newline, carriage return).
     */
    private static boolean containsControlCharacters(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
            if (c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
