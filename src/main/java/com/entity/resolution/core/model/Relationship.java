package com.entity.resolution.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Library-managed relationship between two entities.
 *
 * All relationships created through this library are tracked here, ensuring
 * they can be properly migrated when entities are merged.
 */
public final class Relationship {

    private final String id;
    private final String sourceEntityId;
    private final String targetEntityId;
    private final String relationshipType;
    private final Map<String, Object> properties;
    private final Instant createdAt;
    private final String createdBy;

    private Relationship(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.sourceEntityId = Objects.requireNonNull(builder.sourceEntityId, "sourceEntityId is required");
        this.targetEntityId = Objects.requireNonNull(builder.targetEntityId, "targetEntityId is required");
        this.relationshipType = Objects.requireNonNull(builder.relationshipType, "relationshipType is required");
        this.properties = builder.properties != null ? Map.copyOf(builder.properties) : Map.of();
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.createdBy = builder.createdBy;
    }

    public String getId() {
        return id;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public String getTargetEntityId() {
        return targetEntityId;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Relationship that = (Relationship) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Relationship{" +
                "id='" + id + '\'' +
                ", sourceEntityId='" + sourceEntityId + '\'' +
                ", targetEntityId='" + targetEntityId + '\'' +
                ", type='" + relationshipType + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String sourceEntityId;
        private String targetEntityId;
        private String relationshipType;
        private Map<String, Object> properties;
        private Instant createdAt;
        private String createdBy;

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

        public Builder relationshipType(String relationshipType) {
            this.relationshipType = relationshipType;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Relationship build() {
            return new Relationship(this);
        }
    }
}
