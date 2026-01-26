package com.entity.resolution.api;

import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.EntityRepository;
import com.entity.resolution.graph.InputSanitizer;
import com.entity.resolution.graph.SynonymRepository;
import com.entity.resolution.graph.DuplicateEntityRepository;
import com.entity.resolution.llm.LLMEnricher;
import com.entity.resolution.merge.MergeEngine;
import com.entity.resolution.merge.MergeResult;
import com.entity.resolution.rules.NormalizationEngine;
import com.entity.resolution.similarity.BlockingKeyStrategy;
import com.entity.resolution.similarity.CompositeSimilarityScorer;
import com.entity.resolution.similarity.DefaultBlockingKeyStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Service facade orchestrating all entity resolution components.
 * This is the main service class for performing entity resolution.
 */
public class EntityResolutionService {
    private static final Logger log = LoggerFactory.getLogger(EntityResolutionService.class);

    private final EntityRepository entityRepository;
    private final SynonymRepository synonymRepository;
    private final DuplicateEntityRepository duplicateRepository;
    private final NormalizationEngine normalizationEngine;
    private final CompositeSimilarityScorer similarityScorer;
    private final MergeEngine mergeEngine;
    private final LLMEnricher llmEnricher;
    private final AuditService auditService;
    private final ResolutionOptions defaultOptions;
    private final BlockingKeyStrategy blockingKeyStrategy;

    public EntityResolutionService(
            EntityRepository entityRepository,
            SynonymRepository synonymRepository,
            DuplicateEntityRepository duplicateRepository,
            NormalizationEngine normalizationEngine,
            CompositeSimilarityScorer similarityScorer,
            MergeEngine mergeEngine,
            LLMEnricher llmEnricher,
            AuditService auditService) {
        this(entityRepository, synonymRepository, duplicateRepository, normalizationEngine,
                similarityScorer, mergeEngine, llmEnricher, auditService, ResolutionOptions.defaults());
    }

    public EntityResolutionService(
            EntityRepository entityRepository,
            SynonymRepository synonymRepository,
            DuplicateEntityRepository duplicateRepository,
            NormalizationEngine normalizationEngine,
            CompositeSimilarityScorer similarityScorer,
            MergeEngine mergeEngine,
            LLMEnricher llmEnricher,
            AuditService auditService,
            ResolutionOptions defaultOptions) {
        this(entityRepository, synonymRepository, duplicateRepository, normalizationEngine,
                similarityScorer, mergeEngine, llmEnricher, auditService, defaultOptions,
                new DefaultBlockingKeyStrategy());
    }

    public EntityResolutionService(
            EntityRepository entityRepository,
            SynonymRepository synonymRepository,
            DuplicateEntityRepository duplicateRepository,
            NormalizationEngine normalizationEngine,
            CompositeSimilarityScorer similarityScorer,
            MergeEngine mergeEngine,
            LLMEnricher llmEnricher,
            AuditService auditService,
            ResolutionOptions defaultOptions,
            BlockingKeyStrategy blockingKeyStrategy) {
        this.entityRepository = entityRepository;
        this.synonymRepository = synonymRepository;
        this.duplicateRepository = duplicateRepository;
        this.normalizationEngine = normalizationEngine;
        this.similarityScorer = similarityScorer;
        this.mergeEngine = mergeEngine;
        this.llmEnricher = llmEnricher;
        this.auditService = auditService;
        this.defaultOptions = defaultOptions;
        this.blockingKeyStrategy = blockingKeyStrategy;
    }

    /**
     * Resolves an entity name to a canonical entity.
     * This is the main entry point for entity resolution.
     */
    public EntityResolutionResult resolve(String entityName, EntityType entityType) {
        return resolve(entityName, entityType, defaultOptions);
    }

    /**
     * Resolves an entity name to a canonical entity with custom options.
     */
    public EntityResolutionResult resolve(String entityName, EntityType entityType, ResolutionOptions options) {
        InputSanitizer.validateEntityName(entityName);

        log.info("Resolving entity: '{}' (type: {})", entityName, entityType);

        // Step 1: Normalize the input name
        String normalizedName = normalizationEngine.normalize(entityName, entityType);
        log.debug("Normalized '{}' to '{}'", entityName, normalizedName);

        // Create canonical ID resolver for this resolution context
        Supplier<String> canonicalIdResolver = () -> resolveCanonicalId(entityName, entityType);

        // Step 2: Try exact match on normalized name
        List<Entity> exactMatches = entityRepository.findByNormalizedName(normalizedName, entityType);
        if (!exactMatches.isEmpty()) {
            Entity match = exactMatches.get(0);
            log.info("Exact match found: {} ({})", match.getCanonicalName(), match.getId());
            List<Synonym> synonyms = synonymRepository.findByEntityId(match.getId());
            return EntityResolutionResult.builder()
                    .canonicalEntity(match)
                    .entityReference(EntityReference.withResolver(match.getId(), match.getType(),
                            () -> getCanonicalIdForEntity(match.getId())))
                    .synonyms(synonyms)
                    .decision(MatchDecision.AUTO_MERGE)
                    .confidence(1.0)
                    .isNewEntity(false)
                    .wasMerged(false)
                    .wasMatchedViaSynonym(false)
                    .wasNewSynonymCreated(false)
                    .inputName(entityName)
                    .matchedName(match.getCanonicalName())
                    .reasoning("Exact match on normalized name")
                    .build();
        }

        // Step 3: Try synonym lookup
        List<SynonymRepository.SynonymMatch> synonymMatches =
                synonymRepository.findEntitiesBySynonym(normalizedName, entityType);
        if (!synonymMatches.isEmpty()) {
            SynonymRepository.SynonymMatch synMatch = synonymMatches.get(0);
            Optional<Entity> entityOpt = entityRepository.findById(synMatch.entityId());
            if (entityOpt.isPresent()) {
                Entity match = entityOpt.get();
                log.info("Synonym match found: {} via synonym '{}'",
                        match.getCanonicalName(), synMatch.matchedSynonym());
                List<Synonym> synonyms = synonymRepository.findByEntityId(match.getId());
                return EntityResolutionResult.builder()
                        .canonicalEntity(match)
                        .entityReference(EntityReference.withResolver(match.getId(), match.getType(),
                                () -> getCanonicalIdForEntity(match.getId())))
                        .synonyms(synonyms)
                        .decision(MatchDecision.AUTO_MERGE)
                        .confidence(1.0)
                        .isNewEntity(false)
                        .wasMerged(false)
                        .wasMatchedViaSynonym(true)
                        .wasNewSynonymCreated(false)
                        .inputName(entityName)
                        .matchedName(synMatch.matchedSynonym())
                        .reasoning("Matched via existing synonym: " + synMatch.matchedSynonym())
                        .build();
            }
        }

        // Step 4: Fuzzy matching against all active entities
        MatchResult bestMatch = findBestFuzzyMatch(entityName, normalizedName, entityType, options);

        if (bestMatch.hasMatch()) {
            return handleMatchResult(entityName, normalizedName, entityType, bestMatch, options);
        }

        // Step 5: No match found - create new entity
        return createNewEntity(entityName, normalizedName, entityType, options);
    }

    /**
     * Finds an entity by name without creating if not found.
     * Returns an EntityReference if found, empty otherwise.
     */
    public Optional<EntityReference> findEntity(String entityName, EntityType entityType) {
        String normalizedName = normalizationEngine.normalize(entityName, entityType);

        // Try exact match
        List<Entity> exactMatches = entityRepository.findByNormalizedName(normalizedName, entityType);
        if (!exactMatches.isEmpty()) {
            Entity match = exactMatches.get(0);
            return Optional.of(EntityReference.withResolver(match.getId(), match.getType(),
                    () -> getCanonicalIdForEntity(match.getId())));
        }

        // Try synonym lookup
        List<SynonymRepository.SynonymMatch> synonymMatches =
                synonymRepository.findEntitiesBySynonym(normalizedName, entityType);
        if (!synonymMatches.isEmpty()) {
            SynonymRepository.SynonymMatch synMatch = synonymMatches.get(0);
            Optional<Entity> entityOpt = entityRepository.findById(synMatch.entityId());
            if (entityOpt.isPresent()) {
                Entity match = entityOpt.get();
                return Optional.of(EntityReference.withResolver(match.getId(), match.getType(),
                        () -> getCanonicalIdForEntity(match.getId())));
            }
        }

        return Optional.empty();
    }

    /**
     * Gets the canonical ID for an entity, following merge chains.
     */
    private String getCanonicalIdForEntity(String entityId) {
        return entityRepository.getCanonical(entityId)
                .map(Entity::getId)
                .orElse(entityId);
    }

    /**
     * Resolves the canonical ID for a given entity name.
     * Used by EntityReference to always resolve to current canonical.
     */
    private String resolveCanonicalId(String entityName, EntityType entityType) {
        String normalizedName = normalizationEngine.normalize(entityName, entityType);

        // Check exact match
        List<Entity> matches = entityRepository.findByNormalizedName(normalizedName, entityType);
        if (!matches.isEmpty()) {
            return getCanonicalIdForEntity(matches.get(0).getId());
        }

        // Check synonym match
        List<SynonymRepository.SynonymMatch> synonymMatches =
                synonymRepository.findEntitiesBySynonym(normalizedName, entityType);
        if (!synonymMatches.isEmpty()) {
            return getCanonicalIdForEntity(synonymMatches.get(0).entityId());
        }

        // Not found - would need to create
        return null;
    }

    /**
     * Finds the best fuzzy match among active entities.
     * Uses blocking keys to narrow candidates first, falling back to full scan
     * if no blocking key candidates are found.
     */
    private MatchResult findBestFuzzyMatch(String originalName, String normalizedName,
                                            EntityType entityType, ResolutionOptions options) {
        // Try blocking key candidates first
        Set<String> blockingKeys = blockingKeyStrategy.generateKeys(normalizedName);
        List<Entity> candidates;

        if (!blockingKeys.isEmpty()) {
            candidates = entityRepository.findCandidatesByBlockingKeys(blockingKeys, entityType);
            log.debug("Blocking keys generated {} candidates for fuzzy match", candidates.size());

            if (candidates.isEmpty()) {
                // Fall back to full scan if no blocking key candidates found
                candidates = entityRepository.findAllActive(entityType);
                log.debug("Blocking key fallback: checking {} candidates via full scan", candidates.size());
            }
        } else {
            candidates = entityRepository.findAllActive(entityType);
            log.debug("No blocking keys generated, checking {} candidates via full scan", candidates.size());
        }

        MatchResult bestMatch = MatchResult.noMatch();

        for (Entity candidate : candidates) {
            double score = similarityScorer.compute(normalizedName, candidate.getNormalizedName());
            if (score > bestMatch.score()) {
                bestMatch = MatchResult.of(
                        score,
                        candidate.getId(),
                        candidate.getCanonicalName(),
                        options.getAutoMergeThreshold(),
                        options.getSynonymThreshold(),
                        options.getReviewThreshold()
                );
            }
        }

        log.debug("Best fuzzy match: score={}, decision={}, entity={}",
                bestMatch.score(), bestMatch.decision(), bestMatch.matchedName());

        // Consider LLM enrichment if score is in the uncertain range
        if (options.isUseLLM() && bestMatch.shouldEnrichWithLLM() && llmEnricher.isAvailable()) {
            log.info("Requesting LLM enrichment for uncertain match");
            auditService.record(AuditAction.LLM_ENRICHMENT_REQUESTED, bestMatch.candidateEntityId(),
                    options.getSourceSystem(), Map.of(
                            "originalName", originalName,
                            "candidateName", bestMatch.matchedName(),
                            "fuzzyScore", bestMatch.score()
                    ));

            MatchResult llmResult = llmEnricher.enrich(
                    originalName, bestMatch.matchedName(), entityType, bestMatch.candidateEntityId());

            auditService.record(AuditAction.LLM_ENRICHMENT_COMPLETED, bestMatch.candidateEntityId(),
                    options.getSourceSystem(), Map.of(
                            "llmConfidence", llmResult.score(),
                            "llmDecision", llmResult.decision().name()
                    ));

            return llmResult;
        }

        return bestMatch;
    }

    /**
     * Handles a match result based on the decision.
     */
    private EntityResolutionResult handleMatchResult(String originalName, String normalizedName,
                                                      EntityType entityType, MatchResult matchResult,
                                                      ResolutionOptions options) {
        Optional<Entity> entityOpt = entityRepository.findById(matchResult.candidateEntityId());
        if (entityOpt.isEmpty()) {
            log.warn("Matched entity not found: {}", matchResult.candidateEntityId());
            return createNewEntity(originalName, normalizedName, entityType, options);
        }

        Entity matchedEntity = entityOpt.get();
        List<Synonym> synonyms = synonymRepository.findByEntityId(matchedEntity.getId());
        Supplier<String> canonicalResolver = () -> getCanonicalIdForEntity(matchedEntity.getId());

        switch (matchResult.decision()) {
            case AUTO_MERGE -> {
                if (options.isAutoMergeEnabled()) {
                    // Create a new entity temporarily, then merge it
                    Entity newEntity = createEntityNode(originalName, normalizedName, entityType, matchResult.score());
                    MergeResult mergeResult = mergeEngine.merge(
                            newEntity.getId(), matchedEntity.getId(), matchResult, options.getSourceSystem());

                    if (mergeResult.isSuccess()) {
                        synonyms = synonymRepository.findByEntityId(matchedEntity.getId());
                        return EntityResolutionResult.builder()
                                .canonicalEntity(matchedEntity)
                                .entityReference(EntityReference.withResolver(matchedEntity.getId(),
                                        matchedEntity.getType(), canonicalResolver))
                                .synonyms(synonyms)
                                .decision(MatchDecision.AUTO_MERGE)
                                .confidence(matchResult.score())
                                .isNewEntity(false)
                                .wasMerged(true)
                                .wasMatchedViaSynonym(false)
                                .wasNewSynonymCreated(true)
                                .inputName(originalName)
                                .matchedName(matchedEntity.getCanonicalName())
                                .reasoning(matchResult.reasoning())
                                .build();
                    } else {
                        log.warn("Auto-merge failed: {}", mergeResult.errorMessage());
                        return EntityResolutionResult.builder()
                                .canonicalEntity(matchedEntity)
                                .entityReference(EntityReference.withResolver(matchedEntity.getId(),
                                        matchedEntity.getType(), canonicalResolver))
                                .decision(MatchDecision.REVIEW)
                                .confidence(matchResult.score())
                                .isNewEntity(false)
                                .wasMerged(false)
                                .wasMatchedViaSynonym(false)
                                .wasNewSynonymCreated(false)
                                .inputName(originalName)
                                .matchedName(matchedEntity.getCanonicalName())
                                .reasoning("Auto-merge failed: " + mergeResult.errorMessage())
                                .build();
                    }
                } else {
                    return EntityResolutionResult.builder()
                            .canonicalEntity(matchedEntity)
                            .entityReference(EntityReference.withResolver(matchedEntity.getId(),
                                    matchedEntity.getType(), canonicalResolver))
                            .decision(MatchDecision.REVIEW)
                            .confidence(matchResult.score())
                            .isNewEntity(false)
                            .wasMerged(false)
                            .wasMatchedViaSynonym(false)
                            .wasNewSynonymCreated(false)
                            .inputName(originalName)
                            .matchedName(matchedEntity.getCanonicalName())
                            .reasoning("Auto-merge disabled - manual review required")
                            .build();
                }
            }
            case SYNONYM_ONLY -> {
                // Create synonym linking the new name to the existing entity
                Synonym synonym = Synonym.builder()
                        .value(originalName)
                        .normalizedValue(normalizedName)
                        .source(SynonymSource.SYSTEM)
                        .confidence(matchResult.score())
                        .build();
                synonymRepository.createForEntity(synonym, matchedEntity.getId());

                synonyms = synonymRepository.findByEntityId(matchedEntity.getId());
                return EntityResolutionResult.builder()
                        .canonicalEntity(matchedEntity)
                        .entityReference(EntityReference.withResolver(matchedEntity.getId(),
                                matchedEntity.getType(), canonicalResolver))
                        .synonyms(synonyms)
                        .decision(MatchDecision.SYNONYM_ONLY)
                        .confidence(matchResult.score())
                        .isNewEntity(false)
                        .wasMerged(false)
                        .wasMatchedViaSynonym(false)
                        .wasNewSynonymCreated(true)
                        .inputName(originalName)
                        .matchedName(matchedEntity.getCanonicalName())
                        .reasoning(matchResult.reasoning())
                        .build();
            }
            case REVIEW -> {
                auditService.record(AuditAction.MANUAL_REVIEW_REQUESTED, matchedEntity.getId(),
                        options.getSourceSystem(), Map.of(
                                "originalName", originalName,
                                "matchedName", matchedEntity.getCanonicalName(),
                                "confidence", matchResult.score()
                        ));
                return EntityResolutionResult.builder()
                        .canonicalEntity(matchedEntity)
                        .entityReference(EntityReference.withResolver(matchedEntity.getId(),
                                matchedEntity.getType(), canonicalResolver))
                        .decision(MatchDecision.REVIEW)
                        .confidence(matchResult.score())
                        .isNewEntity(false)
                        .wasMerged(false)
                        .wasMatchedViaSynonym(false)
                        .wasNewSynonymCreated(false)
                        .inputName(originalName)
                        .matchedName(matchedEntity.getCanonicalName())
                        .reasoning(matchResult.reasoning() != null ? matchResult.reasoning() : "Manual review required")
                        .build();
            }
            case LLM_ENRICH -> {
                return EntityResolutionResult.builder()
                        .canonicalEntity(matchedEntity)
                        .entityReference(EntityReference.withResolver(matchedEntity.getId(),
                                matchedEntity.getType(), canonicalResolver))
                        .decision(matchResult.decision())
                        .confidence(matchResult.score())
                        .isNewEntity(false)
                        .wasMerged(false)
                        .wasMatchedViaSynonym(false)
                        .wasNewSynonymCreated(false)
                        .inputName(originalName)
                        .matchedName(matchedEntity.getCanonicalName())
                        .reasoning("LLM enriched: " + matchResult.reasoning())
                        .build();
            }
            default -> {
                return createNewEntity(originalName, normalizedName, entityType, options);
            }
        }
    }

    /**
     * Creates a new entity when no match is found.
     */
    private EntityResolutionResult createNewEntity(String originalName, String normalizedName,
                                                    EntityType entityType, ResolutionOptions options) {
        Entity entity = createEntityNode(originalName, normalizedName, entityType, 1.0);

        auditService.record(AuditAction.ENTITY_CREATED, entity.getId(), options.getSourceSystem(), Map.of(
                "canonicalName", originalName,
                "normalizedName", normalizedName,
                "type", entityType.name()
        ));

        log.info("Created new entity: {} ({})", entity.getCanonicalName(), entity.getId());

        return EntityResolutionResult.builder()
                .canonicalEntity(entity)
                .entityReference(EntityReference.withResolver(entity.getId(), entity.getType(),
                        () -> getCanonicalIdForEntity(entity.getId())))
                .decision(MatchDecision.NO_MATCH)
                .confidence(1.0)
                .isNewEntity(true)
                .wasMerged(false)
                .wasMatchedViaSynonym(false)
                .wasNewSynonymCreated(false)
                .inputName(originalName)
                .matchedName(originalName)
                .reasoning("No matching entity found - created new entity")
                .build();
    }

    /**
     * Creates an entity node in the graph and persists blocking keys.
     */
    private Entity createEntityNode(String canonicalName, String normalizedName,
                                     EntityType entityType, double confidence) {
        Entity entity = Entity.builder()
                .canonicalName(canonicalName)
                .normalizedName(normalizedName)
                .type(entityType)
                .confidenceScore(confidence)
                .build();

        Set<String> blockingKeys = blockingKeyStrategy.generateKeys(normalizedName);
        return entityRepository.save(entity, blockingKeys);
    }

    /**
     * Gets an entity by ID.
     */
    public Optional<Entity> getEntity(String entityId) {
        return entityRepository.findById(entityId);
    }

    /**
     * Gets the canonical entity for a potentially merged entity.
     */
    public Optional<Entity> getCanonicalEntity(String entityId) {
        return entityRepository.getCanonical(entityId);
    }

    /**
     * Gets all synonyms for an entity.
     */
    public List<Synonym> getSynonyms(String entityId) {
        return synonymRepository.findByEntityId(entityId);
    }

    /**
     * Adds a manual synonym to an entity.
     */
    public Synonym addSynonym(String entityId, String synonymValue, SynonymSource source) {
        String normalizedValue = normalizationEngine.normalize(synonymValue);
        Synonym synonym = Synonym.builder()
                .value(synonymValue)
                .normalizedValue(normalizedValue)
                .source(source)
                .confidence(1.0)
                .build();
        synonymRepository.createForEntity(synonym, entityId);

        auditService.record(AuditAction.SYNONYM_CREATED, entityId, "MANUAL", Map.of(
                "synonymValue", synonymValue,
                "source", source.name()
        ));

        return synonym;
    }

    /**
     * Gets the merge history for an entity.
     */
    public List<MergeRecord> getMergeHistory(String entityId) {
        return mergeEngine.getMergeHistory(entityId);
    }

    public NormalizationEngine getNormalizationEngine() {
        return normalizationEngine;
    }

    public CompositeSimilarityScorer getSimilarityScorer() {
        return similarityScorer;
    }

    public MergeEngine getMergeEngine() {
        return mergeEngine;
    }

    public AuditService getAuditService() {
        return auditService;
    }
}
