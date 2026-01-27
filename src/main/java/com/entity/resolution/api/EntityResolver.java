package com.entity.resolution.api;

import com.entity.resolution.audit.AuditRepository;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.audit.MergeLedger;
import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.*;
import com.entity.resolution.health.*;
import com.entity.resolution.llm.LLMEnricher;
import com.entity.resolution.llm.LLMProvider;
import com.entity.resolution.llm.NoOpLLMProvider;
import com.entity.resolution.merge.MergeEngine;
import com.entity.resolution.merge.MergeResult;
import com.entity.resolution.metrics.MetricsService;
import com.entity.resolution.metrics.NoOpMetricsService;
import com.entity.resolution.review.InMemoryReviewQueue;
import com.entity.resolution.review.ReviewItem;
import com.entity.resolution.review.ReviewQueue;
import com.entity.resolution.review.ReviewService;
import com.entity.resolution.tracing.NoOpTracingService;
import com.entity.resolution.tracing.Span;
import com.entity.resolution.tracing.TracingService;
import com.entity.resolution.rules.DefaultNormalizationRules;
import com.entity.resolution.rules.NormalizationEngine;
import com.entity.resolution.similarity.BlockingKeyStrategy;
import com.entity.resolution.similarity.CompositeSimilarityScorer;
import com.entity.resolution.similarity.DefaultBlockingKeyStrategy;
import com.entity.resolution.similarity.SimilarityWeights;
import com.entity.resolution.cache.CacheConfig;
import com.entity.resolution.cache.MergeListener;
import com.entity.resolution.cache.ResolutionCache;
import com.entity.resolution.cache.NoOpResolutionCache;
import com.entity.resolution.lock.DistributedLock;
import com.entity.resolution.lock.NoOpDistributedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main entry point for the entity resolution library.
 * Provides a fluent API for configuring and using entity resolution.
 *
 * <h2>Key Design Principles</h2>
 * <ul>
 *   <li>Returns {@link EntityReference} instead of raw entity IDs to prevent stale references</li>
 *   <li>All relationships must be created through this API for proper merge migration</li>
 *   <li>Batch operations via {@link #beginBatch()} for efficient bulk processing</li>
 * </ul>
 *
 * <h2>Example usage:</h2>
 * <pre>
 * EntityResolver resolver = EntityResolver.builder()
 *     .graphConnection(connection)
 *     .build();
 *
 * // Single resolution
 * EntityResolutionResult result = resolver.resolve("Big Blue", EntityType.COMPANY);
 * EntityReference ref = result.getEntityReference();
 *
 * // Create relationships using references (merge-safe)
 * EntityResolutionResult other = resolver.resolve("Acme Corp", EntityType.COMPANY);
 * resolver.createRelationship(ref, other.getEntityReference(), "PARTNER");
 *
 * // Batch processing
 * try (BatchContext batch = resolver.beginBatch()) {
 *     EntityReference a = batch.resolve("Company A", EntityType.COMPANY).getEntityReference();
 *     EntityReference b = batch.resolve("Company B", EntityType.COMPANY).getEntityReference();
 *     batch.createRelationship(a, b, "SUBSIDIARY");
 *     BatchResult result = batch.commit();
 * }
 * </pre>
 */
public class EntityResolver implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(EntityResolver.class);

    private final EntityResolutionService service;
    private final RelationshipRepository relationshipRepository;
    private final GraphConnection connection;
    private final boolean ownsConnection;
    private final ResolutionOptions defaultOptions;
    private final MetricsService metricsService;
    private final TracingService tracingService;
    private final HealthCheckRegistry healthCheckRegistry;
    private final ReviewService reviewService;

    private EntityResolver(Builder builder) {
        this.connection = builder.connection;
        this.ownsConnection = builder.ownsConnection;
        this.defaultOptions = builder.options;
        this.metricsService = builder.metricsService != null
                ? builder.metricsService : new NoOpMetricsService();
        this.tracingService = builder.tracingService != null
                ? builder.tracingService : new NoOpTracingService();

        // Initialize repositories
        CypherExecutor executor = new CypherExecutor(connection);
        EntityRepository entityRepository = new EntityRepository(executor);
        SynonymRepository synonymRepository = new SynonymRepository(executor);
        DuplicateEntityRepository duplicateRepository = new DuplicateEntityRepository(executor);
        this.relationshipRepository = new RelationshipRepository(executor);

        // Initialize engines
        NormalizationEngine normalizationEngine = builder.normalizationEngine != null
                ? builder.normalizationEngine : DefaultNormalizationRules.createDefaultEngine();

        CompositeSimilarityScorer similarityScorer = new CompositeSimilarityScorer(builder.similarityWeights);

        AuditService auditService;
        if (builder.auditService != null) {
            auditService = builder.auditService;
        } else if (builder.auditRepository != null) {
            auditService = new AuditService(builder.auditRepository);
        } else {
            auditService = new AuditService();
        }

        MergeLedger mergeLedger = builder.mergeLedger != null
                ? builder.mergeLedger : new MergeLedger();

        MergeEngine mergeEngine = new MergeEngine(
                entityRepository, synonymRepository, duplicateRepository,
                relationshipRepository, mergeLedger, auditService);

        LLMProvider llmProvider = builder.llmProvider != null
                ? builder.llmProvider : new NoOpLLMProvider();
        LLMEnricher llmEnricher = new LLMEnricher(llmProvider, builder.options.getLlmConfidenceThreshold());

        BlockingKeyStrategy blockingKeyStrategy = builder.blockingKeyStrategy != null
                ? builder.blockingKeyStrategy : new DefaultBlockingKeyStrategy();

        // Initialize cache and lock
        ResolutionCache cache = builder.resolutionCache != null
                ? builder.resolutionCache : new NoOpResolutionCache();
        DistributedLock lock = builder.distributedLock != null
                ? builder.distributedLock : new NoOpDistributedLock();

        // Register cache as merge listener if it implements MergeListener
        if (cache instanceof MergeListener mergeListener) {
            mergeEngine.addMergeListener(mergeListener);
        }

        // Initialize review service
        ReviewQueue reviewQueue = builder.reviewQueue != null
                ? builder.reviewQueue : new InMemoryReviewQueue();
        this.reviewService = new ReviewService(reviewQueue, mergeEngine, auditService);

        // Create main service
        this.service = new EntityResolutionService(
                entityRepository, synonymRepository, duplicateRepository,
                normalizationEngine, similarityScorer, mergeEngine, llmEnricher, auditService,
                builder.options, blockingKeyStrategy, cache, lock, metricsService, tracingService,
                reviewService
        );

        // Create indexes if requested
        if (builder.createIndexes) {
            connection.createIndexes();
        }

        // Initialize health checks
        this.healthCheckRegistry = new HealthCheckRegistry();
        healthCheckRegistry.register(new FalkorDBHealthCheck(connection));
        healthCheckRegistry.register(new MemoryHealthCheck());
        if (builder.connectionPool != null) {
            healthCheckRegistry.register(new ConnectionPoolHealthCheck(builder.connectionPool));
        }

        log.info("EntityResolver initialized with graph: {}", connection.getGraphName());
    }

    // ========== Resolution API ==========

    /**
     * Resolves an entity name to a canonical entity.
     * Returns an {@link EntityResolutionResult} containing an {@link EntityReference}
     * that can be used for creating relationships.
     */
    public EntityResolutionResult resolve(String entityName, EntityType entityType) {
        return service.resolve(entityName, entityType);
    }

    /**
     * Resolves an entity name with custom options.
     */
    public EntityResolutionResult resolve(String entityName, EntityType entityType, ResolutionOptions options) {
        return service.resolve(entityName, entityType, options);
    }

    /**
     * Finds an entity by name without creating if not found.
     * Use this when you only want to check if an entity exists.
     */
    public Optional<EntityReference> findEntity(String entityName, EntityType entityType) {
        return service.findEntity(entityName, entityType);
    }

    /**
     * Gets an entity by ID.
     */
    public Optional<Entity> getEntity(String entityId) {
        return service.getEntity(entityId);
    }

    /**
     * Gets the canonical entity for a potentially merged entity.
     */
    public Optional<Entity> getCanonicalEntity(String entityId) {
        return service.getCanonicalEntity(entityId);
    }

    /**
     * Gets all synonyms for an entity.
     */
    public List<Synonym> getSynonyms(String entityId) {
        return service.getSynonyms(entityId);
    }

    /**
     * Adds a manual synonym to an entity.
     */
    public Synonym addSynonym(String entityId, String synonymValue) {
        return service.addSynonym(entityId, synonymValue, SynonymSource.HUMAN);
    }

    /**
     * Adds a synonym with specified source.
     */
    public Synonym addSynonym(String entityId, String synonymValue, SynonymSource source) {
        return service.addSynonym(entityId, synonymValue, source);
    }

    // ========== Relationship API ==========

    /**
     * Creates a relationship between two entities using EntityReferences.
     * The references ensure the relationship is created using current canonical IDs,
     * even if the original entities were merged.
     *
     * <p>Relationships created through this API are tracked by the library and will
     * be automatically migrated if either entity is merged in the future.</p>
     *
     * @param source The source entity reference
     * @param target The target entity reference
     * @param relationshipType The type of relationship (e.g., "PARTNER", "SUBSIDIARY")
     * @return The created relationship
     */
    public Relationship createRelationship(EntityReference source, EntityReference target,
                                            String relationshipType) {
        return createRelationship(source, target, relationshipType, Map.of());
    }

    /**
     * Creates a relationship with properties between two entities.
     *
     * @param source The source entity reference
     * @param target The target entity reference
     * @param relationshipType The type of relationship
     * @param properties Additional properties for the relationship
     * @return The created relationship
     */
    public Relationship createRelationship(EntityReference source, EntityReference target,
                                            String relationshipType, Map<String, Object> properties) {
        InputSanitizer.validateRelationshipType(relationshipType);

        try (Span span = tracingService.startSpan("entity.createRelationship",
                Map.of("relationshipType", relationshipType))) {
            // Resolve to current canonical IDs
            String sourceId = source.getId();
            String targetId = target.getId();

            span.setAttribute("sourceId", sourceId);
            span.setAttribute("targetId", targetId);
            log.debug("Creating relationship {} from {} to {}", relationshipType, sourceId, targetId);

            Relationship relationship = Relationship.builder()
                    .sourceEntityId(sourceId)
                    .targetEntityId(targetId)
                    .relationshipType(relationshipType)
                    .properties(properties)
                    .createdBy(defaultOptions.getSourceSystem())
                    .build();

            Relationship created = relationshipRepository.create(relationship);
            span.setStatus(Span.SpanStatus.OK);
            return created;
        }
    }

    /**
     * Finds all relationships where the entity is the source.
     */
    public List<Relationship> getOutgoingRelationships(EntityReference entityRef) {
        return relationshipRepository.findBySourceEntity(entityRef.getId());
    }

    /**
     * Finds all relationships where the entity is the target.
     */
    public List<Relationship> getIncomingRelationships(EntityReference entityRef) {
        return relationshipRepository.findByTargetEntity(entityRef.getId());
    }

    /**
     * Finds all relationships involving the entity.
     */
    public List<Relationship> getRelationships(EntityReference entityRef) {
        return relationshipRepository.findByEntity(entityRef.getId());
    }

    /**
     * Finds relationships between two specific entities.
     */
    public List<Relationship> getRelationshipsBetween(EntityReference source, EntityReference target) {
        return relationshipRepository.findBetween(source.getId(), target.getId());
    }

    /**
     * Deletes a relationship.
     */
    public void deleteRelationship(String relationshipId) {
        relationshipRepository.delete(relationshipId);
    }

    // ========== Batch API ==========

    /**
     * Begins a batch context for efficient bulk resolution.
     *
     * <p>BatchContext provides:</p>
     * <ul>
     *   <li>Deduplication - same name resolves to same entity within batch</li>
     *   <li>Deferred relationships - created after all entities resolved</li>
     *   <li>Transaction-like semantics - commit or rollback</li>
     * </ul>
     *
     * <p>Example:</p>
     * <pre>
     * try (BatchContext batch = resolver.beginBatch()) {
     *     EntityReference a = batch.resolve("Company A", EntityType.COMPANY).getEntityReference();
     *     EntityReference b = batch.resolve("Company B", EntityType.COMPANY).getEntityReference();
     *     batch.createRelationship(a, b, "PARTNER");
     *     BatchResult result = batch.commit();
     * }
     * </pre>
     */
    public BatchContext beginBatch() {
        return beginBatch(defaultOptions);
    }

    /**
     * Begins a batch context with custom options.
     */
    public BatchContext beginBatch(ResolutionOptions options) {
        return new BatchContext(this, options, metricsService, tracingService);
    }

    // ========== Review API ==========

    /**
     * Gets the review service for managing manual review items.
     */
    public ReviewService getReviewService() {
        return reviewService;
    }

    /**
     * Approves a pending review item, triggering a merge of the source entity
     * into the candidate entity.
     *
     * @param reviewId   the review item ID
     * @param reviewerId the reviewer's identifier
     * @param notes      optional notes about the decision
     * @return the merge result
     */
    public MergeResult approveReview(String reviewId, String reviewerId, String notes) {
        return reviewService.approveMatch(reviewId, reviewerId, notes);
    }

    /**
     * Rejects a pending review item, marking the entities as not the same.
     *
     * @param reviewId   the review item ID
     * @param reviewerId the reviewer's identifier
     * @param notes      optional notes about the decision
     */
    public void rejectReview(String reviewId, String reviewerId, String notes) {
        reviewService.rejectMatch(reviewId, reviewerId, notes);
    }

    // ========== Health Check API ==========

    /**
     * Returns the aggregate health status of all registered health checks.
     * Automatically includes FalkorDB connectivity and JVM memory checks.
     * If a connection pool is configured, pool utilization is also checked.
     */
    public HealthStatus health() {
        return healthCheckRegistry.checkAll();
    }

    // ========== Service Access ==========

    /**
     * Gets the underlying service for advanced operations.
     */
    public EntityResolutionService getService() {
        return service;
    }

    /**
     * Gets the graph connection.
     */
    public GraphConnection getConnection() {
        return connection;
    }

    /**
     * Gets the relationship repository.
     */
    public RelationshipRepository getRelationshipRepository() {
        return relationshipRepository;
    }

    /**
     * Creates an {@link AsyncEntityResolver} wrapping this resolver.
     * Uses Java 21 virtual threads for lightweight async execution.
     */
    public AsyncEntityResolver async() {
        return new AsyncEntityResolverImpl(this, defaultOptions);
    }

    @Override
    public void close() {
        if (ownsConnection && connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                log.warn("Error closing connection", e);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private GraphConnection connection;
        private boolean ownsConnection = false;
        private GraphConnectionPool connectionPool;
        private NormalizationEngine normalizationEngine;
        private SimilarityWeights similarityWeights = SimilarityWeights.defaultWeights();
        private LLMProvider llmProvider;
        private AuditService auditService;
        private AuditRepository auditRepository;
        private MergeLedger mergeLedger;
        private BlockingKeyStrategy blockingKeyStrategy;
        private ResolutionOptions options = ResolutionOptions.defaults();
        private boolean createIndexes = true;
        private ResolutionCache resolutionCache;
        private DistributedLock distributedLock;
        private MetricsService metricsService;
        private TracingService tracingService;
        private ReviewQueue reviewQueue;

        /**
         * Sets the graph connection to use.
         */
        public Builder graphConnection(GraphConnection connection) {
            this.connection = connection;
            this.ownsConnection = false;
            return this;
        }

        /**
         * Creates a FalkorDB connection with the given parameters.
         */
        public Builder falkorDB(String host, int port, String graphName) {
            this.connection = new FalkorDBConnection(host, port, graphName);
            this.ownsConnection = true;
            return this;
        }

        /**
         * Creates a pooled FalkorDB connection using the given pool configuration.
         * The pool is owned by this resolver and will be closed when the resolver is closed.
         */
        public Builder falkorDBPool(PoolConfig poolConfig) {
            this.connectionPool = new SimpleGraphConnectionPool(poolConfig);
            this.connection = new PooledFalkorDBConnection(this.connectionPool, poolConfig.getGraphName());
            this.ownsConnection = true;
            return this;
        }

        /**
         * Uses an existing connection pool.
         */
        public Builder connectionPool(GraphConnectionPool pool, String graphName) {
            this.connectionPool = pool;
            this.connection = new PooledFalkorDBConnection(pool, graphName);
            this.ownsConnection = false;
            return this;
        }

        /**
         * Sets a custom normalization engine.
         */
        public Builder normalizationEngine(NormalizationEngine engine) {
            this.normalizationEngine = engine;
            return this;
        }

        /**
         * Sets similarity weights for fuzzy matching.
         */
        public Builder similarityWeights(SimilarityWeights weights) {
            this.similarityWeights = weights;
            return this;
        }

        /**
         * Sets an LLM provider for semantic enrichment.
         */
        public Builder llmProvider(LLMProvider provider) {
            this.llmProvider = provider;
            return this;
        }

        /**
         * Sets a custom audit service.
         */
        public Builder auditService(AuditService auditService) {
            this.auditService = auditService;
            return this;
        }

        /**
         * Sets a custom audit repository for persistence.
         * If set, the AuditService will use this repository instead of the default in-memory one.
         */
        public Builder auditRepository(AuditRepository auditRepository) {
            this.auditRepository = auditRepository;
            return this;
        }

        /**
         * Sets a custom blocking key strategy for candidate narrowing.
         * Defaults to {@link DefaultBlockingKeyStrategy} if not set.
         */
        public Builder blockingKeyStrategy(BlockingKeyStrategy blockingKeyStrategy) {
            this.blockingKeyStrategy = blockingKeyStrategy;
            return this;
        }

        /**
         * Sets a custom merge ledger.
         */
        public Builder mergeLedger(MergeLedger mergeLedger) {
            this.mergeLedger = mergeLedger;
            return this;
        }

        /**
         * Sets resolution options.
         */
        public Builder options(ResolutionOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Controls whether to create indexes on startup.
         */
        public Builder createIndexes(boolean createIndexes) {
            this.createIndexes = createIndexes;
            return this;
        }

        /**
         * Sets a custom resolution cache.
         */
        public Builder cache(ResolutionCache cache) {
            this.resolutionCache = cache;
            return this;
        }

        /**
         * Sets a custom distributed lock.
         */
        public Builder distributedLock(DistributedLock lock) {
            this.distributedLock = lock;
            return this;
        }

        /**
         * Sets a custom metrics service for recording operational metrics.
         * Defaults to {@link NoOpMetricsService} if not set.
         */
        public Builder metricsService(MetricsService metricsService) {
            this.metricsService = metricsService;
            return this;
        }

        /**
         * Sets a custom tracing service for distributed tracing.
         * Defaults to {@link NoOpTracingService} if not set.
         */
        public Builder tracingService(TracingService tracingService) {
            this.tracingService = tracingService;
            return this;
        }

        /**
         * Sets a custom review queue for manual review items.
         * Defaults to {@link InMemoryReviewQueue} if not set.
         */
        public Builder reviewQueue(ReviewQueue reviewQueue) {
            this.reviewQueue = reviewQueue;
            return this;
        }

        /**
         * Enables LLM enrichment with the given provider.
         */
        public Builder withLLM(LLMProvider provider) {
            this.llmProvider = provider;
            this.options = ResolutionOptions.builder()
                    .useLLM(true)
                    .autoMergeThreshold(options.getAutoMergeThreshold())
                    .synonymThreshold(options.getSynonymThreshold())
                    .reviewThreshold(options.getReviewThreshold())
                    .llmConfidenceThreshold(options.getLlmConfidenceThreshold())
                    .build();
            return this;
        }

        public EntityResolver build() {
            if (connection == null) {
                throw new IllegalStateException("GraphConnection is required");
            }
            return new EntityResolver(this);
        }
    }
}
