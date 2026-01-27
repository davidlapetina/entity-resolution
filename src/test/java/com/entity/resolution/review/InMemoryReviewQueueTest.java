package com.entity.resolution.review;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryReviewQueueTest {

    private InMemoryReviewQueue queue;

    @BeforeEach
    void setUp() {
        queue = new InMemoryReviewQueue();
    }

    @Test
    @DisplayName("Should submit review item")
    void testSubmit() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        ReviewItem submitted = queue.submit(item);

        assertNotNull(submitted);
        assertEquals(item.getId(), submitted.getId());
        assertEquals(ReviewStatus.PENDING, submitted.getStatus());
    }

    @Test
    @DisplayName("Should get pending items with pagination")
    void testGetPending() {
        for (int i = 0; i < 5; i++) {
            queue.submit(createReviewItem("src-" + i, "cand-" + i, 0.60 + i * 0.03));
        }

        Page<ReviewItem> page = queue.getPending(PageRequest.of(0, 3));
        assertEquals(3, page.numberOfElements());
        assertEquals(5, page.totalElements());
        assertTrue(page.hasNext());

        Page<ReviewItem> page2 = queue.getPending(PageRequest.of(1, 3));
        assertEquals(2, page2.numberOfElements());
        assertFalse(page2.hasNext());
    }

    @Test
    @DisplayName("Should filter by entity type")
    void testFilterByEntityType() {
        queue.submit(createReviewItem("src-1", "cand-1", 0.72, "COMPANY"));
        queue.submit(createReviewItem("src-2", "cand-2", 0.68, "PERSON"));
        queue.submit(createReviewItem("src-3", "cand-3", 0.75, "COMPANY"));

        Page<ReviewItem> companies = queue.getPendingByEntityType("COMPANY", PageRequest.of(0, 10));
        assertEquals(2, companies.totalElements());

        Page<ReviewItem> persons = queue.getPendingByEntityType("PERSON", PageRequest.of(0, 10));
        assertEquals(1, persons.totalElements());
    }

    @Test
    @DisplayName("Should filter by score range")
    void testFilterByScoreRange() {
        queue.submit(createReviewItem("src-1", "cand-1", 0.60));
        queue.submit(createReviewItem("src-2", "cand-2", 0.70));
        queue.submit(createReviewItem("src-3", "cand-3", 0.75));
        queue.submit(createReviewItem("src-4", "cand-4", 0.65));

        Page<ReviewItem> highScores = queue.getPendingByScoreRange(0.70, 1.0, PageRequest.of(0, 10));
        assertEquals(2, highScores.totalElements());
    }

    @Test
    @DisplayName("Should approve review item")
    void testApprove() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        queue.submit(item);

        queue.approve(item.getId(), "reviewer-1", "Looks correct");

        ReviewItem approved = queue.get(item.getId());
        assertEquals(ReviewStatus.APPROVED, approved.getStatus());
        assertEquals("reviewer-1", approved.getReviewerId());
        assertEquals("Looks correct", approved.getNotes());
        assertNotNull(approved.getReviewedAt());
    }

    @Test
    @DisplayName("Should reject review item")
    void testReject() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        queue.submit(item);

        queue.reject(item.getId(), "reviewer-1", "Different entities");

        ReviewItem rejected = queue.get(item.getId());
        assertEquals(ReviewStatus.REJECTED, rejected.getStatus());
    }

    @Test
    @DisplayName("Should not allow double approval")
    void testDoubleApproval() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        queue.submit(item);
        queue.approve(item.getId(), "reviewer-1", null);

        assertThrows(IllegalStateException.class, () ->
                queue.approve(item.getId(), "reviewer-2", null));
    }

    @Test
    @DisplayName("Should not approve non-existent item")
    void testApproveNonExistent() {
        assertThrows(IllegalArgumentException.class, () ->
                queue.approve("nonexistent", "reviewer", null));
    }

    @Test
    @DisplayName("Should count pending items correctly")
    void testCountPending() {
        queue.submit(createReviewItem("src-1", "cand-1", 0.72));
        queue.submit(createReviewItem("src-2", "cand-2", 0.68));
        assertEquals(2, queue.countPending());

        queue.approve(queue.getPending(PageRequest.first(1)).content().get(0).getId(),
                "reviewer", null);
        assertEquals(1, queue.countPending());
    }

    @Test
    @DisplayName("Approved items should not appear in pending list")
    void testApprovedNotInPending() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        queue.submit(item);
        queue.submit(createReviewItem("src-2", "cand-2", 0.65));
        queue.approve(item.getId(), "reviewer", null);

        Page<ReviewItem> pending = queue.getPending(PageRequest.of(0, 10));
        assertEquals(1, pending.totalElements());
        assertNotEquals(item.getId(), pending.content().get(0).getId());
    }

    @Test
    @DisplayName("Should get review item by ID")
    void testGetById() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        queue.submit(item);

        ReviewItem found = queue.get(item.getId());
        assertNotNull(found);
        assertEquals(item.getSourceEntityId(), found.getSourceEntityId());

        assertNull(queue.get("nonexistent"));
    }

    private ReviewItem createReviewItem(String sourceId, String candidateId, double score) {
        return createReviewItem(sourceId, candidateId, score, "COMPANY");
    }

    private ReviewItem createReviewItem(String sourceId, String candidateId, double score, String entityType) {
        return ReviewItem.builder()
                .sourceEntityId(sourceId)
                .candidateEntityId(candidateId)
                .sourceEntityName("Source " + sourceId)
                .candidateEntityName("Candidate " + candidateId)
                .entityType(entityType)
                .similarityScore(score)
                .build();
    }
}
