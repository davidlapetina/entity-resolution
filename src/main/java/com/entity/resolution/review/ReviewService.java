package com.entity.resolution.review;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.core.model.MatchDecision;
import com.entity.resolution.core.model.MatchResult;
import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.decision.*;
import com.entity.resolution.graph.SynonymRepository;
import com.entity.resolution.merge.MergeEngine;
import com.entity.resolution.merge.MergeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    private final ReviewDecisionRepository reviewDecisionRepository;
    private final MatchDecisionRepository matchDecisionRepository;
    private final SynonymRepository synonymRepository;
    private final ConfidenceDecayEngine confidenceDecayEngine;

    /**
     * v1.0 backward-compatible constructor.
     */
    public ReviewService(ReviewQueue reviewQueue, MergeEngine mergeEngine, AuditService auditService) {
        this(reviewQueue, mergeEngine, auditService, null, null, null, null);
    }

    /**
     * v1.1 constructor with decision graph and confidence decay support.
     */
    public ReviewService(ReviewQueue reviewQueue, MergeEngine mergeEngine, AuditService auditService,
                         ReviewDecisionRepository reviewDecisionRepository,
                         MatchDecisionRepository matchDecisionRepository,
                         SynonymRepository synonymRepository,
                         ConfidenceDecayEngine confidenceDecayEngine) {
        this.reviewQueue = reviewQueue;
        this.mergeEngine = mergeEngine;
        this.auditService = auditService;
        this.reviewDecisionRepository = reviewDecisionRepository;
        this.matchDecisionRepository = matchDecisionRepository;
        this.synonymRepository = synonymRepository;
        this.confidenceDecayEngine = confidenceDecayEngine;
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
     * <p>v1.1: Creates an immutable {@link ReviewDecision} node linked to the
     * original {@link MatchDecisionRecord}. Reinforces synonyms involved in the match.</p>
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

        // v1.1: Create immutable ReviewDecision node
        createReviewDecisionNode(reviewId, item, ReviewAction.APPROVE, reviewerId, notes);

        // v1.1: Reinforce synonyms for the approved match
        reinforceOnApproval(item);

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
     * <p>v1.1: Creates an immutable {@link ReviewDecision} node linked to the
     * original {@link MatchDecisionRecord}. Applies negative reinforcement
     * to weaken candidate confidence.</p>
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

        // v1.1: Create immutable ReviewDecision node
        createReviewDecisionNode(reviewId, item, ReviewAction.REJECT, reviewerId, notes);

        // v1.1: Apply negative reinforcement
        applyNegativeReinforcement(item);

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

    /**
     * Gets the review decision repository (v1.1).
     */
    public ReviewDecisionRepository getReviewDecisionRepository() {
        return reviewDecisionRepository;
    }

    // ========== v1.1 Private Helpers ==========

    /**
     * Creates an immutable ReviewDecision node and links it to the MatchDecision.
     */
    private void createReviewDecisionNode(String reviewId, ReviewItem item,
                                           ReviewAction action, String reviewerId, String notes) {
        if (reviewDecisionRepository == null) {
            return;
        }

        try {
            ReviewDecision reviewDecision = ReviewDecision.builder()
                    .reviewId(reviewId)
                    .action(action)
                    .reviewerId(reviewerId)
                    .rationale(notes)
                    .build();

            // Find the MatchDecision linked to this review
            String matchDecisionId = null;
            if (matchDecisionRepository != null) {
                MatchDecisionRecord matchDecision = matchDecisionRepository.findByReviewEntities(
                        item.getSourceEntityId(), item.getCandidateEntityId());
                if (matchDecision != null) {
                    matchDecisionId = matchDecision.getId();
                }
            }

            reviewDecisionRepository.save(reviewDecision, matchDecisionId);
            log.debug("Created ReviewDecision {} action={} for review {}",
                    reviewDecision.getId(), action, reviewId);
        } catch (Exception e) {
            log.warn("Failed to persist ReviewDecision for review {}: {}", reviewId, e.getMessage());
        }
    }

    /**
     * Reinforces synonyms when a review is approved.
     * Increments supportCount and updates lastConfirmedAt for related synonyms.
     */
    private void reinforceOnApproval(ReviewItem item) {
        if (synonymRepository == null || confidenceDecayEngine == null) {
            return;
        }

        try {
            List<Synonym> synonyms = synonymRepository.findByEntityId(item.getCandidateEntityId());
            for (Synonym synonym : synonyms) {
                confidenceDecayEngine.reinforce(synonym);
                synonymRepository.reinforce(synonym.getId());
            }
            log.debug("Reinforced {} synonyms for approved review of entity {}",
                    synonyms.size(), item.getCandidateEntityId());
        } catch (Exception e) {
            log.warn("Failed to reinforce synonyms for approved review: {}", e.getMessage());
        }
    }

    /**
     * Applies negative reinforcement when a review is rejected.
     * Reduces confidence on synonyms that contributed to the false positive.
     */
    private void applyNegativeReinforcement(ReviewItem item) {
        if (synonymRepository == null || confidenceDecayEngine == null) {
            return;
        }

        try {
            List<Synonym> synonyms = synonymRepository.findByEntityId(item.getCandidateEntityId());
            for (Synonym synonym : synonyms) {
                confidenceDecayEngine.negativeReinforcement(synonym, 0.05);
                synonymRepository.updateConfidence(synonym.getId(), synonym.getConfidence());
            }
            log.debug("Applied negative reinforcement to {} synonyms for rejected review of entity {}",
                    synonyms.size(), item.getCandidateEntityId());
        } catch (Exception e) {
            log.warn("Failed to apply negative reinforcement for rejected review: {}", e.getMessage());
        }
    }
}
