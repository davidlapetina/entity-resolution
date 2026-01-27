package com.entity.resolution.tenant;

import com.entity.resolution.api.ResolutionOptions;
import com.entity.resolution.rules.NormalizationRule;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory implementation of {@link TenantConfigurationService}.
 * Stores per-tenant configuration in memory.
 */
public class InMemoryTenantConfigurationService implements TenantConfigurationService {

    private final ResolutionOptions defaultOptions;
    private final ConcurrentMap<String, ResolutionOptions> tenantOptions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<NormalizationRule>> tenantRules = new ConcurrentHashMap<>();

    public InMemoryTenantConfigurationService() {
        this(ResolutionOptions.defaults());
    }

    public InMemoryTenantConfigurationService(ResolutionOptions defaultOptions) {
        this.defaultOptions = defaultOptions;
    }

    @Override
    public ResolutionOptions getOptionsForTenant(String tenantId) {
        return tenantOptions.getOrDefault(tenantId, defaultOptions);
    }

    @Override
    public List<NormalizationRule> getRulesForTenant(String tenantId) {
        return tenantRules.getOrDefault(tenantId, List.of());
    }

    @Override
    public void setOptionsForTenant(String tenantId, ResolutionOptions options) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        tenantOptions.put(tenantId, options);
    }

    @Override
    public void setRulesForTenant(String tenantId, List<NormalizationRule> rules) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        tenantRules.put(tenantId, List.copyOf(rules));
    }
}
