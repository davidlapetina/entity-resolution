package com.entity.resolution.review;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReviewItemTest {

    @Test
    @DisplayName("Should create review item with builder")
    void testBuilder() {
        ReviewItem item = ReviewItem.builder()
                .sourceEntityId("src-1")
                .candidateEntityId("cand-1")
                .sourceEntityName("Source Corp")
                .candidateEntityName("Candidate Corp")
                .entityType("COMPANY")
                .similarityScore(0.72)
                .build();

        assertNotNull(item.getId());
        assertEquals("src-1", item.getSourceEntityId());
        assertEquals("cand-1", item.getCandidateEntityId());
        assertEquals("Source Corp", item.getSourceEntityName());
        assertEquals("Candidate Corp", item.getCandidateEntityName());
        assertEquals("COMPANY", item.getEntityType());
        assertEquals(0.72, item.getSimilarityScore());
        assertEquals(ReviewStatus.PENDING, item.getStatus());
        assertNotNull(item.getSubmittedAt());
        assertTrue(item.isPending());
        assertFalse(item.isApproved());
        assertFalse(item.isRejected());
    }

    @Test
    @DisplayName("Should require sourceEntityId")
    void testRequireSource() {
        assertThrows(NullPointerException.class, () ->
                ReviewItem.builder()
                        .candidateEntityId("cand-1")
                        .build());
    }

    @Test
    @DisplayName("Should require candidateEntityId")
    void testRequireCandidate() {
        assertThrows(NullPointerException.class, () ->
                ReviewItem.builder()
                        .sourceEntityId("src-1")
                        .build());
    }

    @Test
    @DisplayName("Should mark approved correctly")
    void testMarkApproved() {
        ReviewItem item = ReviewItem.builder()
                .sourceEntityId("src-1")
                .candidateEntityId("cand-1")
                .build();

        item.markApproved("reviewer-1", "Looks correct");

        assertTrue(item.isApproved());
        assertFalse(item.isPending());
        assertEquals("reviewer-1", item.getReviewerId());
        assertEquals("Looks correct", item.getNotes());
        assertNotNull(item.getReviewedAt());
    }

    @Test
    @DisplayName("Should mark rejected correctly")
    void testMarkRejected() {
        ReviewItem item = ReviewItem.builder()
                .sourceEntityId("src-1")
                .candidateEntityId("cand-1")
                .build();

        item.markRejected("reviewer-1", "Different entities");

        assertTrue(item.isRejected());
        assertFalse(item.isPending());
        assertEquals("reviewer-1", item.getReviewerId());
    }

    @Test
    @DisplayName("Should support custom ID")
    void testCustomId() {
        ReviewItem item = ReviewItem.builder()
                .id("custom-id")
                .sourceEntityId("src-1")
                .candidateEntityId("cand-1")
                .build();

        assertEquals("custom-id", item.getId());
    }

    @Test
    @DisplayName("Should support custom submittedAt")
    void testCustomSubmittedAt() {
        Instant customTime = Instant.parse("2024-01-01T00:00:00Z");
        ReviewItem item = ReviewItem.builder()
                .sourceEntityId("src-1")
                .candidateEntityId("cand-1")
                .submittedAt(customTime)
                .build();

        assertEquals(customTime, item.getSubmittedAt());
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on ID")
    void testEquality() {
        ReviewItem item1 = ReviewItem.builder()
                .id("same-id")
                .sourceEntityId("src-1")
                .candidateEntityId("cand-1")
                .build();

        ReviewItem item2 = ReviewItem.builder()
                .id("same-id")
                .sourceEntityId("src-2")
                .candidateEntityId("cand-2")
                .build();

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());
    }

    @Test
    @DisplayName("Should have meaningful toString")
    void testToString() {
        ReviewItem item = ReviewItem.builder()
                .sourceEntityId("src-1")
                .candidateEntityId("cand-1")
                .similarityScore(0.72)
                .build();

        String s = item.toString();
        assertTrue(s.contains("src-1"));
        assertTrue(s.contains("cand-1"));
        assertTrue(s.contains("0.72"));
        assertTrue(s.contains("PENDING"));
    }
}
