package com.entity.resolution.review;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a review item in the manual review queue.
 * Created when entity resolution produces a match with confidence
 * in the review range (typically 0.60 &lt;= score &lt; 0.80).
 */
public class ReviewItem {

    private final String id;
    private final String sourceEntityId;
    private final String candidateEntityId;
    private final String sourceEntityName;
    private final String candidateEntityName;
    private final String entityType;
    private final double similarityScore;
    private ReviewStatus status;
    private final Instant submittedAt;
    private Instant reviewedAt;
    private String reviewerId;
    private String notes;

    private ReviewItem(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.sourceEntityId = Objects.requireNonNull(builder.sourceEntityId, "sourceEntityId is required");
        this.candidateEntityId = Objects.requireNonNull(builder.candidateEntityId, "candidateEntityId is required");
        this.sourceEntityName = builder.sourceEntityName;
        this.candidateEntityName = builder.candidateEntityName;
        this.entityType = builder.entityType;
        this.similarityScore = builder.similarityScore;
        this.status = builder.status != null ? builder.status : ReviewStatus.PENDING;
        this.submittedAt = builder.submittedAt != null ? builder.submittedAt : Instant.now();
        this.reviewedAt = builder.reviewedAt;
        this.reviewerId = builder.reviewerId;
        this.notes = builder.notes;
    }

    public String getId() {
        return id;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public String getCandidateEntityId() {
        return candidateEntityId;
    }

    public String getSourceEntityName() {
        return sourceEntityName;
    }

    public String getCandidateEntityName() {
        return candidateEntityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isPending() {
        return status == ReviewStatus.PENDING;
    }

    public boolean isApproved() {
        return status == ReviewStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == ReviewStatus.REJECTED;
    }

    void markApproved(String reviewerId, String notes) {
        this.status = ReviewStatus.APPROVED;
        this.reviewedAt = Instant.now();
        this.reviewerId = reviewerId;
        this.notes = notes;
    }

    void markRejected(String reviewerId, String notes) {
        this.status = ReviewStatus.REJECTED;
        this.reviewedAt = Instant.now();
        this.reviewerId = reviewerId;
        this.notes = notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewItem that = (ReviewItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ReviewItem{" +
                "id='" + id + '\'' +
                ", sourceEntityId='" + sourceEntityId + '\'' +
                ", candidateEntityId='" + candidateEntityId + '\'' +
                ", score=" + similarityScore +
                ", status=" + status +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String sourceEntityId;
        private String candidateEntityId;
        private String sourceEntityName;
        private String candidateEntityName;
        private String entityType;
        private double similarityScore;
        private ReviewStatus status;
        private Instant submittedAt;
        private Instant reviewedAt;
        private String reviewerId;
        private String notes;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sourceEntityId(String sourceEntityId) {
            this.sourceEntityId = sourceEntityId;
            return this;
        }

        public Builder candidateEntityId(String candidateEntityId) {
            this.candidateEntityId = candidateEntityId;
            return this;
        }

        public Builder sourceEntityName(String sourceEntityName) {
            this.sourceEntityName = sourceEntityName;
            return this;
        }

        public Builder candidateEntityName(String candidateEntityName) {
            this.candidateEntityName = candidateEntityName;
            return this;
        }

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder similarityScore(double similarityScore) {
            this.similarityScore = similarityScore;
            return this;
        }

        public Builder status(ReviewStatus status) {
            this.status = status;
            return this;
        }

        public Builder submittedAt(Instant submittedAt) {
            this.submittedAt = submittedAt;
            return this;
        }

        public Builder reviewedAt(Instant reviewedAt) {
            this.reviewedAt = reviewedAt;
            return this;
        }

        public Builder reviewerId(String reviewerId) {
            this.reviewerId = reviewerId;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public ReviewItem build() {
            return new ReviewItem(this);
        }
    }
}
