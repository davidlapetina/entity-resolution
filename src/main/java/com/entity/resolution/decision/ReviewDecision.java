package com.entity.resolution.decision;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a human review decision.
 * Created when a reviewer approves or rejects a review item.
 * Persisted to the graph as a first-class signal node, linked to both
 * the review item and the original match decision.
 *
 * <p>ReviewDecisions are append-only: they never mutate the MatchDecisionRecord
 * they reference. Instead, they serve as confirming or contradicting evidence.</p>
 */
public final class ReviewDecision {

    private final String id;
    private final String reviewId;
    private final ReviewAction action;
    private final String reviewerId;
    private final String rationale;
    private final Instant decidedAt;

    private ReviewDecision(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.reviewId = Objects.requireNonNull(builder.reviewId, "reviewId is required");
        this.action = Objects.requireNonNull(builder.action, "action is required");
        this.reviewerId = Objects.requireNonNull(builder.reviewerId, "reviewerId is required");
        this.rationale = builder.rationale;
        this.decidedAt = builder.decidedAt != null ? builder.decidedAt : Instant.now();
    }

    public String getId() { return id; }
    public String getReviewId() { return reviewId; }
    public ReviewAction getAction() { return action; }
    public String getReviewerId() { return reviewerId; }
    public String getRationale() { return rationale; }
    public Instant getDecidedAt() { return decidedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewDecision that = (ReviewDecision) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ReviewDecision{" +
                "id='" + id + '\'' +
                ", reviewId='" + reviewId + '\'' +
                ", action=" + action +
                ", reviewerId='" + reviewerId + '\'' +
                ", decidedAt=" + decidedAt +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String reviewId;
        private ReviewAction action;
        private String reviewerId;
        private String rationale;
        private Instant decidedAt;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder reviewId(String reviewId) { this.reviewId = reviewId; return this; }
        public Builder action(ReviewAction action) { this.action = action; return this; }
        public Builder reviewerId(String reviewerId) { this.reviewerId = reviewerId; return this; }
        public Builder rationale(String rationale) { this.rationale = rationale; return this; }
        public Builder decidedAt(Instant decidedAt) { this.decidedAt = decidedAt; return this; }

        public ReviewDecision build() {
            return new ReviewDecision(this);
        }
    }
}
