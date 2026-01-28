package com.entity.resolution.core.model;

/**
 * Enumeration of entity types supported by the entity resolution system.
 * Extensible taxonomy starting with COMPANY.
 */
public enum EntityType {
    COMPANY("Company"),
    PERSON("Person"),
    ORGANIZATION("Organization"),
    PRODUCT("Product"),
    LOCATION("Location"),
    DATASET("Dataset"),
    TABLE("Table"),
    SCHEMA("Schema"),
    DOMAIN("Domain"),
    SERVICE("Service"),
    API("Api"),
    DOCUMENT("Document");

    private final String label;

    EntityType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
