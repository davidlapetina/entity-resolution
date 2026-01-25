package com.entity.resolution.merge;

import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.audit.MergeLedger;
import com.entity.resolution.core.model.*;
import com.entity.resolution.graph.DuplicateEntityRepository;
import com.entity.resolution.graph.EntityRepository;
import com.entity.resolution.graph.RelationshipRepository;
import com.entity.resolution.graph.SynonymRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Engine for orchestrating entity merge operations.
 * Implements the merge algorithm from PRD section 8.
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
     */
    public MergeResult merge(String sourceEntityId, String targetEntityId,
                              MatchResult matchResult, String triggeredBy,
                              MergeStrategy strategy) {
        log.info("Starting merge: {} -> {} (triggered by: {})", sourceEntityId, targetEntityId, triggeredBy);

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

        // Step 1: Create synonym for the source's canonical name
        List<String> synonymsCreated = new ArrayList<>();
        String synonymId = createSynonymForSourceName(source, target.getId(), matchResult.score());
        if (synonymId != null) {
            synonymsCreated.add(synonymId);
        }

        // Step 2: Create duplicate entity record
        String duplicateId = createDuplicateRecord(source, target.getId());

        // Step 3: Migrate library-managed relationships
        if (relationshipRepository != null) {
            migrateLibraryRelationships(sourceEntityId, targetEntityId);
        }

        // Step 4: Record merge in graph (also handles general relationship migration)
        entityRepository.recordMerge(sourceEntityId, targetEntityId,
                matchResult.score(), matchResult.reasoning());

        // Step 5: Record in merge ledger
        MergeRecord mergeRecord = mergeLedger.recordMerge(
                sourceEntityId, targetEntityId,
                source.getCanonicalName(), target.getCanonicalName(),
                matchResult.score(), matchResult.decision(),
                triggeredBy, matchResult.reasoning()
        );

        // Step 6: Create audit entry
        auditService.record(AuditAction.ENTITY_MERGED, sourceEntityId, triggeredBy, Map.of(
                "targetEntityId", targetEntityId,
                "confidence", matchResult.score(),
                "decision", matchResult.decision().name()
        ));

        log.info("Merge completed: {} -> {}", sourceEntityId, targetEntityId);

        return MergeResult.success(target, source, mergeRecord, synonymsCreated, duplicateId);
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

    public MergeLedger getMergeLedger() {
        return mergeLedger;
    }

    public AuditService getAuditService() {
        return auditService;
    }
}
