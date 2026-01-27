package com.entity.resolution.rest.dto;

/**
 * Request DTO for approving or rejecting a review item.
 */
public record ReviewDecisionRequest(
        String reviewerId,
        String notes
) {
    public ReviewDecisionRequest {
        if (reviewerId == null || reviewerId.isBlank()) {
            throw new IllegalArgumentException("reviewerId is required");
        }
    }
}
