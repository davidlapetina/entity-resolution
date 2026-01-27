package com.entity.resolution.review;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import com.entity.resolution.audit.AuditAction;
import com.entity.resolution.audit.AuditService;
import com.entity.resolution.core.model.*;
import com.entity.resolution.merge.MergeEngine;
import com.entity.resolution.merge.MergeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private ReviewService reviewService;
    private InMemoryReviewQueue reviewQueue;

    @Mock
    private MergeEngine mergeEngine;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        reviewQueue = new InMemoryReviewQueue();
        auditService = new AuditService();
        reviewService = new ReviewService(reviewQueue, mergeEngine, auditService);
    }

    @Test
    @DisplayName("Should submit review item and create audit entry")
    void testSubmitForReview() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        ReviewItem submitted = reviewService.submitForReview(item);

        assertNotNull(submitted);
        assertEquals(ReviewStatus.PENDING, submitted.getStatus());

        // Verify audit entry was created
        var auditEntries = auditService.getEntriesByAction(AuditAction.MANUAL_REVIEW_REQUESTED);
        assertEquals(1, auditEntries.size());
        assertEquals("src-1", auditEntries.get(0).entityId());
    }

    @Test
    @DisplayName("Should approve match and trigger merge")
    void testApproveMatch() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        reviewService.submitForReview(item);

        Entity target = Entity.builder()
                .id("cand-1")
                .canonicalName("Target Corp")
                .type(EntityType.COMPANY)
                .build();
        MergeResult mergeResult = MergeResult.success(target, null, null, null, null);
        when(mergeEngine.merge(eq("src-1"), eq("cand-1"), any(), eq("reviewer-1")))
                .thenReturn(mergeResult);

        MergeResult result = reviewService.approveMatch(item.getId(), "reviewer-1", "Confirmed match");

        assertTrue(result.isSuccess());
        verify(mergeEngine).merge(eq("src-1"), eq("cand-1"), any(), eq("reviewer-1"));

        // Verify audit entry for review completion
        var completedEntries = auditService.getEntriesByAction(AuditAction.MANUAL_REVIEW_COMPLETED);
        assertEquals(1, completedEntries.size());
        assertEquals("APPROVED", completedEntries.get(0).details().get("decision"));
    }

    @Test
    @DisplayName("Should reject match without triggering merge")
    void testRejectMatch() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        reviewService.submitForReview(item);

        reviewService.rejectMatch(item.getId(), "reviewer-1", "Not the same entity");

        // Verify no merge was triggered
        verifyNoInteractions(mergeEngine);

        // Verify audit entry
        var completedEntries = auditService.getEntriesByAction(AuditAction.MANUAL_REVIEW_COMPLETED);
        assertEquals(1, completedEntries.size());
        assertEquals("REJECTED", completedEntries.get(0).details().get("decision"));
    }

    @Test
    @DisplayName("Should get pending reviews with pagination")
    void testGetPendingReviews() {
        for (int i = 0; i < 5; i++) {
            reviewService.submitForReview(createReviewItem("src-" + i, "cand-" + i, 0.60 + i * 0.03));
        }

        Page<ReviewItem> page = reviewService.getPendingReviews(PageRequest.of(0, 3));
        assertEquals(3, page.numberOfElements());
        assertEquals(5, page.totalElements());
    }

    @Test
    @DisplayName("Should get pending count")
    void testGetPendingCount() {
        reviewService.submitForReview(createReviewItem("src-1", "cand-1", 0.72));
        reviewService.submitForReview(createReviewItem("src-2", "cand-2", 0.65));

        assertEquals(2, reviewService.getPendingCount());
    }

    @Test
    @DisplayName("Should throw for non-existent review item on approve")
    void testApproveNonExistent() {
        assertThrows(IllegalArgumentException.class, () ->
                reviewService.approveMatch("nonexistent", "reviewer", null));
    }

    @Test
    @DisplayName("Should throw for already processed review item")
    void testApproveAlreadyProcessed() {
        ReviewItem item = createReviewItem("src-1", "cand-1", 0.72);
        reviewService.submitForReview(item);

        Entity target = Entity.builder()
                .id("cand-1")
                .canonicalName("Target Corp")
                .type(EntityType.COMPANY)
                .build();
        when(mergeEngine.merge(anyString(), anyString(), any(), anyString()))
                .thenReturn(MergeResult.success(target, null, null, null, null));

        reviewService.approveMatch(item.getId(), "reviewer-1", null);
        assertThrows(IllegalStateException.class, () ->
                reviewService.approveMatch(item.getId(), "reviewer-2", null));
    }

    private ReviewItem createReviewItem(String sourceId, String candidateId, double score) {
        return ReviewItem.builder()
                .sourceEntityId(sourceId)
                .candidateEntityId(candidateId)
                .sourceEntityName("Source " + sourceId)
                .candidateEntityName("Candidate " + candidateId)
                .entityType("COMPANY")
                .similarityScore(score)
                .build();
    }
}
