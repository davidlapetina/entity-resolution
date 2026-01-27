package com.entity.resolution.review;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;

/**
 * Interface for managing the manual review queue.
 * Review items are created when entity resolution produces matches
 * with confidence in the review range (typically 0.60 <= score < 0.80).
 */
public interface ReviewQueue {

    /**
     * Submits a review item to the queue.
     *
     * @param item the review item to submit
     * @return the submitted review item with generated ID
     */
    ReviewItem submit(ReviewItem item);

    /**
     * Gets pending review items with pagination.
     *
     * @param page pagination parameters
     * @return a page of pending review items
     */
    Page<ReviewItem> getPending(PageRequest page);

    /**
     * Gets review items filtered by entity type with pagination.
     *
     * @param entityType the entity type to filter by
     * @param page       pagination parameters
     * @return a page of matching review items
     */
    Page<ReviewItem> getPendingByEntityType(String entityType, PageRequest page);

    /**
     * Gets review items filtered by score range with pagination.
     *
     * @param minScore minimum similarity score (inclusive)
     * @param maxScore maximum similarity score (inclusive)
     * @param page     pagination parameters
     * @return a page of matching review items
     */
    Page<ReviewItem> getPendingByScoreRange(double minScore, double maxScore, PageRequest page);

    /**
     * Approves a review item, triggering a merge of source into candidate.
     *
     * @param reviewId   the review item ID
     * @param reviewerId the reviewer's identifier
     * @param notes      optional notes about the decision
     */
    void approve(String reviewId, String reviewerId, String notes);

    /**
     * Rejects a review item, marking the entities as not the same.
     *
     * @param reviewId   the review item ID
     * @param reviewerId the reviewer's identifier
     * @param notes      optional notes about the decision
     */
    void reject(String reviewId, String reviewerId, String notes);

    /**
     * Gets a review item by ID.
     *
     * @param reviewId the review item ID
     * @return the review item, or null if not found
     */
    ReviewItem get(String reviewId);

    /**
     * Counts the number of pending review items.
     *
     * @return the count of pending items
     */
    long countPending();
}
