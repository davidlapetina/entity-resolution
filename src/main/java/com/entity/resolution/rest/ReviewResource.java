package com.entity.resolution.rest;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import com.entity.resolution.merge.MergeResult;
import com.entity.resolution.rest.dto.ErrorResponse;
import com.entity.resolution.rest.dto.ReviewDecisionRequest;
import com.entity.resolution.rest.security.RequiresRole;
import com.entity.resolution.rest.security.SecurityRole;
import com.entity.resolution.review.ReviewItem;
import com.entity.resolution.review.ReviewService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST resource for managing the manual review queue.
 *
 * <p>Security: Read endpoints require {@link SecurityRole#READER}.
 * Approve/reject endpoints require {@link SecurityRole#ADMIN} since they trigger
 * irreversible merge operations.</p>
 */
@Path("/api/v1/reviews")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Manual Review", description = "Review queue for ambiguous entity matches requiring human decision")
@SecurityRequirement(name = "apiKey")
public class ReviewResource {
    private static final Logger log = LoggerFactory.getLogger(ReviewResource.class);

    private final ReviewService reviewService;

    @Inject
    public ReviewResource(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Gets pending review items with pagination.
     *
     * GET /api/v1/reviews?page=0&size=20
     */
    @GET
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "List pending reviews", description = "Returns pending review items with optional filtering by entity type or score range.")
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
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Count pending reviews", description = "Returns the total number of pending review items.")
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
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get review item", description = "Retrieves a specific review item by ID.")
    @APIResponse(responseCode = "200", description = "Review item found")
    @APIResponse(responseCode = "404", description = "Review item not found")
    public Response getReviewItem(@Parameter(description = "Review item ID") @PathParam("id") String reviewId) {
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
    @RequiresRole(SecurityRole.ADMIN)
    @Operation(summary = "Approve review (triggers merge)",
            description = "Approves a pending review item, merging the source entity into the candidate. Requires ADMIN role.")
    @APIResponse(responseCode = "200", description = "Review approved and merge attempted")
    @APIResponse(responseCode = "404", description = "Review item not found")
    @APIResponse(responseCode = "409", description = "Review item already decided")
    public Response approveReview(@Parameter(description = "Review item ID") @PathParam("id") String reviewId,
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
    @RequiresRole(SecurityRole.ADMIN)
    @Operation(summary = "Reject review",
            description = "Rejects a pending review item, marking the entities as distinct. Requires ADMIN role.")
    @APIResponse(responseCode = "200", description = "Review rejected")
    @APIResponse(responseCode = "404", description = "Review item not found")
    @APIResponse(responseCode = "409", description = "Review item already decided")
    public Response rejectReview(@Parameter(description = "Review item ID") @PathParam("id") String reviewId,
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
