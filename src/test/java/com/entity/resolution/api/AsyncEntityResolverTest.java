package com.entity.resolution.api;

import com.entity.resolution.audit.AuditService;
import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.*;
import com.entity.resolution.merge.MergeEngine;
import com.entity.resolution.rules.DefaultNormalizationRules;
import com.entity.resolution.rules.NormalizationEngine;
import com.entity.resolution.similarity.CompositeSimilarityScorer;
import com.entity.resolution.similarity.SimilarityWeights;
import com.entity.resolution.llm.LLMEnricher;
import com.entity.resolution.llm.NoOpLLMProvider;
import com.entity.resolution.audit.MergeLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AsyncEntityResolverTest {

    private EntityResolver resolver;
    private GraphConnection mockConnection;

    @BeforeEach
    void setUp() {
        mockConnection = new MockGraphConnection();
        resolver = EntityResolver.builder()
                .graphConnection(mockConnection)
                .createIndexes(false)
                .build();
    }

    @Test
    @DisplayName("Should create async resolver via factory method")
    void testAsyncFactory() {
        AsyncEntityResolver async = resolver.async();
        assertNotNull(async);
        async.close();
    }

    @Test
    @DisplayName("Should resolve entity asynchronously")
    void testResolveAsync() throws Exception {
        AsyncEntityResolver async = resolver.async();

        CompletableFuture<EntityResolutionResult> future =
                async.resolveAsync("Test Company", EntityType.COMPANY);

        EntityResolutionResult result = future.get(10, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result.isNewEntity());
        assertEquals("Test Company", result.getCanonicalEntity().getCanonicalName());

        async.close();
    }

    @Test
    @DisplayName("Should resolve with custom options asynchronously")
    void testResolveAsyncWithOptions() throws Exception {
        AsyncEntityResolver async = resolver.async();
        ResolutionOptions options = ResolutionOptions.conservative();

        CompletableFuture<EntityResolutionResult> future =
                async.resolveAsync("Another Corp", EntityType.COMPANY, options);

        EntityResolutionResult result = future.get(10, TimeUnit.SECONDS);
        assertNotNull(result);
        assertTrue(result.isNewEntity());

        async.close();
    }

    @Test
    @DisplayName("Should resolve batch asynchronously")
    void testResolveBatchAsync() throws Exception {
        AsyncEntityResolver async = resolver.async();

        List<ResolutionRequest> requests = List.of(
                ResolutionRequest.of("Company A", EntityType.COMPANY),
                ResolutionRequest.of("Company B", EntityType.COMPANY),
                ResolutionRequest.of("Company C", EntityType.COMPANY)
        );

        CompletableFuture<List<EntityResolutionResult>> future = async.resolveBatchAsync(requests);

        List<EntityResolutionResult> results = future.get(10, TimeUnit.SECONDS);
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(EntityResolutionResult::isNewEntity));

        async.close();
    }

    @Test
    @DisplayName("Should resolve batch with bounded concurrency")
    void testResolveBatchBounded() throws Exception {
        AsyncEntityResolver async = resolver.async();

        List<ResolutionRequest> requests = List.of(
                ResolutionRequest.of("Corp X", EntityType.COMPANY),
                ResolutionRequest.of("Corp Y", EntityType.COMPANY),
                ResolutionRequest.of("Corp Z", EntityType.COMPANY)
        );

        CompletableFuture<List<EntityResolutionResult>> future =
                async.resolveBatchAsync(requests, 2);

        List<EntityResolutionResult> results = future.get(10, TimeUnit.SECONDS);
        assertEquals(3, results.size());

        async.close();
    }

    @Test
    @DisplayName("Should reject invalid max concurrency")
    void testInvalidMaxConcurrency() {
        AsyncEntityResolver async = resolver.async();
        assertThrows(IllegalArgumentException.class,
                () -> async.resolveBatchAsync(List.of(), 0));
        async.close();
    }

    @Test
    @DisplayName("ResolutionRequest should create with defaults")
    void testResolutionRequestDefaults() {
        ResolutionRequest request = ResolutionRequest.of("Test", EntityType.COMPANY);
        assertEquals("Test", request.entityName());
        assertEquals(EntityType.COMPANY, request.entityType());
        assertNull(request.options());
    }

    @Test
    @DisplayName("ResolutionRequest should carry custom options")
    void testResolutionRequestWithOptions() {
        ResolutionOptions opts = ResolutionOptions.conservative();
        ResolutionRequest request = ResolutionRequest.of("Test", EntityType.PERSON, opts);
        assertEquals(opts, request.options());
    }

    @Test
    @DisplayName("Should close executor gracefully")
    void testCloseGracefully() {
        AsyncEntityResolver async = resolver.async();
        assertDoesNotThrow(async::close);
    }

    /**
     * Mock GraphConnection for testing without FalkorDB.
     */
    static class MockGraphConnection implements GraphConnection {
        @Override
        public void execute(String query, Map<String, Object> params) {
            // no-op
        }

        @Override
        public List<Map<String, Object>> query(String query, Map<String, Object> params) {
            return List.of();
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String getGraphName() {
            return "test-graph";
        }

        @Override
        public void createIndexes() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
