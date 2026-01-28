package com.entity.resolution.rules;

import com.entity.resolution.core.model.EntityType;

import java.util.List;

/**
 * Normalization rules for metadata entity types (DATASET, TABLE, SCHEMA, DOMAIN, SERVICE, API, DOCUMENT).
 * Handles version suffixes, date suffixes, environment suffixes, and schema prefixes
 * commonly found in data catalog, metadata registry, and service inventory contexts.
 */
public final class MetadataNormalizationRules {

    private MetadataNormalizationRules() {
        // Utility class
    }

    /**
     * Creates a NormalizationEngine with only metadata-specific rules.
     */
    public static NormalizationEngine createMetadataEngine() {
        NormalizationEngine engine = new NormalizationEngine();
        engine.addRules(getDatasetTableRules());
        engine.addRules(getSchemaRules());
        engine.addRules(getServiceApiRules());
        engine.addRules(getMetadataCommonRules());
        return engine;
    }

    /**
     * Creates a NormalizationEngine with both default (company/person) and metadata rules.
     */
    public static NormalizationEngine createFullEngine() {
        NormalizationEngine engine = DefaultNormalizationRules.createDefaultEngine();
        engine.addRules(getDatasetTableRules());
        engine.addRules(getSchemaRules());
        engine.addRules(getServiceApiRules());
        engine.addRules(getMetadataCommonRules());
        return engine;
    }

    /**
     * Gets rules for DATASET and TABLE entity types.
     * Strips version suffixes (_v1, _v2, _final, _draft) and date suffixes (_20240101, _2024).
     */
    public static List<NormalizationRule> getDatasetTableRules() {
        return List.of(
                // Version suffixes: _v1, _v2, _v10, etc.
                NormalizationRule.builder()
                        .name("metadata-version-suffix")
                        .pattern("[_\\-]v\\d+$")
                        .replacement("")
                        .applicableTypes(EntityType.DATASET, EntityType.TABLE)
                        .priority(10)
                        .build(),

                // Status suffixes: _final, _draft
                NormalizationRule.builder()
                        .name("metadata-final-suffix")
                        .pattern("[_\\-](final|draft)$")
                        .replacement("")
                        .applicableTypes(EntityType.DATASET, EntityType.TABLE)
                        .priority(10)
                        .build(),

                // Environment suffixes: _prod, _dev, _staging, _test
                NormalizationRule.builder()
                        .name("metadata-dataset-env-suffix")
                        .pattern("[_\\-](prod|dev|staging|test)$")
                        .replacement("")
                        .applicableTypes(EntityType.DATASET, EntityType.TABLE)
                        .priority(10)
                        .build(),

                // Date suffixes: _20240101, _2024_01_01, _2024
                NormalizationRule.builder()
                        .name("metadata-date-suffix-full")
                        .pattern("[_\\-]\\d{4}[_\\-]?\\d{2}[_\\-]?\\d{2}$")
                        .replacement("")
                        .applicableTypes(EntityType.DATASET, EntityType.TABLE)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("metadata-date-suffix-year")
                        .pattern("[_\\-]\\d{4}$")
                        .replacement("")
                        .applicableTypes(EntityType.DATASET, EntityType.TABLE)
                        .priority(15)
                        .build()
        );
    }

    /**
     * Gets rules for SCHEMA entity types.
     * Strips common schema prefixes (dbo., public., raw., staging.).
     */
    public static List<NormalizationRule> getSchemaRules() {
        return List.of(
                NormalizationRule.builder()
                        .name("schema-dbo-prefix")
                        .pattern("^dbo\\.")
                        .replacement("")
                        .applicableTypes(EntityType.SCHEMA)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("schema-public-prefix")
                        .pattern("^public\\.")
                        .replacement("")
                        .applicableTypes(EntityType.SCHEMA)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("schema-raw-prefix")
                        .pattern("^raw\\.")
                        .replacement("")
                        .applicableTypes(EntityType.SCHEMA)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("schema-staging-prefix")
                        .pattern("^staging\\.")
                        .replacement("")
                        .applicableTypes(EntityType.SCHEMA)
                        .priority(10)
                        .build()
        );
    }

    /**
     * Gets rules for SERVICE and API entity types.
     * Strips environment suffixes (-dev, -staging, -prod, -production, -uat, -qa).
     */
    public static List<NormalizationRule> getServiceApiRules() {
        return List.of(
                NormalizationRule.builder()
                        .name("service-env-suffix")
                        .pattern("[_\\-](dev|staging|prod|production|uat|qa)$")
                        .replacement("")
                        .applicableTypes(EntityType.SERVICE, EntityType.API)
                        .priority(10)
                        .build()
        );
    }

    /**
     * Gets common rules for all metadata entity types.
     * Normalizes underscores and hyphens to spaces.
     */
    public static List<NormalizationRule> getMetadataCommonRules() {
        return List.of(
                // Normalize underscores to spaces
                NormalizationRule.builder()
                        .name("metadata-underscores-to-spaces")
                        .pattern("_")
                        .replacement(" ")
                        .applicableTypes(EntityType.DATASET, EntityType.TABLE, EntityType.SCHEMA,
                                EntityType.DOMAIN, EntityType.SERVICE, EntityType.API, EntityType.DOCUMENT)
                        .priority(50)
                        .build(),

                // Normalize hyphens to spaces
                NormalizationRule.builder()
                        .name("metadata-hyphens-to-spaces")
                        .pattern("-")
                        .replacement(" ")
                        .applicableTypes(EntityType.DATASET, EntityType.TABLE, EntityType.SCHEMA,
                                EntityType.DOMAIN, EntityType.SERVICE, EntityType.API, EntityType.DOCUMENT)
                        .priority(50)
                        .build()
        );
    }
}
