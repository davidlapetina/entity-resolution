package com.entity.resolution.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InputSanitizer validation utility.
 */
class InputSanitizerTest {

    // ========== validateEntityName ==========

    @Test
    void validateEntityName_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateEntityName(null));
    }

    @Test
    void validateEntityName_rejectsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateEntityName(""));
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateEntityName("   "));
    }

    @Test
    void validateEntityName_rejectsOverMaxLength() {
        String longName = "A".repeat(InputSanitizer.MAX_ENTITY_NAME_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateEntityName(longName));
    }

    @Test
    void validateEntityName_acceptsMaxLength() {
        String maxName = "A".repeat(InputSanitizer.MAX_ENTITY_NAME_LENGTH);
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName(maxName));
    }

    @Test
    void validateEntityName_rejectsControlCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateEntityName("Company\u0000Name"));
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateEntityName("Company\u0001Name"));
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateEntityName("Company\u007FName"));
    }

    @Test
    void validateEntityName_acceptsNormalCharacters() {
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName("Acme Corp."));
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName("Microsoft Corporation"));
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName("Société Générale"));
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName("Big Blue (IBM)"));
    }

    @Test
    void validateEntityName_acceptsWhitespace() {
        // Tab, newline, carriage return are allowed
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName("Company\tName"));
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName("Company\nName"));
        assertDoesNotThrow(() -> InputSanitizer.validateEntityName("Company\rName"));
    }

    // ========== validateRelationshipType ==========

    @Test
    void validateRelationshipType_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateRelationshipType(null));
    }

    @Test
    void validateRelationshipType_rejectsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateRelationshipType(""));
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateRelationshipType("   "));
    }

    @Test
    void validateRelationshipType_rejectsSpecialCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateRelationshipType("HAS-CEO"));
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateRelationshipType("HAS CEO"));
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateRelationshipType("type; DROP"));
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.validateRelationshipType("type'injection"));
    }

    @Test
    void validateRelationshipType_acceptsValidTypes() {
        assertDoesNotThrow(() -> InputSanitizer.validateRelationshipType("PARTNER"));
        assertDoesNotThrow(() -> InputSanitizer.validateRelationshipType("HAS_CEO"));
        assertDoesNotThrow(() -> InputSanitizer.validateRelationshipType("SUBSIDIARY_OF"));
        assertDoesNotThrow(() -> InputSanitizer.validateRelationshipType("relType123"));
    }

    // ========== sanitizeForCypher ==========

    @Test
    void sanitizeForCypher_acceptsNull() {
        assertDoesNotThrow(() -> InputSanitizer.sanitizeForCypher(null));
    }

    @Test
    void sanitizeForCypher_acceptsNormalLength() {
        assertDoesNotThrow(() -> InputSanitizer.sanitizeForCypher("normal string"));
    }

    @Test
    void sanitizeForCypher_acceptsMaxLength() {
        String maxValue = "A".repeat(InputSanitizer.MAX_CYPHER_VALUE_LENGTH);
        assertDoesNotThrow(() -> InputSanitizer.sanitizeForCypher(maxValue));
    }

    @Test
    void sanitizeForCypher_rejectsOverMaxLength() {
        String longValue = "A".repeat(InputSanitizer.MAX_CYPHER_VALUE_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> InputSanitizer.sanitizeForCypher(longValue));
    }
}
