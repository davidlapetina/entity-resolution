package com.entity.resolution.rest;

import com.entity.resolution.api.*;
import com.entity.resolution.core.model.*;
import com.entity.resolution.rest.dto.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST resource for entity resolution operations.
 *
 * <p>Provides endpoints for:</p>
 * <ul>
 *   <li>Resolving entities (single and batch)</li>
 *   <li>Looking up entities by ID</li>
 *   <li>Managing relationships</li>
 *   <li>Querying synonyms and audit trail</li>
 * </ul>
 */
@Path("/api/v1/entities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EntityResolutionResource {
    private static final Logger log = LoggerFactory.getLogger(EntityResolutionResource.class);

    private final EntityResolver resolver;

    public EntityResolutionResource(EntityResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Resolves a single entity.
     *
     * POST /api/v1/entities/resolve
     */
    @POST
    @Path("/resolve")
    public Response resolve(ResolveRequest request) {
        try {
            EntityType entityType = EntityType.valueOf(request.entityType().toUpperCase());

            ResolutionOptions options = buildOptions(request);

            EntityResolutionResult result = resolver.resolve(
                    request.name(), entityType, options);

            return Response.ok(EntityResponse.from(result)).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.badRequest(e.getMessage(), "/api/v1/entities/resolve"))
                    .build();
        } catch (Exception e) {
            log.error("resolve.failed name='{}' error={}", request.name(), e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(), "/api/v1/entities/resolve"))
                    .build();
        }
    }

    /**
     * Resolves entities in batch.
     *
     * POST /api/v1/entities/batch
     */
    @POST
    @Path("/batch")
    public Response batchResolve(BatchResolveRequest request) {
        try {
            List<EntityResponse> results = new ArrayList<>();
            List<ErrorResponse> errors = new ArrayList<>();

            try (BatchContext batch = resolver.beginBatch()) {
                for (BatchResolveRequest.BatchItem item : request.items()) {
                    try {
                        EntityType entityType = EntityType.valueOf(item.entityType().toUpperCase());
                        EntityResolutionResult result = batch.resolve(item.name(), entityType);
                        results.add(EntityResponse.from(result));
                    } catch (Exception e) {
                        errors.add(ErrorResponse.badRequest(
                                "Failed to resolve '" + item.name() + "': " + e.getMessage(),
                                "/api/v1/entities/batch"));
                    }
                }
                batch.commit();
            }

            Map<String, Object> response = Map.of(
                    "results", results,
                    "errors", errors,
                    "totalProcessed", results.size(),
                    "totalErrors", errors.size()
            );
            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("batchResolve.failed error={}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(), "/api/v1/entities/batch"))
                    .build();
        }
    }

    /**
     * Gets an entity by ID.
     *
     * GET /api/v1/entities/{id}
     */
    @GET
    @Path("/{id}")
    public Response getEntity(@PathParam("id") String entityId) {
        try {
            Optional<Entity> entity = resolver.getService().getEntityRepository().findById(entityId);
            if (entity.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound(
                                "Entity not found: " + entityId,
                                "/api/v1/entities/" + entityId))
                        .build();
            }

            return Response.ok(EntityResponse.fromEntity(entity.get())).build();
        } catch (Exception e) {
            log.error("getEntity.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/entities/" + entityId))
                    .build();
        }
    }

    /**
     * Gets synonyms for an entity.
     *
     * GET /api/v1/entities/{id}/synonyms
     */
    @GET
    @Path("/{id}/synonyms")
    public Response getSynonyms(@PathParam("id") String entityId) {
        try {
            List<Synonym> synonyms = resolver.getService().getSynonymRepository()
                    .findByEntityId(entityId);
            return Response.ok(synonyms).build();
        } catch (Exception e) {
            log.error("getSynonyms.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/entities/" + entityId + "/synonyms"))
                    .build();
        }
    }

    /**
     * Creates a relationship between two entities.
     *
     * POST /api/v1/relationships
     */
    @POST
    @Path("/relationships")
    public Response createRelationship(CreateRelationshipRequest request) {
        try {
            Optional<Entity> sourceEntity = resolver.getService().getEntityRepository()
                    .findById(request.sourceEntityId());
            Optional<Entity> targetEntity = resolver.getService().getEntityRepository()
                    .findById(request.targetEntityId());
            if (sourceEntity.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound("Source entity not found: " + request.sourceEntityId(),
                                "/api/v1/relationships"))
                        .build();
            }
            if (targetEntity.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound("Target entity not found: " + request.targetEntityId(),
                                "/api/v1/relationships"))
                        .build();
            }
            EntityReference sourceRef = EntityReference.of(request.sourceEntityId(), sourceEntity.get().getType());
            EntityReference targetRef = EntityReference.of(request.targetEntityId(), targetEntity.get().getType());
            Relationship relationship = resolver.createRelationship(
                    sourceRef, targetRef,
                    request.relationshipType(),
                    request.properties());

            return Response.status(Response.Status.CREATED).entity(relationship).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ErrorResponse.badRequest(e.getMessage(), "/api/v1/relationships"))
                    .build();
        } catch (Exception e) {
            log.error("createRelationship.failed error={}", e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(), "/api/v1/relationships"))
                    .build();
        }
    }

    /**
     * Gets relationships for an entity.
     *
     * GET /api/v1/entities/{id}/relationships
     */
    @GET
    @Path("/{id}/relationships")
    public Response getRelationships(
            @PathParam("id") String entityId,
            @QueryParam("direction") @DefaultValue("all") String direction,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        try {
            PageRequest pageRequest = PageRequest.of(page, size);
            // Create a lightweight reference for querying (type doesn't matter for lookups)
            EntityReference ref = EntityReference.of(entityId, EntityType.COMPANY);
            List<Relationship> relationships;

            switch (direction.toLowerCase()) {
                case "outgoing" -> relationships = resolver.getOutgoingRelationships(ref);
                case "incoming" -> relationships = resolver.getIncomingRelationships(ref);
                default -> relationships = resolver.getRelationships(ref);
            }

            // Simple pagination over the list
            int total = relationships.size();
            int fromIndex = Math.min(pageRequest.offset(), total);
            int toIndex = Math.min(pageRequest.offset() + pageRequest.limit(), total);
            List<Relationship> paged = relationships.subList(fromIndex, toIndex);

            Map<String, Object> response = Map.of(
                    "content", paged,
                    "totalElements", total,
                    "page", page,
                    "size", size
            );
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("getRelationships.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/entities/" + entityId + "/relationships"))
                    .build();
        }
    }

    /**
     * Gets the audit trail for an entity.
     *
     * GET /api/v1/entities/{id}/audit
     */
    @GET
    @Path("/{id}/audit")
    public Response getAuditTrail(
            @PathParam("id") String entityId,
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        try {
            CursorPage<?> auditPage = resolver.getService().getAuditService()
                    .getAuditTrailPaginated(entityId, cursor, limit);

            return Response.ok(auditPage).build();
        } catch (Exception e) {
            log.error("getAuditTrail.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/entities/" + entityId + "/audit"))
                    .build();
        }
    }

    /**
     * Gets merge history for an entity.
     *
     * GET /api/v1/entities/{id}/merge-history
     */
    @GET
    @Path("/{id}/merge-history")
    public Response getMergeHistory(@PathParam("id") String entityId) {
        try {
            var mergeHistory = resolver.getService().getMergeHistory(entityId);
            return Response.ok(mergeHistory).build();
        } catch (Exception e) {
            log.error("getMergeHistory.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError(e.getMessage(),
                            "/api/v1/entities/" + entityId + "/merge-history"))
                    .build();
        }
    }

    private ResolutionOptions buildOptions(ResolveRequest request) {
        ResolutionOptions.Builder builder = ResolutionOptions.builder();
        if (request.autoMergeThreshold() != null) {
            builder.autoMergeThreshold(request.autoMergeThreshold());
        }
        if (request.synonymThreshold() != null) {
            builder.synonymThreshold(request.synonymThreshold());
        }
        if (request.reviewThreshold() != null) {
            builder.reviewThreshold(request.reviewThreshold());
        }
        if (request.sourceSystem() != null) {
            builder.sourceSystem(request.sourceSystem());
        }
        return builder.build();
    }
}
