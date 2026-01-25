package com.entity.resolution.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a duplicate entity node that has been identified as a duplicate of a canonical entity.
 * Per PRD section 9, nodes are never physically deleted - duplicates are preserved with links to canonical.
 */
public class DuplicateEntity {
    private final String id;
    private String originalName;
    private String normalizedName;
    private String sourceSystem;
    private Instant createdAt;

    private DuplicateEntity(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.originalName = builder.originalName;
        this.normalizedName = builder.normalizedName;
        this.sourceSystem = builder.sourceSystem;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DuplicateEntity that = (DuplicateEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DuplicateEntity{" +
                "id='" + id + '\'' +
                ", originalName='" + originalName + '\'' +
                ", normalizedName='" + normalizedName + '\'' +
                ", sourceSystem='" + sourceSystem + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String originalName;
        private String normalizedName;
        private String sourceSystem;
        private Instant createdAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder originalName(String originalName) {
            this.originalName = originalName;
            return this;
        }

        public Builder normalizedName(String normalizedName) {
            this.normalizedName = normalizedName;
            return this;
        }

        public Builder sourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public DuplicateEntity build() {
            Objects.requireNonNull(originalName, "originalName is required");
            return new DuplicateEntity(this);
        }
    }
}
