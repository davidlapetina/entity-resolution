package com.entity.resolution.rest;

import com.entity.resolution.api.*;
import com.entity.resolution.core.model.*;
import com.entity.resolution.decision.MatchDecisionRecord;
import com.entity.resolution.rest.dto.*;
import com.entity.resolution.rest.security.RequiresRole;
import com.entity.resolution.rest.security.SecurityRole;
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
 *
 * <p>Security: All endpoints require authentication via API key.
 * GET endpoints require {@link SecurityRole#READER},
 * POST endpoints require {@link SecurityRole#WRITER}.</p>
 */
@Path("/api/v1/entities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Tag(name = "Entity Resolution", description = "Resolve, lookup, and manage entities and their relationships")
@SecurityRequirement(name = "apiKey")
public class EntityResolutionResource {
    private static final Logger log = LoggerFactory.getLogger(EntityResolutionResource.class);
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_AUDIT_LIMIT = 200;

    private final EntityResolver resolver;

    @Inject
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
    @RequiresRole(SecurityRole.WRITER)
    @Operation(summary = "Resolve a single entity",
            description = "Resolves an entity name to a canonical entity using exact, fuzzy, and optional LLM matching.")
    @APIResponse(responseCode = "200", description = "Entity resolved successfully")
    @APIResponse(responseCode = "400", description = "Invalid request (bad entity type or name)")
    @APIResponse(responseCode = "401", description = "Missing or invalid API key")
    @APIResponse(responseCode = "403", description = "Insufficient permissions (requires WRITER)")
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
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.", "/api/v1/entities/resolve"))
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
    @RequiresRole(SecurityRole.WRITER)
    @Operation(summary = "Resolve entities in batch",
            description = "Resolves multiple entities with automatic deduplication within the batch.")
    @APIResponse(responseCode = "200", description = "Batch resolved (may contain partial errors)")
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
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.", "/api/v1/entities/batch"))
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
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get entity by ID", description = "Retrieves a canonical entity by its unique identifier.")
    @APIResponse(responseCode = "200", description = "Entity found")
    @APIResponse(responseCode = "404", description = "Entity not found")
    public Response getEntity(@Parameter(description = "Entity UUID") @PathParam("id") String entityId) {
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
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
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
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get synonyms for entity", description = "Returns all known synonyms linked to the entity.")
    public Response getSynonyms(@Parameter(description = "Entity UUID") @PathParam("id") String entityId) {
        try {
            List<Synonym> synonyms = resolver.getService().getSynonymRepository()
                    .findByEntityId(entityId);
            return Response.ok(synonyms).build();
        } catch (Exception e) {
            log.error("getSynonyms.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
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
    @RequiresRole(SecurityRole.WRITER)
    @Operation(summary = "Create a relationship", description = "Creates a library-managed relationship that auto-migrates during merges.")
    @APIResponse(responseCode = "201", description = "Relationship created")
    @APIResponse(responseCode = "404", description = "Source or target entity not found")
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
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.", "/api/v1/relationships"))
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
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get entity relationships", description = "Returns relationships with optional direction filtering and pagination.")
    public Response getRelationships(
            @Parameter(description = "Entity UUID") @PathParam("id") String entityId,
            @QueryParam("direction") @DefaultValue("all") String direction,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size) {
        try {
            int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
            PageRequest pageRequest = PageRequest.of(page, clampedSize);
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
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
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
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get audit trail", description = "Returns the audit trail for an entity with cursor-based pagination.")
    public Response getAuditTrail(
            @Parameter(description = "Entity UUID") @PathParam("id") String entityId,
            @QueryParam("cursor") String cursor,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        try {
            int clampedLimit = Math.min(Math.max(limit, 1), MAX_AUDIT_LIMIT);
            CursorPage<?> auditPage = resolver.getService().getAuditService()
                    .getAuditTrailPaginated(entityId, cursor, clampedLimit);

            return Response.ok(auditPage).build();
        } catch (Exception e) {
            log.error("getAuditTrail.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
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
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get merge history", description = "Returns the merge history for an entity including all predecessors.")
    public Response getMergeHistory(@Parameter(description = "Entity UUID") @PathParam("id") String entityId) {
        try {
            var mergeHistory = resolver.getService().getMergeHistory(entityId);
            return Response.ok(mergeHistory).build();
        } catch (Exception e) {
            log.error("getMergeHistory.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
                            "/api/v1/entities/" + entityId + "/merge-history"))
                    .build();
        }
    }

    /**
     * Gets the full context for an entity, including synonyms, relationships,
     * match decisions, and merge history.
     *
     * GET /api/v1/entities/{id}/context
     */
    @GET
    @Path("/{id}/context")
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get entity context",
            description = "Returns entity with synonyms, relationships, match decisions, and merge history bundled in a single call.")
    @APIResponse(responseCode = "200", description = "Entity context returned")
    @APIResponse(responseCode = "404", description = "Entity not found")
    public Response getEntityContext(@Parameter(description = "Entity UUID") @PathParam("id") String entityId) {
        try {
            Optional<EntityContext> context = resolver.getEntityContext(entityId);
            if (context.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound(
                                "Entity not found: " + entityId,
                                "/api/v1/entities/" + entityId + "/context"))
                        .build();
            }
            return Response.ok(EntityContextResponse.from(context.get())).build();
        } catch (Exception e) {
            log.error("getEntityContext.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
                            "/api/v1/entities/" + entityId + "/context"))
                    .build();
        }
    }

    /**
     * Exports the subgraph rooted at an entity for RAG use cases.
     *
     * GET /api/v1/entities/{id}/subgraph?depth=1
     */
    @GET
    @Path("/{id}/subgraph")
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Export entity subgraph",
            description = "Exports the entity's subgraph including related entities, synonyms, relationships, and decisions at the specified depth (1-3). Designed for RAG pipelines.")
    @APIResponse(responseCode = "200", description = "Entity subgraph returned")
    @APIResponse(responseCode = "404", description = "Entity not found")
    public Response getEntitySubgraph(
            @Parameter(description = "Entity UUID") @PathParam("id") String entityId,
            @Parameter(description = "Traversal depth (1-3)") @QueryParam("depth") @DefaultValue("1") int depth) {
        try {
            Optional<EntitySubgraph> subgraph = resolver.exportEntitySubgraph(entityId, depth);
            if (subgraph.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(ErrorResponse.notFound(
                                "Entity not found: " + entityId,
                                "/api/v1/entities/" + entityId + "/subgraph"))
                        .build();
            }
            return Response.ok(EntitySubgraphResponse.from(subgraph.get())).build();
        } catch (Exception e) {
            log.error("getEntitySubgraph.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
                            "/api/v1/entities/" + entityId + "/subgraph"))
                    .build();
        }
    }

    /**
     * Gets match decisions for an entity.
     *
     * GET /api/v1/entities/{id}/decisions
     */
    @GET
    @Path("/{id}/decisions")
    @RequiresRole(SecurityRole.READER)
    @Operation(summary = "Get match decisions",
            description = "Returns all match decisions involving this entity, including score breakdowns, thresholds, and outcomes.")
    @APIResponse(responseCode = "200", description = "Match decisions returned")
    public Response getDecisions(
            @Parameter(description = "Entity UUID") @PathParam("id") String entityId) {
        try {
            List<MatchDecisionRecord> decisions = resolver.getDecisionsForEntity(entityId);
            List<MatchDecisionResponse> response = decisions.stream()
                    .map(MatchDecisionResponse::from)
                    .toList();
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("getDecisions.failed id={} error={}", entityId, e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.internalError("An internal error occurred. Check server logs for details.",
                            "/api/v1/entities/" + entityId + "/decisions"))
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
