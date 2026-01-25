package com.entity.resolution.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable audit record for a merge operation.
 * Part of the merge ledger that tracks all entity merges with provenance.
 */
public record MergeRecord(
        String id,
        String sourceEntityId,
        String targetEntityId,
        String sourceEntityName,
        String targetEntityName,
        double confidenceScore,
        MatchDecision decision,
        String triggeredBy,
        String reasoning,
        Instant timestamp
) {
    public MergeRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId is required");
        Objects.requireNonNull(targetEntityId, "targetEntityId is required");
        Objects.requireNonNull(decision, "decision is required");
        Objects.requireNonNull(timestamp, "timestamp is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String sourceEntityId;
        private String targetEntityId;
        private String sourceEntityName;
        private String targetEntityName;
        private double confidenceScore;
        private MatchDecision decision;
        private String triggeredBy;
        private String reasoning;
        private Instant timestamp = Instant.now();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sourceEntityId(String sourceEntityId) {
            this.sourceEntityId = sourceEntityId;
            return this;
        }

        public Builder targetEntityId(String targetEntityId) {
            this.targetEntityId = targetEntityId;
            return this;
        }

        public Builder sourceEntityName(String sourceEntityName) {
            this.sourceEntityName = sourceEntityName;
            return this;
        }

        public Builder targetEntityName(String targetEntityName) {
            this.targetEntityName = targetEntityName;
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder decision(MatchDecision decision) {
            this.decision = decision;
            return this;
        }

        public Builder triggeredBy(String triggeredBy) {
            this.triggeredBy = triggeredBy;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MergeRecord build() {
            return new MergeRecord(
                    id, sourceEntityId, targetEntityId, sourceEntityName, targetEntityName,
                    confidenceScore, decision, triggeredBy, reasoning, timestamp
            );
        }
    }
}
