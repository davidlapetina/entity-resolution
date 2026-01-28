package com.entity.resolution.rules;

import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class MetadataNormalizationRulesTest {

    @Test
    void createMetadataEngine_shouldReturnNonNullEngine() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertNotNull(engine);
        assertFalse(engine.getRules().isEmpty());
    }

    @Test
    void createFullEngine_shouldIncludeBothDefaultAndMetadataRules() {
        NormalizationEngine fullEngine = MetadataNormalizationRules.createFullEngine();
        NormalizationEngine defaultEngine = DefaultNormalizationRules.createDefaultEngine();
        NormalizationEngine metadataEngine = MetadataNormalizationRules.createMetadataEngine();

        // Full engine should have more rules than either individually
        assertTrue(fullEngine.getRules().size() > defaultEngine.getRules().size());
        assertTrue(fullEngine.getRules().size() > metadataEngine.getRules().size());
    }

    // === DATASET / TABLE rules ===

    @ParameterizedTest
    @CsvSource({
            "sales_data_v1, sales data",
            "sales_data_v2, sales data",
            "sales_data_v10, sales data",
            "report-v3, report"
    })
    void shouldStripVersionSuffixes(String input, String expected) {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertEquals(expected, engine.normalize(input, EntityType.DATASET));
    }

    @ParameterizedTest
    @CsvSource({
            "sales_data_final, sales data",
            "sales_data_draft, sales data",
            "report-final, report",
            "report-draft, report"
    })
    void shouldStripStatusSuffixes(String input, String expected) {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertEquals(expected, engine.normalize(input, EntityType.DATASET));
    }

    @ParameterizedTest
    @CsvSource({
            "sales_data_prod, sales data",
            "sales_data_dev, sales data",
            "sales_data_staging, sales data",
            "sales_data_test, sales data"
    })
    void shouldStripDatasetEnvSuffixes(String input, String expected) {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertEquals(expected, engine.normalize(input, EntityType.DATASET));
    }

    @ParameterizedTest
    @CsvSource({
            "sales_data_20240101, sales data",
            "sales_data_2024, sales data",
            "report-20231215, report"
    })
    void shouldStripDateSuffixes(String input, String expected) {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertEquals(expected, engine.normalize(input, EntityType.DATASET));
    }

    @Test
    void shouldNormalizeSameDatasetVariants() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        String base = engine.normalize("sales_data", EntityType.DATASET);
        String v1 = engine.normalize("sales_data_v1", EntityType.DATASET);
        String v2 = engine.normalize("sales_data_v2", EntityType.DATASET);
        String finalVersion = engine.normalize("sales_data_final", EntityType.DATASET);
        String dated = engine.normalize("sales_data_2024", EntityType.DATASET);

        assertEquals(base, v1);
        assertEquals(base, v2);
        assertEquals(base, finalVersion);
        assertEquals(base, dated);
    }

    // === TABLE rules ===

    @Test
    void shouldApplyRulesToTableType() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        String base = engine.normalize("user_accounts", EntityType.TABLE);
        String versioned = engine.normalize("user_accounts_v3", EntityType.TABLE);
        assertEquals(base, versioned);
    }

    // === SCHEMA rules ===

    @ParameterizedTest
    @CsvSource({
            "dbo.customers, customers",
            "public.orders, orders",
            "raw.events, events",
            "staging.metrics, metrics"
    })
    void shouldStripSchemaPrefixes(String input, String expected) {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertEquals(expected, engine.normalize(input, EntityType.SCHEMA));
    }

    @Test
    void shouldNotStripSchemaPrefixesForNonSchemaTypes() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        // For DATASET type, "dbo.customers" should NOT have dbo. stripped (rule is SCHEMA-only)
        String result = engine.normalize("dbo.customers", EntityType.DATASET);
        // The dot will be converted by common rules, but "dbo" should remain
        assertTrue(result.contains("dbo"));
    }

    // === SERVICE / API rules ===

    @ParameterizedTest
    @CsvSource({
            "payment-service-dev, payment service",
            "payment-service-staging, payment service",
            "payment-service-prod, payment service",
            "payment-service-production, payment service",
            "payment-service-uat, payment service",
            "payment-service-qa, payment service"
    })
    void shouldStripServiceEnvSuffixes(String input, String expected) {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertEquals(expected, engine.normalize(input, EntityType.SERVICE));
    }

    @Test
    void shouldNormalizeSameServiceVariants() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        String dev = engine.normalize("user-api-dev", EntityType.API);
        String prod = engine.normalize("user-api-prod", EntityType.API);
        String staging = engine.normalize("user-api-staging", EntityType.API);
        assertEquals(dev, prod);
        assertEquals(dev, staging);
    }

    // === Metadata common rules ===

    @Test
    void shouldNormalizeUnderscoresAndHyphensToSpaces() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        assertEquals("customer data", engine.normalize("customer_data", EntityType.DATASET));
        assertEquals("customer data", engine.normalize("customer-data", EntityType.DATASET));
    }

    @Test
    void shouldHandleDocumentType() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        String result = engine.normalize("architecture_design_doc", EntityType.DOCUMENT);
        assertEquals("architecture design doc", result);
    }

    @Test
    void shouldHandleDomainType() {
        NormalizationEngine engine = MetadataNormalizationRules.createMetadataEngine();
        String result = engine.normalize("customer-management", EntityType.DOMAIN);
        assertEquals("customer management", result);
    }

    // === Full engine combines both rule sets ===

    @Test
    void fullEngine_shouldStillNormalizeCompanies() {
        NormalizationEngine engine = MetadataNormalizationRules.createFullEngine();
        String result = engine.normalize("Microsoft Corporation", EntityType.COMPANY);
        assertEquals("microsoft", result);
    }

    @Test
    void fullEngine_shouldAlsoNormalizeMetadata() {
        NormalizationEngine engine = MetadataNormalizationRules.createFullEngine();
        String result = engine.normalize("sales_data_v2", EntityType.DATASET);
        assertEquals("sales data", result);
    }

    // === Rule list coverage ===

    @Test
    void getDatasetTableRules_shouldReturnNonEmpty() {
        assertFalse(MetadataNormalizationRules.getDatasetTableRules().isEmpty());
    }

    @Test
    void getSchemaRules_shouldReturnNonEmpty() {
        assertFalse(MetadataNormalizationRules.getSchemaRules().isEmpty());
    }

    @Test
    void getServiceApiRules_shouldReturnNonEmpty() {
        assertFalse(MetadataNormalizationRules.getServiceApiRules().isEmpty());
    }

    @Test
    void getMetadataCommonRules_shouldReturnNonEmpty() {
        assertFalse(MetadataNormalizationRules.getMetadataCommonRules().isEmpty());
    }
}
