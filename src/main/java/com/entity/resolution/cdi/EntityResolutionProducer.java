package com.entity.resolution.cdi;

import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.api.ResolutionOptions;
import com.entity.resolution.graph.PoolConfig;
import com.entity.resolution.llm.LLMProvider;
import com.entity.resolution.llm.NoOpLLMProvider;
import com.entity.resolution.llm.OllamaLLMProvider;

import java.time.Duration;
import com.entity.resolution.rest.security.*;
import com.entity.resolution.review.ReviewService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * CDI producer that wires the entity resolution library from MicroProfile Config properties.
 *
 * <p>When this class is on the classpath in a CDI container (e.g., Quarkus),
 * it reads configuration from {@code application.yaml} and produces all the
 * necessary beans: {@link EntityResolver}, security filters, and configuration objects.</p>
 *
 * <h2>Required configuration</h2>
 * <p>At minimum, FalkorDB connection settings must be provided:</p>
 * <pre>
 * entity-resolution:
 *   falkordb:
 *     host: localhost
 *     port: 6379
 *     graph-name: entity-resolution
 * </pre>
 *
 * <h2>Quarkus usage</h2>
 * <p>Add the entity-resolution JAR to your Quarkus project dependencies.
 * The CDI beans are auto-discovered. Inject them directly:</p>
 * <pre>
 * &#64;Inject EntityResolver resolver;
 * &#64;Inject SecurityConfig securityConfig;
 * </pre>
 */
@ApplicationScoped
public class EntityResolutionProducer {

    private static final Logger log = LoggerFactory.getLogger(EntityResolutionProducer.class);

    // ── FalkorDB ──────────────────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.falkordb.host", defaultValue = "localhost")
    String falkordbHost;

    @Inject
    @ConfigProperty(name = "entity-resolution.falkordb.port", defaultValue = "6379")
    int falkordbPort;

    @Inject
    @ConfigProperty(name = "entity-resolution.falkordb.graph-name", defaultValue = "entity-resolution")
    String falkordbGraphName;

    // ── Connection Pool ───────────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.pool.max-total", defaultValue = "20")
    int poolMaxTotal;

    @Inject
    @ConfigProperty(name = "entity-resolution.pool.max-idle", defaultValue = "10")
    int poolMaxIdle;

    @Inject
    @ConfigProperty(name = "entity-resolution.pool.min-idle", defaultValue = "2")
    int poolMinIdle;

    @Inject
    @ConfigProperty(name = "entity-resolution.pool.max-wait-millis", defaultValue = "5000")
    long poolMaxWaitMillis;

    @Inject
    @ConfigProperty(name = "entity-resolution.pool.test-on-borrow", defaultValue = "true")
    boolean poolTestOnBorrow;

    // ── Resolution Thresholds ─────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.resolution.auto-merge-threshold", defaultValue = "0.92")
    double autoMergeThreshold;

    @Inject
    @ConfigProperty(name = "entity-resolution.resolution.synonym-threshold", defaultValue = "0.80")
    double synonymThreshold;

    @Inject
    @ConfigProperty(name = "entity-resolution.resolution.review-threshold", defaultValue = "0.60")
    double reviewThreshold;

    @Inject
    @ConfigProperty(name = "entity-resolution.resolution.auto-merge-enabled", defaultValue = "true")
    boolean autoMergeEnabled;

    @Inject
    @ConfigProperty(name = "entity-resolution.resolution.source-system", defaultValue = "SYSTEM")
    String sourceSystem;

    // ── Cache ─────────────────────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.cache.enabled", defaultValue = "true")
    boolean cacheEnabled;

    @Inject
    @ConfigProperty(name = "entity-resolution.cache.max-size", defaultValue = "10000")
    int cacheMaxSize;

    @Inject
    @ConfigProperty(name = "entity-resolution.cache.ttl-seconds", defaultValue = "300")
    int cacheTtlSeconds;

    // ── LLM ───────────────────────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.llm.enabled", defaultValue = "false")
    boolean llmEnabled;

    @Inject
    @ConfigProperty(name = "entity-resolution.llm.provider", defaultValue = "ollama")
    String llmProviderType;

    @Inject
    @ConfigProperty(name = "entity-resolution.llm.ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaBaseUrl;

    @Inject
    @ConfigProperty(name = "entity-resolution.llm.ollama.model", defaultValue = "llama3.2")
    String ollamaModel;

    @Inject
    @ConfigProperty(name = "entity-resolution.llm.ollama.timeout-seconds", defaultValue = "30")
    int ollamaTimeoutSeconds;

    @Inject
    @ConfigProperty(name = "entity-resolution.llm.confidence-threshold", defaultValue = "0.85")
    double llmConfidenceThreshold;

    // ── Security ──────────────────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.security.enabled", defaultValue = "true")
    boolean securityEnabled;

    @Inject
    @ConfigProperty(name = "entity-resolution.security.api-key-header", defaultValue = "X-API-Key")
    String apiKeyHeader;

    @Inject
    @ConfigProperty(name = "entity-resolution.security.admin-keys")
    Optional<List<String>> adminKeys;

    @Inject
    @ConfigProperty(name = "entity-resolution.security.writer-keys")
    Optional<List<String>> writerKeys;

    @Inject
    @ConfigProperty(name = "entity-resolution.security.reader-keys")
    Optional<List<String>> readerKeys;

    // ── CORS ──────────────────────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.cors.enabled", defaultValue = "false")
    boolean corsEnabled;

    @Inject
    @ConfigProperty(name = "entity-resolution.cors.allowed-origins", defaultValue = "*")
    String corsAllowedOrigins;

    @Inject
    @ConfigProperty(name = "entity-resolution.cors.allowed-methods", defaultValue = "GET,POST,PUT,DELETE,OPTIONS")
    String corsAllowedMethods;

    @Inject
    @ConfigProperty(name = "entity-resolution.cors.allowed-headers", defaultValue = "Content-Type,X-API-Key,Authorization")
    String corsAllowedHeaders;

    @Inject
    @ConfigProperty(name = "entity-resolution.cors.max-age", defaultValue = "86400")
    long corsMaxAge;

    // ── Rate Limiting ─────────────────────────────────────────

    @Inject
    @ConfigProperty(name = "entity-resolution.rate-limit.enabled", defaultValue = "true")
    boolean rateLimitEnabled;

    @Inject
    @ConfigProperty(name = "entity-resolution.rate-limit.requests-per-second", defaultValue = "100")
    int rateLimitRequestsPerSecond;

    @Inject
    @ConfigProperty(name = "entity-resolution.rate-limit.burst-size", defaultValue = "200")
    int rateLimitBurstSize;

    // ══════════════════════════════════════════════════════════
    //  Producers
    // ══════════════════════════════════════════════════════════

    @Produces
    @ApplicationScoped
    public EntityResolver entityResolver() {
        log.info("Producing EntityResolver: falkordb={}:{}/{}", falkordbHost, falkordbPort, falkordbGraphName);

        PoolConfig poolConfig = PoolConfig.builder()
                .host(falkordbHost)
                .port(falkordbPort)
                .graphName(falkordbGraphName)
                .maxTotal(poolMaxTotal)
                .maxIdle(poolMaxIdle)
                .minIdle(poolMinIdle)
                .maxWaitMillis(poolMaxWaitMillis)
                .testOnBorrow(poolTestOnBorrow)
                .build();

        ResolutionOptions options = ResolutionOptions.builder()
                .autoMergeThreshold(autoMergeThreshold)
                .synonymThreshold(synonymThreshold)
                .reviewThreshold(reviewThreshold)
                .autoMergeEnabled(autoMergeEnabled)
                .sourceSystem(sourceSystem)
                .cachingEnabled(cacheEnabled)
                .cacheMaxSize(cacheMaxSize)
                .cacheTtlSeconds(cacheTtlSeconds)
                .useLLM(llmEnabled)
                .llmConfidenceThreshold(llmConfidenceThreshold)
                .build();

        EntityResolver.Builder builder = EntityResolver.builder()
                .falkorDBPool(poolConfig)
                .options(options);

        // Conditionally wire LLM provider
        if (llmEnabled) {
            LLMProvider llmProvider = createLLMProvider();
            builder.llmProvider(llmProvider);
            log.info("LLM enrichment enabled: provider={} model={}", llmProviderType, ollamaModel);
        } else {
            log.info("LLM enrichment disabled");
        }

        return builder.build();
    }

    public void closeResolver(@Disposes EntityResolver resolver) {
        log.info("Closing EntityResolver");
        resolver.close();
    }

    @Produces
    @ApplicationScoped
    public ReviewService reviewService(EntityResolver resolver) {
        return resolver.getReviewService();
    }

    @Produces
    @ApplicationScoped
    public SecurityConfig securityConfig() {
        SecurityConfig.Builder builder = SecurityConfig.builder()
                .enabled(securityEnabled)
                .apiKeyHeader(apiKeyHeader);

        adminKeys.ifPresent(keys -> builder.addKeys(keys, SecurityRole.ADMIN));
        writerKeys.ifPresent(keys -> builder.addKeys(keys, SecurityRole.WRITER));
        readerKeys.ifPresent(keys -> builder.addKeys(keys, SecurityRole.READER));

        SecurityConfig config = builder.build();
        log.info("Security config: enabled={} keyCount={}", config.isEnabled(), config.keyCount());
        return config;
    }

    @Produces
    @ApplicationScoped
    public CorsConfig corsConfig() {
        return new CorsConfig(corsEnabled, corsAllowedOrigins, corsAllowedMethods,
                corsAllowedHeaders, corsMaxAge);
    }

    @Produces
    @ApplicationScoped
    public RateLimitConfig rateLimitConfig() {
        return new RateLimitConfig(rateLimitEnabled, rateLimitRequestsPerSecond, rateLimitBurstSize);
    }

    @Produces
    @ApplicationScoped
    public ApiKeyAuthFilter apiKeyAuthFilter(SecurityConfig config) {
        return new ApiKeyAuthFilter(config);
    }

    @Produces
    @ApplicationScoped
    public RoleAuthorizationFilter roleAuthorizationFilter(SecurityConfig config) {
        return new RoleAuthorizationFilter(config);
    }

    @Produces
    @ApplicationScoped
    public CorsFilter corsFilter(CorsConfig config) {
        return new CorsFilter(config);
    }

    @Produces
    @ApplicationScoped
    public RateLimitFilter rateLimitFilter(RateLimitConfig config) {
        return new RateLimitFilter(config);
    }

    // ══════════════════════════════════════════════════════════
    //  Internal
    // ══════════════════════════════════════════════════════════

    private LLMProvider createLLMProvider() {
        if ("ollama".equalsIgnoreCase(llmProviderType)) {
            return OllamaLLMProvider.builder()
                    .baseUrl(ollamaBaseUrl)
                    .model(ollamaModel)
                    .timeout(Duration.ofSeconds(ollamaTimeoutSeconds))
                    .build();
        }
        log.warn("Unknown LLM provider '{}', falling back to NoOp", llmProviderType);
        return new NoOpLLMProvider();
    }
}
