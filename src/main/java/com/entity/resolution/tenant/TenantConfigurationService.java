package com.entity.resolution.tenant;

import com.entity.resolution.api.ResolutionOptions;
import com.entity.resolution.rules.NormalizationRule;

import java.util.List;

/**
 * Service for managing per-tenant configuration.
 * Allows different resolution thresholds and normalization rules per tenant.
 */
public interface TenantConfigurationService {

    /**
     * Gets the resolution options for a specific tenant.
     * Returns default options if no tenant-specific configuration exists.
     *
     * @param tenantId the tenant identifier
     * @return the resolution options for the tenant
     */
    ResolutionOptions getOptionsForTenant(String tenantId);

    /**
     * Gets custom normalization rules for a specific tenant.
     * Returns an empty list if no tenant-specific rules exist.
     *
     * @param tenantId the tenant identifier
     * @return the normalization rules for the tenant
     */
    List<NormalizationRule> getRulesForTenant(String tenantId);

    /**
     * Sets resolution options for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @param options  the resolution options to set
     */
    void setOptionsForTenant(String tenantId, ResolutionOptions options);

    /**
     * Sets normalization rules for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @param rules    the normalization rules to set
     */
    void setRulesForTenant(String tenantId, List<NormalizationRule> rules);
}
