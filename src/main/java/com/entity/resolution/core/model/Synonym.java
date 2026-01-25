package com.entity.resolution.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a synonym node linked to an entity.
 * Synonyms capture alternative names for the same entity.
 */
public class Synonym {
    private final String id;
    private String value;
    private String normalizedValue;
    private SynonymSource source;
    private double confidence;
    private Instant createdAt;

    private Synonym(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.value = builder.value;
        this.normalizedValue = builder.normalizedValue;
        this.source = builder.source;
        this.confidence = builder.confidence;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getNormalizedValue() {
        return normalizedValue;
    }

    public void setNormalizedValue(String normalizedValue) {
        this.normalizedValue = normalizedValue;
    }

    public SynonymSource getSource() {
        return source;
    }

    public void setSource(SynonymSource source) {
        this.source = source;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Synonym synonym = (Synonym) o;
        return Objects.equals(id, synonym.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Synonym{" +
                "id='" + id + '\'' +
                ", value='" + value + '\'' +
                ", normalizedValue='" + normalizedValue + '\'' +
                ", source=" + source +
                ", confidence=" + confidence +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String value;
        private String normalizedValue;
        private SynonymSource source = SynonymSource.SYSTEM;
        private double confidence = 1.0;
        private Instant createdAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder normalizedValue(String normalizedValue) {
            this.normalizedValue = normalizedValue;
            return this;
        }

        public Builder source(SynonymSource source) {
            this.source = source;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Synonym build() {
            Objects.requireNonNull(value, "value is required");
            return new Synonym(this);
        }
    }
}
