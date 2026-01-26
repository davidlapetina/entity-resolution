package com.entity.resolution.merge;

import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.audit.MergeLedger;
import com.entity.resolution.logging.LogContext;
import com.entity.resolution.cache.MergeListener;
import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.CypherExecutor;
import com.entity.resolution.graph.DuplicateEntityRepository;
import com.entity.resolution.graph.EntityRepository;
import com.entity.resolution.graph.RelationshipRepository;
import com.entity.resolution.graph.SynonymRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Engine for orchestrating entity merge operations.
 * Implements the merge algorithm from PRD section 8.
 * Uses {@link MergeTransaction} for compensating transaction support.
 *
 * Merge process:
 * 1. Candidate discovery (8.1)
 * 2. Synonym attachment (8.2)
 * 3. Duplicate creation (8.3)
 * 4. Relationship migration (8.4, 8.5) - includes library-managed relationships
 * 5. Merge registration (8.6)
 */
public class MergeEngine {
    private static final Logger log = LoggerFactory.getLogger(MergeEngine.class);

    private final EntityRepository entityRepository;
    private final SynonymRepository synonymRepository;
    private final DuplicateEntityRepository duplicateRepository;
    private final RelationshipRepository relationshipRepository;
    private final MergeLedger mergeLedger;
    private final AuditService auditService;
    private final MergeStrategy defaultStrategy;
    private final List<MergeListener> mergeListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a MergeEngine without relationship repository (legacy support).
     */
    public MergeEngine(EntityRepository entityRepository,
                       SynonymRepository synonymRepository,
                       DuplicateEntityRepository duplicateRepository,
                       MergeLedger mergeLedger,
                       AuditService auditService) {
        this(entityRepository, synonymRepository, duplicateRepository, null,
                mergeLedger, auditService, MergeStrategy.KEEP_TARGET);
    }

    /**
     * Creates a MergeEngine with full relationship migration support.
     */
    public MergeEngine(EntityRepository entityRepository,
                       SynonymRepository synonymRepository,
                       DuplicateEntityRepository duplicateRepository,
                       RelationshipRepository relationshipRepository,
                       MergeLedger mergeLedger,
                       AuditService auditService) {
        this(entityRepository, synonymRepository, duplicateRepository, relationshipRepository,
                mergeLedger, auditService, MergeStrategy.KEEP_TARGET);
    }

    public MergeEngine(EntityRepository entityRepository,
                       SynonymRepository synonymRepository,
                       DuplicateEntityRepository duplicateRepository,
                       RelationshipRepository relationshipRepository,
                       MergeLedger mergeLedger,
                       AuditService auditService,
                       MergeStrategy defaultStrategy) {
        this.entityRepository = entityRepository;
        this.synonymRepository = synonymRepository;
        this.duplicateRepository = duplicateRepository;
        this.relationshipRepository = relationshipRepository;
        this.mergeLedger = mergeLedger;
        this.auditService = auditService;
        this.defaultStrategy = defaultStrategy;
    }

    /**
     * Merges the source entity into the target entity.
     * The source entity becomes a duplicate, and the target becomes the canonical entity.
     *
     * @param sourceEntityId entity to be merged (will become duplicate)
     * @param targetEntityId entity to merge into (canonical)
     * @param matchResult    the match result that triggered this merge
     * @param triggeredBy    identifier of who/what triggered the merge
     * @return result of the merge operation
     */
    public MergeResult merge(String sourceEntityId, String targetEntityId,
                              MatchResult matchResult, String triggeredBy) {
        return merge(sourceEntityId, targetEntityId, matchResult, triggeredBy, defaultStrategy);
    }

    /**
     * Merges the source entity into the target entity with specified strategy.
     * Uses a compensating transaction to roll back partial changes on failure.
     */
    public MergeResult merge(String sourceEntityId, String targetEntityId,
                              MatchResult matchResult, String triggeredBy,
                              MergeStrategy strategy) {
        try (LogContext logCtx = LogContext.forMerge(
                LogContext.generateCorrelationId(), sourceEntityId, targetEntityId)) {
        log.info("merge.starting sourceEntityId={} targetEntityId={} triggeredBy={}",
                sourceEntityId, targetEntityId, triggeredBy);

        // Validate entities exist and are active
        var sourceOpt = entityRepository.findById(sourceEntityId);
        var targetOpt = entityRepository.findById(targetEntityId);

        if (sourceOpt.isEmpty()) {
            return MergeResult.failure("Source entity not found: " + sourceEntityId);
        }
        if (targetOpt.isEmpty()) {
            return MergeResult.failure("Target entity not found: " + targetEntityId);
        }

        Entity source = sourceOpt.get();
        Entity target = targetOpt.get();

        if (!source.isActive()) {
            return MergeResult.failure(source, target, "Source entity is not active (already merged)");
        }
        if (!target.isActive()) {
            return MergeResult.failure(source, target, "Target entity is not active");
        }

        CypherExecutor executor = entityRepository.getExecutor();

        try (MergeTransaction tx = new MergeTransaction()) {
            List<String> synonymsCreated = new ArrayList<>();
            final String[] synonymIdHolder = {null};
            final String[] duplicateIdHolder = {null};

            // Step 1: Create synonym for the source's canonical name
            tx.execute("create synonym for source name",
                    () -> {
                        String synId = createSynonymForSourceName(source, target.getId(), matchResult.score());
                        synonymIdHolder[0] = synId;
                        if (synId != null) {
                            synonymsCreated.add(synId);
                        }
                    },
                    () -> {
                        if (synonymIdHolder[0] != null) {
                            executor.deleteSynonym(synonymIdHolder[0]);
                        }
                    }
            );

            // Step 2: Create duplicate entity record
            tx.execute("create duplicate record",
                    () -> {
                        duplicateIdHolder[0] = createDuplicateRecord(source, target.getId());
                    },
                    () -> {
                        if (duplicateIdHolder[0] != null) {
                            executor.deleteDuplicateEntity(duplicateIdHolder[0]);
                        }
                    }
            );

            // Step 3: Migrate library-managed relationships
            if (relationshipRepository != null) {
                tx.execute("migrate library relationships",
                        () -> migrateLibraryRelationships(sourceEntityId, targetEntityId),
                        () -> {
                            // Best-effort reverse migration
                            try {
                                relationshipRepository.migrateRelationships(targetEntityId, sourceEntityId);
                            } catch (Exception e) {
                                log.warn("Best-effort relationship reverse-migration failed: {}", e.getMessage());
                            }
                        }
                );
            }

            // Step 4: Record merge in graph (status change + MERGED_INTO relationship)
            tx.execute("record merge in graph",
                    () -> entityRepository.recordMerge(sourceEntityId, targetEntityId,
                            matchResult.score(), matchResult.reasoning()),
                    () -> executor.revertMerge(sourceEntityId, targetEntityId)
            );

            // Step 5: Record in merge ledger (append-only, compensation logs warning)
            final MergeRecord[] mergeRecordHolder = {null};
            tx.executeNoCompensation("record in merge ledger",
                    () -> mergeRecordHolder[0] = mergeLedger.recordMerge(
                            sourceEntityId, targetEntityId,
                            source.getCanonicalName(), target.getCanonicalName(),
                            matchResult.score(), matchResult.decision(),
                            triggeredBy, matchResult.reasoning()
                    )
            );

            // Step 6: Create audit entry (append-only, no compensation)
            tx.executeNoCompensation("create audit entry",
                    () -> auditService.record(AuditAction.ENTITY_MERGED, sourceEntityId, triggeredBy, Map.of(
                            "targetEntityId", targetEntityId,
                            "confidence", matchResult.score(),
                            "decision", matchResult.decision().name()
                    ))
            );

            tx.markSuccess();
            log.info("merge.completed sourceEntityId={} targetEntityId={} confidence={}",
                    sourceEntityId, targetEntityId, matchResult.score());

            // Notify merge listeners
            notifyMergeListeners(sourceEntityId, targetEntityId);

            return MergeResult.success(target, source, mergeRecordHolder[0],
                    synonymsCreated, duplicateIdHolder[0]);

        } catch (Exception e) {
            log.error("merge.failed sourceEntityId={} targetEntityId={} error={}",
                    sourceEntityId, targetEntityId, e.getMessage());
            return MergeResult.failure(source, target, "Merge failed: " + e.getMessage());
        }
        } // end LogContext
    }

    /**
     * Migrates library-managed relationships from source to target.
     */
    private void migrateLibraryRelationships(String sourceEntityId, String targetEntityId) {
        try {
            List<Relationship> sourceRelationships = relationshipRepository.findByEntity(sourceEntityId);
            if (!sourceRelationships.isEmpty()) {
                log.info("Migrating {} library-managed relationships from {} to {}",
                        sourceRelationships.size(), sourceEntityId, targetEntityId);

                relationshipRepository.migrateRelationships(sourceEntityId, targetEntityId);

                auditService.record(AuditAction.RELATIONSHIPS_MIGRATED, sourceEntityId, "MERGE_ENGINE", Map.of(
                        "targetEntityId", targetEntityId,
                        "relationshipCount", sourceRelationships.size()
                ));
            }
        } catch (Exception e) {
            log.warn("Error migrating library relationships: {}", e.getMessage());
            // Don't fail the merge for relationship migration issues
        }
    }

    /**
     * Creates a synonym for the source entity's name, attached to the target.
     */
    private String createSynonymForSourceName(Entity source, String targetEntityId, double confidence) {
        // Check if synonym already exists
        List<Synonym> existingSynonyms = synonymRepository.findByEntityId(targetEntityId);
        boolean alreadyExists = existingSynonyms.stream()
                .anyMatch(s -> s.getValue().equalsIgnoreCase(source.getCanonicalName()));

        if (alreadyExists) {
            log.debug("Synonym already exists for '{}' on entity {}",
                    source.getCanonicalName(), targetEntityId);
            return null;
        }

        Synonym synonym = Synonym.builder()
                .value(source.getCanonicalName())
                .normalizedValue(source.getNormalizedName())
                .source(SynonymSource.SYSTEM)
                .confidence(confidence)
                .build();

        synonymRepository.createForEntity(synonym, targetEntityId);

        auditService.record(AuditAction.SYNONYM_CREATED, targetEntityId, "MERGE_ENGINE", Map.of(
                "synonymValue", source.getCanonicalName(),
                "sourceEntityId", source.getId()
        ));

        log.debug("Created synonym '{}' for entity {}", source.getCanonicalName(), targetEntityId);
        return synonym.getId();
    }

    /**
     * Creates a duplicate entity record for tracking.
     */
    private String createDuplicateRecord(Entity source, String canonicalEntityId) {
        DuplicateEntity duplicate = DuplicateEntity.builder()
                .originalName(source.getCanonicalName())
                .normalizedName(source.getNormalizedName())
                .sourceSystem("MERGE")
                .build();

        duplicateRepository.createForCanonical(duplicate, canonicalEntityId);

        auditService.record(AuditAction.DUPLICATE_CREATED, canonicalEntityId, "MERGE_ENGINE", Map.of(
                "duplicateId", duplicate.getId(),
                "originalName", source.getCanonicalName()
        ));

        log.debug("Created duplicate record {} for entity {}", duplicate.getId(), canonicalEntityId);
        return duplicate.getId();
    }

    /**
     * Checks if two entities can be merged.
     */
    public boolean canMerge(String sourceEntityId, String targetEntityId) {
        var sourceOpt = entityRepository.findById(sourceEntityId);
        var targetOpt = entityRepository.findById(targetEntityId);

        if (sourceOpt.isEmpty() || targetOpt.isEmpty()) {
            return false;
        }

        Entity source = sourceOpt.get();
        Entity target = targetOpt.get();

        return source.isActive() && target.isActive() && source.getType() == target.getType();
    }

    /**
     * Gets the merge history for an entity.
     */
    public List<MergeRecord> getMergeHistory(String entityId) {
        return mergeLedger.getRecordsForTarget(entityId);
    }

    /**
     * Gets all entities that were merged into the given entity.
     */
    public List<String> getMergedEntityIds(String canonicalEntityId) {
        return mergeLedger.getMergeChain(canonicalEntityId);
    }

    /**
     * Adds a listener that will be notified after successful merges.
     */
    public void addMergeListener(MergeListener listener) {
        if (listener != null) {
            mergeListeners.add(listener);
        }
    }

    private void notifyMergeListeners(String sourceEntityId, String targetEntityId) {
        for (MergeListener listener : mergeListeners) {
            try {
                listener.onMerge(sourceEntityId, targetEntityId);
            } catch (Exception e) {
                log.warn("Merge listener notification failed: {}", e.getMessage());
            }
        }
    }

    public MergeLedger getMergeLedger() {
        return mergeLedger;
    }

    public AuditService getAuditService() {
        return auditService;
    }
}
