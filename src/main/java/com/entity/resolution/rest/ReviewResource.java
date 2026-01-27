package com.entity.resolution.rest;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import com.entity.resolution.merge.MergeResult;
import com.entity.resolution.rest.dto.ErrorResponse;
import com.entity.resolution.rest.dto.ReviewDecisionRequest;
import com.entity.resolution.review.ReviewItem;
import com.entity.resolution.review.ReviewService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST resource for managing the manual review queue.
 */
@Path("/api/v1/reviews")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewResource {
    private static final Logger log = LoggerFactory.getLogger(ReviewResource.class);

    private final ReviewService reviewService;

    public ReviewResource(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Gets pending review items with pagination.
     *
     * GET /api/v1/reviews?page=0&size=20
     */
    @GET
    public Response getPendingReviews(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("entityType") String entityType,
            @QueryParam("minScore") Double minScore,
            @QueryParam("maxScore") Double maxScore) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            Page<ReviewItem> result;

            if (entityType != null && !entityType.isBlank()) {
                result = reviewService.getPendingReviewsByEntityType(entityType, pageRequest);
            } else if (minScore != null && maxScore != null) {
                result = reviewService.getPendingReviewsByScoreRange(minScore, maxScore, pageRequest);
            } else {
                result = reviewService.getPendingReviews(pageRequest);
            }

            return Response.ok(result).build();
        } catch (Exception e) {
            log.error("getPendingReviews.failed error={}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(), "/api/v1/reviews"))
                    .build();
        }
    }

    /**
     * Gets pending review count.
     *
     * GET /api/v1/reviews/count
     */
    @GET
    @Path("/count")
    public Response getPendingCount() {
        try {
            long count = reviewService.getPendingCount();
            return Response.ok(Map.of("pendingCount", count)).build();
        } catch (Exception e) {
            log.error("getPendingCount.failed error={}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(), "/api/v1/reviews/count"))
                    .build();
        }
    }

    /**
     * Gets a specific review item.
     *
     * GET /api/v1/reviews/{id}
     */
    @GET
    @Path("/{id}")
    public Response getReviewItem(@PathParam("id") String reviewId) {
        try {
            ReviewItem item = reviewService.getReviewItem(reviewId);
            if (item == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound(
                                "Review item not found: " + reviewId,
                                "/api/v1/reviews/" + reviewId))
                        .build();
            }
            return Response.ok(item).build();
        } catch (Exception e) {
            log.error("getReviewItem.failed id={} error={}", reviewId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/reviews/" + reviewId))
                    .build();
        }
    }

    /**
     * Approves a review item, triggering a merge.
     *
     * POST /api/v1/reviews/{id}/approve
     */
    @POST
    @Path("/{id}/approve")
    public Response approveReview(@PathParam("id") String reviewId,
                                   ReviewDecisionRequest request) {
        try {
            MergeResult mergeResult = reviewService.approveMatch(
                    reviewId, request.reviewerId(), request.notes());

            Map<String, Object> response = Map.of(
                    "reviewId", reviewId,
                    "decision", "APPROVED",
                    "mergeSuccess", mergeResult.isSuccess(),
                    "mergeMessage", mergeResult.errorMessage() != null ? mergeResult.errorMessage() : ""
            );
            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage(),
                            "/api/v1/reviews/" + reviewId + "/approve"))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.conflict(e.getMessage(),
                            "/api/v1/reviews/" + reviewId + "/approve"))
                    .build();
        } catch (Exception e) {
            log.error("approveReview.failed id={} error={}", reviewId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/reviews/" + reviewId + "/approve"))
                    .build();
        }
    }

    /**
     * Rejects a review item.
     *
     * POST /api/v1/reviews/{id}/reject
     */
    @POST
    @Path("/{id}/reject")
    public Response rejectReview(@PathParam("id") String reviewId,
                                  ReviewDecisionRequest request) {
        try {
            reviewService.rejectMatch(reviewId, request.reviewerId(), request.notes());

            Map<String, Object> response = Map.of(
                    "reviewId", reviewId,
                    "decision", "REJECTED"
            );
            return Response.ok(response).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ErrorResponse.notFound(e.getMessage(),
                            "/api/v1/reviews/" + reviewId + "/reject"))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(ErrorResponse.conflict(e.getMessage(),
                            "/api/v1/reviews/" + reviewId + "/reject"))
                    .build();
        } catch (Exception e) {
            log.error("rejectReview.failed id={} error={}", reviewId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/reviews/" + reviewId + "/reject"))
                    .build();
        }
    }
}
