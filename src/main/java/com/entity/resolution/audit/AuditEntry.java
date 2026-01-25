package com.entity.resolution.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of an auditable operation.
 */
public record AuditEntry(
        String id,
        AuditAction action,
        String entityId,
        String actorId,
        Map<String, Object> details,
        Instant timestamp
) {
    public AuditEntry {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(timestamp, "timestamp is required");
        details = details != null ? Map.copyOf(details) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private AuditAction action;
        private String entityId;
        private String actorId;
        private Map<String, Object> details;
        private Instant timestamp = Instant.now();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder action(AuditAction action) {
            this.action = action;
            return this;
        }

        public Builder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(id, action, entityId, actorId, details, timestamp);
        }
    }
}
