package com.entity.resolution.api;

import com.entity.resolution.core.model.EntityType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Async interface for entity resolution using Java 21 virtual threads.
 * All methods return {@link CompletableFuture} for non-blocking operation.
 */
public interface AsyncEntityResolver extends AutoCloseable {

    /**
     * Asynchronously resolves an entity name to a canonical entity.
     */
    CompletableFuture<EntityResolutionResult> resolveAsync(String entityName, EntityType entityType);

    /**
     * Asynchronously resolves an entity name with custom options.
     */
    CompletableFuture<EntityResolutionResult> resolveAsync(String entityName, EntityType entityType,
                                                            ResolutionOptions options);

    /**
     * Resolves a batch of requests in parallel.
     */
    CompletableFuture<List<EntityResolutionResult>> resolveBatchAsync(List<ResolutionRequest> requests);

    /**
     * Resolves a batch of requests with bounded concurrency.
     *
     * @param requests       the resolution requests
     * @param maxConcurrency maximum number of concurrent resolutions
     */
    CompletableFuture<List<EntityResolutionResult>> resolveBatchAsync(List<ResolutionRequest> requests,
                                                                       int maxConcurrency);

    @Override
    void close();
}
