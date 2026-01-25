package com.entity.resolution.rules;

import com.entity.resolution.core.model.EntityType;

import java.util.List;

/**
 * Default normalization rules as specified in the PRD.
 * Provides built-in rules for common entity name patterns.
 */
public final class DefaultNormalizationRules {

    private DefaultNormalizationRules() {
        // Utility class
    }

    /**
     * Creates a NormalizationEngine with all default rules.
     */
    public static NormalizationEngine createDefaultEngine() {
        NormalizationEngine engine = new NormalizationEngine();
        engine.addRules(getCompanyRules());
        engine.addRules(getCommonRules());
        return engine;
    }

    /**
     * Gets rules specific to company entities.
     */
    public static List<NormalizationRule> getCompanyRules() {
        return List.of(
                // Inc variations - priority 10 (highest for company suffixes)
                NormalizationRule.builder()
                        .name("company-inc")
                        .pattern(",?\\s*(Inc\\.?|Incorporated)$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // Ltd variations
                NormalizationRule.builder()
                        .name("company-ltd")
                        .pattern(",?\\s*(Ltd\\.?|Limited)$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // Corp variations
                NormalizationRule.builder()
                        .name("company-corp")
                        .pattern(",?\\s*(Corp\\.?|Corporation)$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // Co variations
                NormalizationRule.builder()
                        .name("company-co")
                        .pattern(",?\\s*(Co\\.?|Company)$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // SA (Sociedad Anónima / Société Anonyme)
                NormalizationRule.builder()
                        .name("company-sa")
                        .pattern(",?\\s*S\\.?A\\.?$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // LLC variations
                NormalizationRule.builder()
                        .name("company-llc")
                        .pattern(",?\\s*(LLC|L\\.L\\.C\\.)$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // PLC (Public Limited Company)
                NormalizationRule.builder()
                        .name("company-plc")
                        .pattern(",?\\s*(PLC|P\\.L\\.C\\.)$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // GmbH (German)
                NormalizationRule.builder()
                        .name("company-gmbh")
                        .pattern(",?\\s*GmbH$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // AG (German)
                NormalizationRule.builder()
                        .name("company-ag")
                        .pattern(",?\\s*AG$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // NV (Dutch)
                NormalizationRule.builder()
                        .name("company-nv")
                        .pattern(",?\\s*N\\.?V\\.?$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // BV (Dutch)
                NormalizationRule.builder()
                        .name("company-bv")
                        .pattern(",?\\s*B\\.?V\\.?$")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(10)
                        .build(),

                // The prefix
                NormalizationRule.builder()
                        .name("company-the")
                        .pattern("^The\\s+")
                        .replacement("")
                        .applicableTypes(EntityType.COMPANY)
                        .priority(20)
                        .build()
        );
    }

    /**
     * Gets common rules that apply to all entity types.
     */
    public static List<NormalizationRule> getCommonRules() {
        return List.of(
                // Remove special characters (but keep spaces and alphanumeric)
                NormalizationRule.builder()
                        .name("common-special-chars")
                        .pattern("[^a-zA-Z0-9\\s]")
                        .replacement(" ")
                        .priority(100)
                        .build(),

                // Collapse multiple spaces
                NormalizationRule.builder()
                        .name("common-collapse-spaces")
                        .pattern("\\s+")
                        .replacement(" ")
                        .priority(200)
                        .build(),

                // Common abbreviations - "and" to "&"
                NormalizationRule.builder()
                        .name("common-and")
                        .pattern("\\s+and\\s+")
                        .replacement(" ")
                        .priority(50)
                        .build(),

                // Common abbreviations - "&" to " "
                NormalizationRule.builder()
                        .name("common-ampersand")
                        .pattern("\\s*&\\s*")
                        .replacement(" ")
                        .priority(50)
                        .build()
        );
    }

    /**
     * Gets rules for person names.
     */
    public static List<NormalizationRule> getPersonRules() {
        return List.of(
                // Common titles
                NormalizationRule.builder()
                        .name("person-mr")
                        .pattern("^Mr\\.?\\s+")
                        .replacement("")
                        .applicableTypes(EntityType.PERSON)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("person-mrs")
                        .pattern("^Mrs\\.?\\s+")
                        .replacement("")
                        .applicableTypes(EntityType.PERSON)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("person-ms")
                        .pattern("^Ms\\.?\\s+")
                        .replacement("")
                        .applicableTypes(EntityType.PERSON)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("person-dr")
                        .pattern("^Dr\\.?\\s+")
                        .replacement("")
                        .applicableTypes(EntityType.PERSON)
                        .priority(10)
                        .build(),

                // Suffixes
                NormalizationRule.builder()
                        .name("person-jr")
                        .pattern(",?\\s+(Jr\\.?|Junior)$")
                        .replacement("")
                        .applicableTypes(EntityType.PERSON)
                        .priority(10)
                        .build(),

                NormalizationRule.builder()
                        .name("person-sr")
                        .pattern(",?\\s+(Sr\\.?|Senior)$")
                        .replacement("")
                        .applicableTypes(EntityType.PERSON)
                        .priority(10)
                        .build()
        );
    }
}
