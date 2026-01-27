package com.entity.resolution.tenant;

import com.entity.resolution.api.ResolutionOptions;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.rules.NormalizationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryTenantConfigurationServiceTest {

    private InMemoryTenantConfigurationService configService;

    @BeforeEach
    void setUp() {
        configService = new InMemoryTenantConfigurationService();
    }

    @Test
    @DisplayName("Should return default options for unknown tenant")
    void testDefaultOptions() {
        ResolutionOptions options = configService.getOptionsForTenant("unknown");
        assertNotNull(options);
        assertEquals(0.92, options.getAutoMergeThreshold());
    }

    @Test
    @DisplayName("Should return custom options for configured tenant")
    void testCustomOptions() {
        ResolutionOptions custom = ResolutionOptions.builder()
                .autoMergeThreshold(0.98)
                .build();
        configService.setOptionsForTenant("tenant-1", custom);

        ResolutionOptions retrieved = configService.getOptionsForTenant("tenant-1");
        assertEquals(0.98, retrieved.getAutoMergeThreshold());
    }

    @Test
    @DisplayName("Should return empty rules for unknown tenant")
    void testDefaultRules() {
        List<NormalizationRule> rules = configService.getRulesForTenant("unknown");
        assertTrue(rules.isEmpty());
    }

    @Test
    @DisplayName("Should return custom rules for configured tenant")
    void testCustomRules() {
        NormalizationRule rule = NormalizationRule.builder()
                .name("test-rule")
                .pattern("\\bTest\\b")
                .replacement("")
                .applicableTypes(EntityType.COMPANY)
                .priority(100)
                .build();
        configService.setRulesForTenant("tenant-1", List.of(rule));

        List<NormalizationRule> rules = configService.getRulesForTenant("tenant-1");
        assertEquals(1, rules.size());
        assertEquals("test-rule", rules.get(0).getName());
    }

    @Test
    @DisplayName("Different tenants should have independent configs")
    void testTenantIsolation() {
        configService.setOptionsForTenant("tenant-1", ResolutionOptions.builder()
                .autoMergeThreshold(0.95).build());
        configService.setOptionsForTenant("tenant-2", ResolutionOptions.builder()
                .autoMergeThreshold(0.85).build());

        assertEquals(0.95, configService.getOptionsForTenant("tenant-1").getAutoMergeThreshold());
        assertEquals(0.85, configService.getOptionsForTenant("tenant-2").getAutoMergeThreshold());
    }

    @Test
    @DisplayName("Should reject null tenant ID")
    void testNullTenantId() {
        assertThrows(IllegalArgumentException.class, () ->
                configService.setOptionsForTenant(null, ResolutionOptions.defaults()));
    }

    @Test
    @DisplayName("Should reject blank tenant ID")
    void testBlankTenantId() {
        assertThrows(IllegalArgumentException.class, () ->
                configService.setRulesForTenant("  ", List.of()));
    }
}
