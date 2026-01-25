package com.entity.resolution.audit;

import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.MergeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Immutable ledger tracking all merge operations with full provenance.
 * Per PRD section 9, this provides complete audit trail for entity merges.
 */
public class MergeLedger {
    private static final Logger log = LoggerFactory.getLogger(MergeLedger.class);

    private final List<MergeRecord> records;

    public MergeLedger() {
        this.records = new CopyOnWriteArrayList<>();
    }

    /**
     * Records a new merge operation in the ledger.
     * This operation is append-only - records cannot be modified or deleted.
     */
    public MergeRecord record(MergeRecord mergeRecord) {
        records.add(mergeRecord);
        log.info("Merge recorded: {} -> {} (confidence: {}, decision: {})",
                mergeRecord.sourceEntityId(),
                mergeRecord.targetEntityId(),
                mergeRecord.confidenceScore(),
                mergeRecord.decision());
        return mergeRecord;
    }

    /**
     * Creates and records a new merge.
     */
    public MergeRecord recordMerge(String sourceEntityId, String targetEntityId,
                                    String sourceEntityName, String targetEntityName,
                                    double confidenceScore, MatchDecision decision,
                                    String triggeredBy, String reasoning) {
        MergeRecord record = MergeRecord.builder()
                .sourceEntityId(sourceEntityId)
                .targetEntityId(targetEntityId)
                .sourceEntityName(sourceEntityName)
                .targetEntityName(targetEntityName)
                .confidenceScore(confidenceScore)
                .decision(decision)
                .triggeredBy(triggeredBy)
                .reasoning(reasoning)
                .build();
        return record(record);
    }

    /**
     * Gets all merge records (immutable view).
     */
    public List<MergeRecord> getAllRecords() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    /**
     * Gets merge records for a specific target entity.
     */
    public List<MergeRecord> getRecordsForTarget(String targetEntityId) {
        return records.stream()
                .filter(r -> r.targetEntityId().equals(targetEntityId))
                .collect(Collectors.toList());
    }

    /**
     * Gets merge records for a specific source entity.
     */
    public List<MergeRecord> getRecordsForSource(String sourceEntityId) {
        return records.stream()
                .filter(r -> r.sourceEntityId().equals(sourceEntityId))
                .collect(Collectors.toList());
    }

    /**
     * Gets merge records within a time range.
     */
    public List<MergeRecord> getRecordsBetween(Instant start, Instant end) {
        return records.stream()
                .filter(r -> !r.timestamp().isBefore(start) && !r.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    /**
     * Gets merge records by decision type.
     */
    public List<MergeRecord> getRecordsByDecision(MatchDecision decision) {
        return records.stream()
                .filter(r -> r.decision() == decision)
                .collect(Collectors.toList());
    }

    /**
     * Gets merge records triggered by a specific actor.
     */
    public List<MergeRecord> getRecordsByActor(String triggeredBy) {
        return records.stream()
                .filter(r -> triggeredBy.equals(r.triggeredBy()))
                .collect(Collectors.toList());
    }

    /**
     * Gets the total number of merges recorded.
     */
    public int size() {
        return records.size();
    }

    /**
     * Gets the full merge chain for an entity (all entities that were merged into it).
     */
    public List<String> getMergeChain(String entityId) {
        List<String> chain = new ArrayList<>();
        collectMergeChain(entityId, chain);
        return chain;
    }

    private void collectMergeChain(String entityId, List<String> chain) {
        List<MergeRecord> mergesInto = getRecordsForTarget(entityId);
        for (MergeRecord record : mergesInto) {
            chain.add(record.sourceEntityId());
            collectMergeChain(record.sourceEntityId(), chain);
        }
    }
}
