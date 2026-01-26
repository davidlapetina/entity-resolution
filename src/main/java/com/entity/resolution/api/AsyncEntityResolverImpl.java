package com.entity.resolution.api;

import com.entity.resolution.core.model.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Virtual thread-based implementation of {@link AsyncEntityResolver}.
 * Uses Java 21 virtual threads for lightweight async execution.
 */
public class AsyncEntityResolverImpl implements AsyncEntityResolver {
    private static final Logger log = LoggerFactory.getLogger(AsyncEntityResolverImpl.class);

    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    private final EntityResolver resolver;
    private final ResolutionOptions defaultOptions;
    private final ExecutorService executor;
    private final long timeoutMs;

    public AsyncEntityResolverImpl(EntityResolver resolver, ResolutionOptions defaultOptions) {
        this(resolver, defaultOptions, defaultOptions.getAsyncTimeoutMs());
    }

    public AsyncEntityResolverImpl(EntityResolver resolver, ResolutionOptions defaultOptions, long timeoutMs) {
        this.resolver = resolver;
        this.defaultOptions = defaultOptions;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.timeoutMs = timeoutMs;
    }

    @Override
    public CompletableFuture<EntityResolutionResult> resolveAsync(String entityName, EntityType entityType) {
        return resolveAsync(entityName, entityType, defaultOptions);
    }

    @Override
    public CompletableFuture<EntityResolutionResult> resolveAsync(String entityName, EntityType entityType,
                                                                    ResolutionOptions options) {
        return CompletableFuture.supplyAsync(
                () -> resolver.resolve(entityName, entityType, options),
                executor
        ).orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public CompletableFuture<List<EntityResolutionResult>> resolveBatchAsync(List<ResolutionRequest> requests) {
        List<CompletableFuture<EntityResolutionResult>> futures = requests.stream()
                .map(req -> {
                    ResolutionOptions opts = req.options() != null ? req.options() : defaultOptions;
                    return resolveAsync(req.entityName(), req.entityType(), opts);
                })
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    @Override
    public CompletableFuture<List<EntityResolutionResult>> resolveBatchAsync(List<ResolutionRequest> requests,
                                                                               int maxConcurrency) {
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be > 0");
        }

        Semaphore semaphore = new Semaphore(maxConcurrency);

        List<CompletableFuture<EntityResolutionResult>> futures = requests.stream()
                .map(req -> CompletableFuture.supplyAsync(() -> {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CompletionException(e);
                    }
                    try {
                        ResolutionOptions opts = req.options() != null ? req.options() : defaultOptions;
                        return resolver.resolve(req.entityName(), req.entityType(), opts);
                    } finally {
                        semaphore.release();
                    }
                }, executor).orTimeout(timeoutMs, TimeUnit.MILLISECONDS))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
