package com.entity.resolution.decision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReviewDecisionTest {

    @Test
    @DisplayName("Should build valid approve ReviewDecision")
    void buildApproveDecision() {
        ReviewDecision decision = ReviewDecision.builder()
                .reviewId("review-123")
                .action(ReviewAction.APPROVE)
                .reviewerId("reviewer-1")
                .rationale("Confirmed same entity")
                .build();

        assertNotNull(decision.getId());
        assertEquals("review-123", decision.getReviewId());
        assertEquals(ReviewAction.APPROVE, decision.getAction());
        assertEquals("reviewer-1", decision.getReviewerId());
        assertEquals("Confirmed same entity", decision.getRationale());
        assertNotNull(decision.getDecidedAt());
    }

    @Test
    @DisplayName("Should build valid reject ReviewDecision")
    void buildRejectDecision() {
        ReviewDecision decision = ReviewDecision.builder()
                .reviewId("review-456")
                .action(ReviewAction.REJECT)
                .reviewerId("reviewer-2")
                .rationale("Different entities")
                .build();

        assertEquals(ReviewAction.REJECT, decision.getAction());
    }

    @Test
    @DisplayName("Should auto-generate ID")
    void autoGenerateId() {
        ReviewDecision d1 = buildMinimalDecision();
        ReviewDecision d2 = buildMinimalDecision();

        assertNotEquals(d1.getId(), d2.getId());
    }

    @Test
    @DisplayName("Should use provided ID")
    void useProvidedId() {
        ReviewDecision decision = ReviewDecision.builder()
                .id("custom-id")
                .reviewId("review-1")
                .action(ReviewAction.APPROVE)
                .reviewerId("reviewer-1")
                .build();

        assertEquals("custom-id", decision.getId());
    }

    @Test
    @DisplayName("Should require reviewId")
    void requireReviewId() {
        assertThrows(NullPointerException.class, () ->
                ReviewDecision.builder()
                        .action(ReviewAction.APPROVE)
                        .reviewerId("reviewer-1")
                        .build());
    }

    @Test
    @DisplayName("Should require action")
    void requireAction() {
        assertThrows(NullPointerException.class, () ->
                ReviewDecision.builder()
                        .reviewId("review-1")
                        .reviewerId("reviewer-1")
                        .build());
    }

    @Test
    @DisplayName("Should require reviewerId")
    void requireReviewerId() {
        assertThrows(NullPointerException.class, () ->
                ReviewDecision.builder()
                        .reviewId("review-1")
                        .action(ReviewAction.APPROVE)
                        .build());
    }

    @Test
    @DisplayName("Rationale should be nullable")
    void rationaleNullable() {
        ReviewDecision decision = ReviewDecision.builder()
                .reviewId("review-1")
                .action(ReviewAction.APPROVE)
                .reviewerId("reviewer-1")
                .build();

        assertNull(decision.getRationale());
    }

    @Test
    @DisplayName("Should set custom decidedAt")
    void setCustomDecidedAt() {
        Instant custom = Instant.parse("2024-06-01T12:00:00Z");
        ReviewDecision decision = ReviewDecision.builder()
                .reviewId("review-1")
                .action(ReviewAction.REJECT)
                .reviewerId("reviewer-1")
                .decidedAt(custom)
                .build();

        assertEquals(custom, decision.getDecidedAt());
    }

    @Test
    @DisplayName("Equals and hashCode based on ID")
    void equalsAndHashCode() {
        ReviewDecision d1 = ReviewDecision.builder()
                .id("same-id")
                .reviewId("review-1")
                .action(ReviewAction.APPROVE)
                .reviewerId("reviewer-1")
                .build();

        ReviewDecision d2 = ReviewDecision.builder()
                .id("same-id")
                .reviewId("review-2")
                .action(ReviewAction.REJECT)
                .reviewerId("reviewer-2")
                .build();

        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    @DisplayName("All ReviewAction values should be valid")
    void allReviewActionsValid() {
        ReviewAction[] actions = ReviewAction.values();
        assertEquals(2, actions.length);
        assertNotNull(ReviewAction.APPROVE);
        assertNotNull(ReviewAction.REJECT);
    }

    private ReviewDecision buildMinimalDecision() {
        return ReviewDecision.builder()
                .reviewId("review-1")
                .action(ReviewAction.APPROVE)
                .reviewerId("reviewer-1")
                .build();
    }
}
