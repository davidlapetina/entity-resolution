package com.entity.resolution.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an entity node in the graph.
 * Core domain object for entity resolution.
 */
public class Entity {
    private final String id;
    private String canonicalName;
    private String normalizedName;
    private EntityType type;
    private double confidenceScore;
    private EntityStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Entity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.canonicalName = builder.canonicalName;
        this.normalizedName = builder.normalizedName;
        this.type = builder.type;
        this.confidenceScore = builder.confidenceScore;
        this.status = builder.status != null ? builder.status : EntityStatus.ACTIVE;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.updatedAt = builder.updatedAt != null ? builder.updatedAt : this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    public void setCanonicalName(String canonicalName) {
        this.canonicalName = canonicalName;
        this.updatedAt = Instant.now();
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
        this.updatedAt = Instant.now();
    }

    public EntityType getType() {
        return type;
    }

    public void setType(EntityType type) {
        this.type = type;
        this.updatedAt = Instant.now();
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
        this.updatedAt = Instant.now();
    }

    public EntityStatus getStatus() {
        return status;
    }

    public void setStatus(EntityStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return status == EntityStatus.ACTIVE;
    }

    public boolean isMerged() {
        return status == EntityStatus.MERGED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Entity{" +
                "id='" + id + '\'' +
                ", canonicalName='" + canonicalName + '\'' +
                ", normalizedName='" + normalizedName + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", confidenceScore=" + confidenceScore +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(Entity entity) {
        return new Builder()
                .id(entity.id)
                .canonicalName(entity.canonicalName)
                .normalizedName(entity.normalizedName)
                .type(entity.type)
                .confidenceScore(entity.confidenceScore)
                .status(entity.status)
                .createdAt(entity.createdAt)
                .updatedAt(entity.updatedAt);
    }

    public static class Builder {
        private String id;
        private String canonicalName;
        private String normalizedName;
        private EntityType type;
        private double confidenceScore = 1.0;
        private EntityStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder canonicalName(String canonicalName) {
            this.canonicalName = canonicalName;
            return this;
        }

        public Builder normalizedName(String normalizedName) {
            this.normalizedName = normalizedName;
            return this;
        }

        public Builder type(EntityType type) {
            this.type = type;
            return this;
        }

        public Builder confidenceScore(double confidenceScore) {
            this.confidenceScore = confidenceScore;
            return this;
        }

        public Builder status(EntityStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Entity build() {
            Objects.requireNonNull(canonicalName, "canonicalName is required");
            Objects.requireNonNull(type, "type is required");
            return new Entity(this);
        }
    }
}
