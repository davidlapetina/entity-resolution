package com.entity.resolution.rest.dto;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.MatchDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DtoValidationTest {

    // ========== ResolveRequest Tests ==========

    @Test
    @DisplayName("Should create valid ResolveRequest")
    void testValidResolveRequest() {
        ResolveRequest req = new ResolveRequest("Acme Corp", "COMPANY", null, null, null, null);
        assertEquals("Acme Corp", req.name());
        assertEquals("COMPANY", req.entityType());
    }

    @Test
    @DisplayName("Should reject ResolveRequest with null name")
    void testResolveRequestNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResolveRequest(null, "COMPANY", null, null, null, null));
    }

    @Test
    @DisplayName("Should reject ResolveRequest with blank name")
    void testResolveRequestBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResolveRequest("  ", "COMPANY", null, null, null, null));
    }

    @Test
    @DisplayName("Should reject ResolveRequest with null entityType")
    void testResolveRequestNullType() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResolveRequest("Acme", null, null, null, null, null));
    }

    // ========== BatchResolveRequest Tests ==========

    @Test
    @DisplayName("Should create valid BatchResolveRequest")
    void testValidBatchRequest() {
        var items = List.of(
                new BatchResolveRequest.BatchItem("Acme", "COMPANY"),
                new BatchResolveRequest.BatchItem("Big Blue", "COMPANY")
        );
        BatchResolveRequest req = new BatchResolveRequest(items, "TEST");
        assertEquals(2, req.items().size());
    }

    @Test
    @DisplayName("Should reject BatchResolveRequest with empty items")
    void testBatchRequestEmptyItems() {
        assertThrows(IllegalArgumentException.class, () ->
                new BatchResolveRequest(List.of(), null));
    }

    @Test
    @DisplayName("Should reject BatchItem with null name")
    void testBatchItemNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new BatchResolveRequest.BatchItem(null, "COMPANY"));
    }

    // ========== CreateRelationshipRequest Tests ==========

    @Test
    @DisplayName("Should create valid CreateRelationshipRequest")
    void testValidRelationshipRequest() {
        CreateRelationshipRequest req = new CreateRelationshipRequest(
                "source-1", "target-1", "PARTNER", Map.of("since", "2024"));
        assertEquals("source-1", req.sourceEntityId());
        assertEquals("PARTNER", req.relationshipType());
    }

    @Test
    @DisplayName("Should default properties to empty map")
    void testRelationshipRequestDefaultProperties() {
        CreateRelationshipRequest req = new CreateRelationshipRequest(
                "source-1", "target-1", "PARTNER", null);
        assertNotNull(req.properties());
        assertTrue(req.properties().isEmpty());
    }

    @Test
    @DisplayName("Should reject null source entity ID")
    void testRelationshipRequestNullSource() {
        assertThrows(IllegalArgumentException.class, () ->
                new CreateRelationshipRequest(null, "target-1", "PARTNER", null));
    }

    // ========== ReviewDecisionRequest Tests ==========

    @Test
    @DisplayName("Should create valid ReviewDecisionRequest")
    void testValidReviewDecision() {
        ReviewDecisionRequest req = new ReviewDecisionRequest("reviewer-1", "Looks good");
        assertEquals("reviewer-1", req.reviewerId());
        assertEquals("Looks good", req.notes());
    }

    @Test
    @DisplayName("Should reject null reviewerId")
    void testReviewDecisionNullReviewer() {
        assertThrows(IllegalArgumentException.class, () ->
                new ReviewDecisionRequest(null, null));
    }

    // ========== EntityResponse Tests ==========

    @Test
    @DisplayName("Should create EntityResponse from EntityResolutionResult")
    void testEntityResponseFromResult() {
        Entity entity = Entity.builder()
                .canonicalName("Acme Corp")
                .normalizedName("acme corp")
                .type(EntityType.COMPANY)
                .build();
        EntityResolutionResult result = EntityResolutionResult.newEntity(entity);

        EntityResponse response = EntityResponse.from(result);

        assertNotNull(response.entityId());
        assertEquals("Acme Corp", response.canonicalName());
        assertEquals("COMPANY", response.entityType());
        assertTrue(response.isNewEntity());
        assertFalse(response.wasMerged());
    }

    @Test
    @DisplayName("Should create EntityResponse from Entity")
    void testEntityResponseFromEntity() {
        Entity entity = Entity.builder()
                .canonicalName("Acme Corp")
                .normalizedName("acme corp")
                .type(EntityType.COMPANY)
                .build();

        EntityResponse response = EntityResponse.fromEntity(entity);

        assertEquals("Acme Corp", response.canonicalName());
        assertEquals("ACTIVE", response.status());
    }

    // ========== ErrorResponse Tests ==========

    @Test
    @DisplayName("Should create error responses with factory methods")
    void testErrorResponseFactoryMethods() {
        ErrorResponse badRequest = ErrorResponse.badRequest("Invalid input", "/api/test");
        assertEquals(400, badRequest.status());
        assertEquals("Bad Request", badRequest.error());

        ErrorResponse notFound = ErrorResponse.notFound("Not found", "/api/test");
        assertEquals(404, notFound.status());

        ErrorResponse conflict = ErrorResponse.conflict("Already exists", "/api/test");
        assertEquals(409, conflict.status());

        ErrorResponse serverError = ErrorResponse.internalError("Oops", "/api/test");
        assertEquals(500, serverError.status());
    }

    @Test
    @DisplayName("Should include timestamp in ErrorResponse")
    void testErrorResponseTimestamp() {
        ErrorResponse error = ErrorResponse.badRequest("test", "/api/test");
        assertNotNull(error.timestamp());
    }
}
