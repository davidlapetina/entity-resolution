package com.entity.resolution.mcp;

import com.entity.resolution.api.EntityContext;
import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.decision.MatchDecisionRecord;

import java.util.*;

/**
 * Builds MCP (Model Context Protocol) tool definitions for the entity resolution library.
 * All tools are <strong>read-only</strong>. LLMs cannot create, merge, or mutate entity data.
 *
 * <p>Available tools:</p>
 * <ul>
 *   <li>{@code resolve_entity} -- look up an entity by name and type (read-only, no creation)</li>
 *   <li>{@code get_entity_context} -- get full entity context (synonyms, relationships, decisions, merge history)</li>
 *   <li>{@code get_entity_decisions} -- get match decisions involving an entity</li>
 *   <li>{@code search_entities} -- search active entities by type</li>
 *   <li>{@code get_entity_synonyms} -- get all synonyms for an entity</li>
 * </ul>
 */
public final class EntityResolutionMcpTools {

    private final EntityResolver resolver;

    public EntityResolutionMcpTools(EntityResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
    }

    /**
     * Returns all 5 read-only MCP tool definitions.
     */
    public List<McpToolDefinition> getToolDefinitions() {
        return List.of(
                buildResolveEntityTool(),
                buildGetEntityContextTool(),
                buildGetEntityDecisionsTool(),
                buildSearchEntitiesTool(),
                buildGetEntitySynonymsTool()
        );
    }

    /**
     * Finds a tool definition by name.
     */
    public Optional<McpToolDefinition> getTool(String name) {
        return getToolDefinitions().stream()
                .filter(t -> t.name().equals(name))
                .findFirst();
    }

    private McpToolDefinition buildResolveEntityTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Entity name to look up"),
                        "entity_type", Map.of("type", "string", "description",
                                "Entity type (COMPANY, PERSON, ORGANIZATION, PRODUCT, LOCATION, DATASET, TABLE, SCHEMA, DOMAIN, SERVICE, API, DOCUMENT)")
                ),
                "required", List.of("name", "entity_type")
        );

        return new McpToolDefinition(
                "resolve_entity",
                "Look up an entity by name and type. Returns the canonical entity if found. Read-only: does not create new entities.",
                schema,
                params -> {
                    String name = (String) params.get("name");
                    String entityTypeStr = (String) params.get("entity_type");
                    EntityType entityType = EntityType.valueOf(entityTypeStr.toUpperCase());

                    Optional<EntityReference> ref = resolver.findEntity(name, entityType);
                    if (ref.isEmpty()) {
                        return Map.of("found", false, "message", "No entity found for: " + name);
                    }
                    Optional<Entity> entity = resolver.getEntity(ref.get().getId());
                    if (entity.isEmpty()) {
                        return Map.of("found", false, "message", "Entity reference exists but entity not found");
                    }
                    Entity e = entity.get();
                    return Map.of(
                            "found", true,
                            "entityId", e.getId(),
                            "canonicalName", e.getCanonicalName(),
                            "type", e.getType().name(),
                            "status", e.getStatus().name(),
                            "confidenceScore", e.getConfidenceScore()
                    );
                }
        );
    }

    private McpToolDefinition buildGetEntityContextTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "entity_id", Map.of("type", "string", "description", "Entity UUID")
                ),
                "required", List.of("entity_id")
        );

        return new McpToolDefinition(
                "get_entity_context",
                "Get the full context for an entity including synonyms, relationships, match decisions, and merge history.",
                schema,
                params -> {
                    String entityId = (String) params.get("entity_id");
                    Optional<EntityContext> ctx = resolver.getEntityContext(entityId);
                    if (ctx.isEmpty()) {
                        return Map.of("found", false, "message", "Entity not found: " + entityId);
                    }
                    EntityContext c = ctx.get();
                    return Map.of(
                            "found", true,
                            "entityId", c.entity().getId(),
                            "canonicalName", c.entity().getCanonicalName(),
                            "type", c.entity().getType().name(),
                            "synonymCount", c.synonyms().size(),
                            "relationshipCount", c.relationships().size(),
                            "decisionCount", c.decisions().size(),
                            "mergeHistoryCount", c.mergeHistory().size()
                    );
                }
        );
    }

    private McpToolDefinition buildGetEntityDecisionsTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "entity_id", Map.of("type", "string", "description", "Entity UUID")
                ),
                "required", List.of("entity_id")
        );

        return new McpToolDefinition(
                "get_entity_decisions",
                "Get all match decisions involving an entity, including score breakdowns and outcomes.",
                schema,
                params -> {
                    String entityId = (String) params.get("entity_id");
                    List<MatchDecisionRecord> decisions = resolver.getDecisionsForEntity(entityId);
                    List<Map<String, Object>> decisionMaps = decisions.stream()
                            .map(d -> Map.<String, Object>of(
                                    "id", d.getId(),
                                    "candidateEntityId", d.getCandidateEntityId(),
                                    "finalScore", d.getFinalScore(),
                                    "outcome", d.getOutcome().name(),
                                    "evaluator", d.getEvaluator()
                            ))
                            .toList();
                    return Map.of("entityId", entityId, "decisions", decisionMaps);
                }
        );
    }

    private McpToolDefinition buildSearchEntitiesTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "entity_type", Map.of("type", "string", "description",
                                "Entity type to search (COMPANY, PERSON, ORGANIZATION, PRODUCT, LOCATION, DATASET, TABLE, SCHEMA, DOMAIN, SERVICE, API, DOCUMENT)")
                ),
                "required", List.of("entity_type")
        );

        return new McpToolDefinition(
                "search_entities",
                "Search for active entities by type. Returns a list of matching entities.",
                schema,
                params -> {
                    String entityTypeStr = (String) params.get("entity_type");
                    EntityType entityType = EntityType.valueOf(entityTypeStr.toUpperCase());
                    List<Entity> entities = resolver.getService().getEntityRepository()
                            .findAllActive(entityType);
                    List<Map<String, Object>> entityMaps = entities.stream()
                            .map(e -> Map.<String, Object>of(
                                    "entityId", e.getId(),
                                    "canonicalName", e.getCanonicalName(),
                                    "type", e.getType().name(),
                                    "confidenceScore", e.getConfidenceScore()
                            ))
                            .toList();
                    return Map.of("entityType", entityType.name(), "count", entities.size(),
                            "entities", entityMaps);
                }
        );
    }

    private McpToolDefinition buildGetEntitySynonymsTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "entity_id", Map.of("type", "string", "description", "Entity UUID")
                ),
                "required", List.of("entity_id")
        );

        return new McpToolDefinition(
                "get_entity_synonyms",
                "Get all synonyms for an entity, including their source, confidence, and support count.",
                schema,
                params -> {
                    String entityId = (String) params.get("entity_id");
                    List<Synonym> synonyms = resolver.getSynonyms(entityId);
                    List<Map<String, Object>> synonymMaps = synonyms.stream()
                            .map(s -> Map.<String, Object>of(
                                    "id", s.getId(),
                                    "value", s.getValue(),
                                    "source", s.getSource().name(),
                                    "confidence", s.getConfidence(),
                                    "supportCount", s.getSupportCount()
                            ))
                            .toList();
                    return Map.of("entityId", entityId, "count", synonyms.size(),
                            "synonyms", synonymMaps);
                }
        );
    }
}
