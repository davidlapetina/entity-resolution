package com.entity.resolution.review;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory implementation of {@link ReviewQueue}.
 * Suitable for testing and single-JVM deployments.
 */
public class InMemoryReviewQueue implements ReviewQueue {
    private static final Logger log = LoggerFactory.getLogger(InMemoryReviewQueue.class);

    private final ConcurrentMap<String, ReviewItem> items = new ConcurrentHashMap<>();

    @Override
    public ReviewItem submit(ReviewItem item) {
        items.put(item.getId(), item);
        log.debug("Submitted review item {} (source={}, candidate={}, score={})",
                item.getId(), item.getSourceEntityId(), item.getCandidateEntityId(),
                item.getSimilarityScore());
        return item;
    }

    @Override
    public Page<ReviewItem> getPending(PageRequest page) {
        List<ReviewItem> pending = items.values().stream()
                .filter(ReviewItem::isPending)
                .sorted(Comparator.comparing(ReviewItem::getSubmittedAt))
                .toList();
        return paginate(pending, page);
    }

    @Override
    public Page<ReviewItem> getPendingByEntityType(String entityType, PageRequest page) {
        List<ReviewItem> filtered = items.values().stream()
                .filter(ReviewItem::isPending)
                .filter(item -> entityType.equals(item.getEntityType()))
                .sorted(Comparator.comparing(ReviewItem::getSubmittedAt))
                .toList();
        return paginate(filtered, page);
    }

    @Override
    public Page<ReviewItem> getPendingByScoreRange(double minScore, double maxScore, PageRequest page) {
        List<ReviewItem> filtered = items.values().stream()
                .filter(ReviewItem::isPending)
                .filter(item -> item.getSimilarityScore() >= minScore && item.getSimilarityScore() <= maxScore)
                .sorted(Comparator.comparingDouble(ReviewItem::getSimilarityScore).reversed())
                .toList();
        return paginate(filtered, page);
    }

    @Override
    public void approve(String reviewId, String reviewerId, String notes) {
        ReviewItem item = items.get(reviewId);
        if (item == null) {
            throw new IllegalArgumentException("Review item not found: " + reviewId);
        }
        if (!item.isPending()) {
            throw new IllegalStateException("Review item is not pending: " + reviewId);
        }
        item.markApproved(reviewerId, notes);
        log.info("Review item {} approved by {}", reviewId, reviewerId);
    }

    @Override
    public void reject(String reviewId, String reviewerId, String notes) {
        ReviewItem item = items.get(reviewId);
        if (item == null) {
            throw new IllegalArgumentException("Review item not found: " + reviewId);
        }
        if (!item.isPending()) {
            throw new IllegalStateException("Review item is not pending: " + reviewId);
        }
        item.markRejected(reviewerId, notes);
        log.info("Review item {} rejected by {}", reviewId, reviewerId);
    }

    @Override
    public ReviewItem get(String reviewId) {
        return items.get(reviewId);
    }

    @Override
    public long countPending() {
        return items.values().stream().filter(ReviewItem::isPending).count();
    }

    private Page<ReviewItem> paginate(List<ReviewItem> all, PageRequest page) {
        int total = all.size();
        int fromIndex = Math.min(page.offset(), total);
        int toIndex = Math.min(page.offset() + page.limit(), total);
        List<ReviewItem> content = all.subList(fromIndex, toIndex);
        return new Page<>(content, total, page.pageNumber(), page.limit());
    }
}
