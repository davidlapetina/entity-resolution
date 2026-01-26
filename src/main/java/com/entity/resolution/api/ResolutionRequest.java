package com.entity.resolution.api;

import com.entity.resolution.core.model.EntityType;

/**
 * A batch resolution request.
 *
 * @param entityName the entity name to resolve
 * @param entityType the entity type
 * @param options    optional resolution options (null for defaults)
 */
public record ResolutionRequest(String entityName, EntityType entityType, ResolutionOptions options) {

    /**
     * Creates a request with default options.
     */
    public static ResolutionRequest of(String entityName, EntityType entityType) {
        return new ResolutionRequest(entityName, entityType, null);
    }

    /**
     * Creates a request with custom options.
     */
    public static ResolutionRequest of(String entityName, EntityType entityType, ResolutionOptions options) {
        return new ResolutionRequest(entityName, entityType, options);
    }
}
