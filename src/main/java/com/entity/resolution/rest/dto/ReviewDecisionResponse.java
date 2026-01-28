package com.entity.resolution.rest.dto;

import com.entity.resolution.decision.ReviewAction;
import com.entity.resolution.decision.ReviewDecision;

import java.time.Instant;

/**
 * REST response DTO for a review decision record.
 */
public record ReviewDecisionResponse(
        String id,
        String reviewId,
        ReviewAction action,
        String reviewerId,
        String rationale,
        Instant decidedAt
) {
    /**
     * Creates a response DTO from a domain model.
     */
    public static ReviewDecisionResponse from(ReviewDecision decision) {
        return new ReviewDecisionResponse(
                decision.getId(),
                decision.getReviewId(),
                decision.getAction(),
                decision.getReviewerId(),
                decision.getRationale(),
                decision.getDecidedAt()
        );
    }
}
