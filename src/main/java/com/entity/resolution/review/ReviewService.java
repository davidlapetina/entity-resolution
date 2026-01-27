package com.entity.resolution.review;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.MatchResult;
import com.entity.resolution.merge.MergeEngine;
import com.entity.resolution.merge.MergeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Service coordinating review queue operations with merge and audit subsystems.
 * Handles the approve/reject workflows including triggering merges on approval
 * and recording audit trails for all review decisions.
 */
public class ReviewService {
    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewQueue reviewQueue;
    private final MergeEngine mergeEngine;
    private final AuditService auditService;

    public ReviewService(ReviewQueue reviewQueue, MergeEngine mergeEngine, AuditService auditService) {
        this.reviewQueue = reviewQueue;
        this.mergeEngine = mergeEngine;
        this.auditService = auditService;
    }

    /**
     * Submits a review item to the queue and records an audit entry.
     *
     * @param item the review item to submit
     * @return the submitted review item
     */
    public ReviewItem submitForReview(ReviewItem item) {
        ReviewItem submitted = reviewQueue.submit(item);

        auditService.record(AuditAction.MANUAL_REVIEW_REQUESTED, item.getSourceEntityId(),
                "SYSTEM", Map.of(
                        "reviewItemId", submitted.getId(),
                        "candidateEntityId", item.getCandidateEntityId(),
                        "similarityScore", item.getSimilarityScore()
                ));

        log.info("review.submitted reviewItemId={} sourceEntityId={} candidateEntityId={} score={}",
                submitted.getId(), item.getSourceEntityId(),
                item.getCandidateEntityId(), item.getSimilarityScore());

        return submitted;
    }

    /**
     * Approves a review item, triggering a merge of the source entity into the candidate.
     *
     * @param reviewId   the review item ID
     * @param reviewerId the reviewer's identifier
     * @param notes      optional notes about the decision
     * @return the merge result, or null if merge was not applicable
     */
    public MergeResult approveMatch(String reviewId, String reviewerId, String notes) {
        ReviewItem item = reviewQueue.get(reviewId);
        if (item == null) {
            throw new IllegalArgumentException("Review item not found: " + reviewId);
        }
        if (!item.isPending()) {
            throw new IllegalStateException("Review item is not pending: " + reviewId);
        }

        // Approve in the queue
        reviewQueue.approve(reviewId, reviewerId, notes);

        // Record audit entry for the review decision
        auditService.record(AuditAction.MANUAL_REVIEW_COMPLETED, item.getSourceEntityId(),
                reviewerId, Map.of(
                        "reviewItemId", reviewId,
                        "decision", "APPROVED",
                        "candidateEntityId", item.getCandidateEntityId(),
                        "notes", notes != null ? notes : ""
                ));

        // Trigger merge: source entity merges into candidate entity
        MatchResult matchResult = new MatchResult(
                item.getSimilarityScore(),
                MatchDecision.AUTO_MERGE,
                item.getCandidateEntityId(),
                item.getCandidateEntityName(),
                "Manual review approved by " + reviewerId +
                        (notes != null && !notes.isEmpty() ? ": " + notes : "")
        );

        MergeResult mergeResult = mergeEngine.merge(
                item.getSourceEntityId(),
                item.getCandidateEntityId(),
                matchResult,
                reviewerId
        );

        log.info("review.approved reviewItemId={} mergeSuccess={}", reviewId, mergeResult.isSuccess());
        return mergeResult;
    }

    /**
     * Rejects a review item, marking the entities as not the same.
     *
     * @param reviewId   the review item ID
     * @param reviewerId the reviewer's identifier
     * @param notes      optional notes about the decision
     */
    public void rejectMatch(String reviewId, String reviewerId, String notes) {
        ReviewItem item = reviewQueue.get(reviewId);
        if (item == null) {
            throw new IllegalArgumentException("Review item not found: " + reviewId);
        }
        if (!item.isPending()) {
            throw new IllegalStateException("Review item is not pending: " + reviewId);
        }

        // Reject in the queue
        reviewQueue.reject(reviewId, reviewerId, notes);

        // Record audit entry for the review decision
        auditService.record(AuditAction.MANUAL_REVIEW_COMPLETED, item.getSourceEntityId(),
                reviewerId, Map.of(
                        "reviewItemId", reviewId,
                        "decision", "REJECTED",
                        "candidateEntityId", item.getCandidateEntityId(),
                        "notes", notes != null ? notes : ""
                ));

        log.info("review.rejected reviewItemId={} sourceEntityId={} candidateEntityId={}",
                reviewId, item.getSourceEntityId(), item.getCandidateEntityId());
    }

    /**
     * Gets pending review items with pagination.
     */
    public Page<ReviewItem> getPendingReviews(PageRequest page) {
        return reviewQueue.getPending(page);
    }

    /**
     * Gets pending reviews filtered by entity type.
     */
    public Page<ReviewItem> getPendingReviewsByEntityType(String entityType, PageRequest page) {
        return reviewQueue.getPendingByEntityType(entityType, page);
    }

    /**
     * Gets pending reviews filtered by score range.
     */
    public Page<ReviewItem> getPendingReviewsByScoreRange(double minScore, double maxScore, PageRequest page) {
        return reviewQueue.getPendingByScoreRange(minScore, maxScore, page);
    }

    /**
     * Gets a review item by ID.
     */
    public ReviewItem getReviewItem(String reviewId) {
        return reviewQueue.get(reviewId);
    }

    /**
     * Gets the count of pending review items.
     */
    public long getPendingCount() {
        return reviewQueue.countPending();
    }

    /**
     * Gets the underlying review queue.
     */
    public ReviewQueue getReviewQueue() {
        return reviewQueue;
    }
}
