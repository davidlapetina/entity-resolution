package com.entity.resolution.core.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class EntityTypeTest {

    @Test
    void shouldContainAllMetadataTypes() {
        assertNotNull(EntityType.valueOf("DATASET"));
        assertNotNull(EntityType.valueOf("TABLE"));
        assertNotNull(EntityType.valueOf("SCHEMA"));
        assertNotNull(EntityType.valueOf("DOMAIN"));
        assertNotNull(EntityType.valueOf("SERVICE"));
        assertNotNull(EntityType.valueOf("API"));
        assertNotNull(EntityType.valueOf("DOCUMENT"));
    }

    @Test
    void shouldContainOriginalTypes() {
        assertNotNull(EntityType.valueOf("COMPANY"));
        assertNotNull(EntityType.valueOf("PERSON"));
        assertNotNull(EntityType.valueOf("ORGANIZATION"));
        assertNotNull(EntityType.valueOf("PRODUCT"));
        assertNotNull(EntityType.valueOf("LOCATION"));
    }

    @Test
    void shouldHaveTwelveValues() {
        assertEquals(12, EntityType.values().length);
    }

    @ParameterizedTest
    @EnumSource(EntityType.class)
    void allTypesShouldHaveLabel(EntityType type) {
        assertNotNull(type.getLabel());
        assertFalse(type.getLabel().isBlank());
    }

    @Test
    void metadataTypesHaveCorrectLabels() {
        assertEquals("Dataset", EntityType.DATASET.getLabel());
        assertEquals("Table", EntityType.TABLE.getLabel());
        assertEquals("Schema", EntityType.SCHEMA.getLabel());
        assertEquals("Domain", EntityType.DOMAIN.getLabel());
        assertEquals("Service", EntityType.SERVICE.getLabel());
        assertEquals("Api", EntityType.API.getLabel());
        assertEquals("Document", EntityType.DOCUMENT.getLabel());
    }
}
