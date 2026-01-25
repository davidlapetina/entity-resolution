package com.entity.resolution.rules;

import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class NormalizationEngineTest {

    private NormalizationEngine engine;

    @BeforeEach
    void setUp() {
        engine = DefaultNormalizationRules.createDefaultEngine();
    }

    @Test
    @DisplayName("Should handle null and blank inputs")
    void testNullAndBlankInputs() {
        assertEquals("", engine.normalize(null));
        assertEquals("", engine.normalize(""));
        assertEquals("", engine.normalize("   "));
    }

    @ParameterizedTest
    @DisplayName("Should normalize company suffixes")
    @CsvSource({
            "Apple Inc.,apple",
            "Apple Inc,apple",
            "Apple Incorporated,apple",
            "Microsoft Corporation,microsoft",
            "Microsoft Corp,microsoft",
            "Microsoft Corp.,microsoft",
            "Google LLC,google",
            "Amazon.com Ltd,amazon com",
            "Amazon.com Limited,amazon com",
            "Tesla Co.,tesla",
            "Tesla Company,tesla"
    })
    void testCompanySuffixNormalization(String input, String expected) {
        String result = engine.normalize(input, EntityType.COMPANY);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @DisplayName("Should normalize international company suffixes")
    @CsvSource({
            "Volkswagen AG,volkswagen",
            "Siemens GmbH,siemens",
            "Royal Dutch Shell N.V.,royal dutch shell",
            "Heineken NV,heineken",
            "Unilever B.V.,unilever"
    })
    void testInternationalCompanySuffixes(String input, String expected) {
        String result = engine.normalize(input, EntityType.COMPANY);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Should remove 'The' prefix from company names")
    void testThePrefix() {
        assertEquals("coca cola", engine.normalize("The Coca Cola", EntityType.COMPANY));
        assertEquals("walt disney", engine.normalize("The Walt Disney", EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should normalize 'and' and ampersand")
    void testAndNormalization() {
        assertEquals("procter gamble", engine.normalize("Procter & Gamble", EntityType.COMPANY));
        assertEquals("procter gamble", engine.normalize("Procter and Gamble", EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should collapse multiple spaces")
    void testSpaceCollapsing() {
        assertEquals("big blue", engine.normalize("Big    Blue", EntityType.COMPANY));
        assertEquals("test company", engine.normalize("  Test   Company  ", EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should convert to lowercase")
    void testLowercasing() {
        assertEquals("ibm", engine.normalize("IBM", EntityType.COMPANY));
        assertEquals("international business machines",
                engine.normalize("International Business Machines", EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should check equivalence after normalization")
    void testAreEquivalent() {
        assertTrue(engine.areEquivalent("Apple Inc.", "Apple Incorporated", EntityType.COMPANY));
        assertTrue(engine.areEquivalent("Microsoft Corp", "Microsoft Corporation", EntityType.COMPANY));
        assertFalse(engine.areEquivalent("Apple", "Microsoft", EntityType.COMPANY));
    }

    @Test
    @DisplayName("Should allow adding custom rules")
    void testCustomRules() {
        NormalizationEngine customEngine = new NormalizationEngine();
        customEngine.addRule(NormalizationRule.builder()
                .name("custom-test")
                .pattern("TEST")
                .replacement("REPLACED")
                .priority(1)
                .build());

        assertEquals("replaced company", customEngine.normalize("TEST Company"));
    }

    @Test
    @DisplayName("Should apply rules by priority order")
    void testRulePriority() {
        NormalizationEngine customEngine = new NormalizationEngine();

        // Lower priority number = applied first
        customEngine.addRule(NormalizationRule.builder()
                .name("rule-high-priority")
                .pattern("FIRST")
                .replacement("SECOND")
                .priority(1)
                .build());

        customEngine.addRule(NormalizationRule.builder()
                .name("rule-low-priority")
                .pattern("SECOND")
                .replacement("THIRD")
                .priority(2)
                .build());

        // FIRST -> SECOND (priority 1) -> THIRD (priority 2)
        assertEquals("third", customEngine.normalize("FIRST"));
    }

    @Test
    @DisplayName("Should support entity type scoped rules")
    void testEntityTypeScoping() {
        NormalizationEngine customEngine = new NormalizationEngine();
        customEngine.addRules(DefaultNormalizationRules.getCompanyRules());

        // Company suffix should be removed for COMPANY type
        assertEquals("test", customEngine.normalize("Test Inc.", EntityType.COMPANY));

        // But not for other types (rule doesn't apply, and no common rules to strip the period)
        assertEquals("test inc.", customEngine.normalize("Test Inc.", EntityType.PERSON));
    }
}
