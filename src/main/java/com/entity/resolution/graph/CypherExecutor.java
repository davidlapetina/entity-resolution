package com.entity.resolution.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Executes Cypher queries for entity resolution operations.
 * Contains queries from PRD sections 8.1-8.6.
 */
public class CypherExecutor {
    private static final Logger log = LoggerFactory.getLogger(CypherExecutor.class);

    private final GraphConnection connection;

    public CypherExecutor(GraphConnection connection) {
        this.connection = connection;
    }

    // ========== PRD Section 8.1: Candidate Discovery ==========

    /**
     * Find candidate entities by exact normalized name match.
     */
    public List<Map<String, Object>> findCandidatesByNormalizedName(String normalizedName, String entityType) {
        String query = """
                MATCH (e:Entity)
                WHERE e.normalizedName = $normalizedName
                  AND e.type = $entityType
                  AND e.status = 'ACTIVE'
                RETURN e.id as id, e.canonicalName as canonicalName, e.normalizedName as normalizedName,
                       e.type as type, e.confidenceScore as confidenceScore
                """;
        return connection.query(query, Map.of(
                "normalizedName", normalizedName,
                "entityType", entityType
        ));
    }

    /**
     * Find candidate entities by synonym lookup.
     */
    public List<Map<String, Object>> findCandidatesBySynonym(String normalizedValue, String entityType) {
        String query = """
                MATCH (s:Synonym)-[:SYNONYM_OF]->(e:Entity)
                WHERE s.normalizedValue = $normalizedValue
                  AND e.type = $entityType
                  AND e.status = 'ACTIVE'
                RETURN e.id as id, e.canonicalName as canonicalName, e.normalizedName as normalizedName,
                       e.type as type, e.confidenceScore as confidenceScore, s.value as matchedSynonym
                """;
        return connection.query(query, Map.of(
                "normalizedValue", normalizedValue,
                "entityType", entityType
        ));
    }

    /**
     * Find all active entities of a given type for fuzzy matching.
     */
    public List<Map<String, Object>> findAllActiveEntities(String entityType) {
        String query = """
                MATCH (e:Entity)
                WHERE e.type = $entityType
                  AND e.status = 'ACTIVE'
                RETURN e.id as id, e.canonicalName as canonicalName, e.normalizedName as normalizedName,
                       e.type as type, e.confidenceScore as confidenceScore
                """;
        return connection.query(query, Map.of("entityType", entityType));
    }

    // ========== PRD Section 8.2: Synonym Attachment ==========

    /**
     * Create a synonym and attach it to an entity.
     */
    public void createSynonym(String synonymId, String value, String normalizedValue,
                              String source, double confidence, String entityId) {
        String query = """
                MATCH (e:Entity {id: $entityId})
                CREATE (s:Synonym {
                    id: $synonymId,
                    value: $value,
                    normalizedValue: $normalizedValue,
                    source: $source,
                    confidence: $confidence,
                    createdAt: datetime()
                })
                CREATE (s)-[:SYNONYM_OF]->(e)
                """;
        connection.execute(query, Map.of(
                "synonymId", synonymId,
                "value", value,
                "normalizedValue", normalizedValue,
                "source", source,
                "confidence", confidence,
                "entityId", entityId
        ));
        log.debug("Created synonym '{}' for entity {}", value, entityId);
    }

    // ========== PRD Section 8.3: Duplicate Entity Creation ==========

    /**
     * Create a duplicate entity node linked to the canonical entity.
     */
    public void createDuplicateEntity(String duplicateId, String originalName, String normalizedName,
                                       String sourceSystem, String canonicalEntityId) {
        String query = """
                MATCH (canonical:Entity {id: $canonicalEntityId})
                CREATE (dup:DuplicateEntity {
                    id: $duplicateId,
                    originalName: $originalName,
                    normalizedName: $normalizedName,
                    sourceSystem: $sourceSystem,
                    createdAt: datetime()
                })
                CREATE (dup)-[:DUPLICATE_OF]->(canonical)
                """;
        connection.execute(query, Map.of(
                "duplicateId", duplicateId,
                "originalName", originalName,
                "normalizedName", normalizedName,
                "sourceSystem", sourceSystem,
                "canonicalEntityId", canonicalEntityId
        ));
        log.debug("Created duplicate entity '{}' -> canonical {}", originalName, canonicalEntityId);
    }

    // ========== PRD Section 8.4 & 8.5: Relationship Migration ==========

    /**
     * Migrate all relationships from source entity to target entity.
     * This handles both incoming and outgoing relationships.
     */
    public void migrateRelationships(String sourceEntityId, String targetEntityId) {
        // Migrate outgoing relationships
        String migrateOutgoing = """
                MATCH (source:Entity {id: $sourceEntityId})-[r]->(target)
                WHERE NOT target:Entity OR target.id <> $targetEntityId
                WITH source, r, target, type(r) as relType, properties(r) as props
                MATCH (newSource:Entity {id: $targetEntityId})
                CALL {
                    WITH newSource, target, relType, props
                    CREATE (newSource)-[newRel]->(target)
                    SET newRel = props
                    RETURN newRel
                }
                DELETE r
                """;

        // Migrate incoming relationships
        String migrateIncoming = """
                MATCH (source)-[r]->(target:Entity {id: $sourceEntityId})
                WHERE NOT source:Entity OR source.id <> $targetEntityId
                WITH source, r, target, type(r) as relType, properties(r) as props
                MATCH (newTarget:Entity {id: $targetEntityId})
                CALL {
                    WITH source, newTarget, relType, props
                    CREATE (source)-[newRel]->(newTarget)
                    SET newRel = props
                    RETURN newRel
                }
                DELETE r
                """;

        Map<String, Object> params = Map.of(
                "sourceEntityId", sourceEntityId,
                "targetEntityId", targetEntityId
        );

        try {
            connection.execute(migrateOutgoing, params);
            connection.execute(migrateIncoming, params);
            log.debug("Migrated relationships from {} to {}", sourceEntityId, targetEntityId);
        } catch (Exception e) {
            log.warn("Relationship migration may have partial results: {}", e.getMessage());
        }
    }

    /**
     * Migrate synonyms from source entity to target entity.
     */
    public void migrateSynonyms(String sourceEntityId, String targetEntityId) {
        String query = """
                MATCH (s:Synonym)-[r:SYNONYM_OF]->(source:Entity {id: $sourceEntityId})
                MATCH (target:Entity {id: $targetEntityId})
                DELETE r
                CREATE (s)-[:SYNONYM_OF]->(target)
                """;
        connection.execute(query, Map.of(
                "sourceEntityId", sourceEntityId,
                "targetEntityId", targetEntityId
        ));
        log.debug("Migrated synonyms from {} to {}", sourceEntityId, targetEntityId);
    }

    // ========== PRD Section 8.6: Merge Registration ==========

    /**
     * Record a merge in the graph by creating a MERGED_INTO relationship
     * and updating the source entity status.
     */
    public void recordMerge(String sourceEntityId, String targetEntityId, double confidence, String reason) {
        String query = """
                MATCH (source:Entity {id: $sourceEntityId})
                MATCH (target:Entity {id: $targetEntityId})
                SET source.status = 'MERGED'
                SET source.updatedAt = datetime()
                CREATE (source)-[:MERGED_INTO {
                    confidence: $confidence,
                    reason: $reason,
                    mergedAt: datetime()
                }]->(target)
                """;
        connection.execute(query, Map.of(
                "sourceEntityId", sourceEntityId,
                "targetEntityId", targetEntityId,
                "confidence", confidence,
                "reason", reason != null ? reason : ""
        ));
        log.info("Recorded merge: {} -> {} (confidence: {})", sourceEntityId, targetEntityId, confidence);
    }

    // ========== Entity CRUD Operations ==========

    /**
     * Create a new entity node.
     */
    public void createEntity(String id, String canonicalName, String normalizedName,
                              String type, double confidenceScore) {
        String query = """
                CREATE (e:Entity {
                    id: $id,
                    canonicalName: $canonicalName,
                    normalizedName: $normalizedName,
                    type: $type,
                    confidenceScore: $confidenceScore,
                    status: 'ACTIVE',
                    createdAt: datetime(),
                    updatedAt: datetime()
                })
                """;
        connection.execute(query, Map.of(
                "id", id,
                "canonicalName", canonicalName,
                "normalizedName", normalizedName,
                "type", type,
                "confidenceScore", confidenceScore
        ));
        log.debug("Created entity: {} ({})", canonicalName, id);
    }

    /**
     * Find an entity by ID.
     */
    public List<Map<String, Object>> findEntityById(String id) {
        String query = """
                MATCH (e:Entity {id: $id})
                RETURN e.id as id, e.canonicalName as canonicalName, e.normalizedName as normalizedName,
                       e.type as type, e.confidenceScore as confidenceScore, e.status as status,
                       e.createdAt as createdAt, e.updatedAt as updatedAt
                """;
        return connection.query(query, Map.of("id", id));
    }

    /**
     * Update entity confidence score.
     */
    public void updateEntityConfidence(String entityId, double newConfidence) {
        String query = """
                MATCH (e:Entity {id: $entityId})
                SET e.confidenceScore = $newConfidence
                SET e.updatedAt = datetime()
                """;
        connection.execute(query, Map.of(
                "entityId", entityId,
                "newConfidence", newConfidence
        ));
    }

    /**
     * Get all synonyms for an entity.
     */
    public List<Map<String, Object>> getSynonymsForEntity(String entityId) {
        String query = """
                MATCH (s:Synonym)-[:SYNONYM_OF]->(e:Entity {id: $entityId})
                RETURN s.id as id, s.value as value, s.normalizedValue as normalizedValue,
                       s.source as source, s.confidence as confidence, s.createdAt as createdAt
                """;
        return connection.query(query, Map.of("entityId", entityId));
    }

    /**
     * Get the canonical entity for a merged entity.
     */
    public List<Map<String, Object>> getCanonicalEntity(String mergedEntityId) {
        String query = """
                MATCH (merged:Entity {id: $mergedEntityId})-[:MERGED_INTO*]->(canonical:Entity)
                WHERE canonical.status = 'ACTIVE'
                RETURN canonical.id as id, canonical.canonicalName as canonicalName,
                       canonical.normalizedName as normalizedName, canonical.type as type,
                       canonical.confidenceScore as confidenceScore
                """;
        return connection.query(query, Map.of("mergedEntityId", mergedEntityId));
    }

    /**
     * Get merge history for an entity.
     */
    public List<Map<String, Object>> getMergeHistory(String entityId) {
        String query = """
                MATCH (source:Entity)-[m:MERGED_INTO]->(target:Entity {id: $entityId})
                RETURN source.id as sourceId, source.canonicalName as sourceName,
                       m.confidence as confidence, m.reason as reason, m.mergedAt as mergedAt
                ORDER BY m.mergedAt DESC
                """;
        return connection.query(query, Map.of("entityId", entityId));
    }

    public GraphConnection getConnection() {
        return connection;
    }

    // ========== Library-Managed Relationships ==========

    /**
     * Create a library-managed relationship between two entities.
     */
    public void createRelationship(String relationshipId, String sourceEntityId, String targetEntityId,
                                    String relationshipType, Map<String, Object> properties, String createdBy) {
        String query = """
                MATCH (source:Entity {id: $sourceEntityId})
                MATCH (target:Entity {id: $targetEntityId})
                CREATE (source)-[r:LIBRARY_REL {
                    id: $relationshipId,
                    type: $relationshipType,
                    createdAt: datetime(),
                    createdBy: $createdBy
                }]->(target)
                """;

        Map<String, Object> params = new java.util.HashMap<>(Map.of(
                "relationshipId", relationshipId,
                "sourceEntityId", sourceEntityId,
                "targetEntityId", targetEntityId,
                "relationshipType", relationshipType,
                "createdBy", createdBy != null ? createdBy : ""
        ));

        // Add any additional properties
        if (properties != null && !properties.isEmpty()) {
            // For FalkorDB, we'd need to set these separately or include in the query
            log.debug("Relationship properties: {}", properties);
        }

        connection.execute(query, params);
        log.debug("Created library relationship {} (type: {}) from {} to {}",
                relationshipId, relationshipType, sourceEntityId, targetEntityId);
    }

    /**
     * Find a relationship by ID.
     */
    public List<Map<String, Object>> findRelationshipById(String relationshipId) {
        String query = """
                MATCH (source:Entity)-[r:LIBRARY_REL {id: $relationshipId}]->(target:Entity)
                RETURN r.id as id, source.id as sourceEntityId, target.id as targetEntityId,
                       r.type as relationshipType, r.createdAt as createdAt, r.createdBy as createdBy
                """;
        return connection.query(query, Map.of("relationshipId", relationshipId));
    }

    /**
     * Find all relationships where entity is the source.
     */
    public List<Map<String, Object>> findRelationshipsBySource(String entityId) {
        String query = """
                MATCH (source:Entity {id: $entityId})-[r:LIBRARY_REL]->(target:Entity)
                RETURN r.id as id, source.id as sourceEntityId, target.id as targetEntityId,
                       r.type as relationshipType, r.createdAt as createdAt, r.createdBy as createdBy
                """;
        return connection.query(query, Map.of("entityId", entityId));
    }

    /**
     * Find all relationships where entity is the target.
     */
    public List<Map<String, Object>> findRelationshipsByTarget(String entityId) {
        String query = """
                MATCH (source:Entity)-[r:LIBRARY_REL]->(target:Entity {id: $entityId})
                RETURN r.id as id, source.id as sourceEntityId, target.id as targetEntityId,
                       r.type as relationshipType, r.createdAt as createdAt, r.createdBy as createdBy
                """;
        return connection.query(query, Map.of("entityId", entityId));
    }

    /**
     * Find all relationships involving an entity (as source or target).
     */
    public List<Map<String, Object>> findRelationshipsByEntity(String entityId) {
        String query = """
                MATCH (source:Entity)-[r:LIBRARY_REL]->(target:Entity)
                WHERE source.id = $entityId OR target.id = $entityId
                RETURN r.id as id, source.id as sourceEntityId, target.id as targetEntityId,
                       r.type as relationshipType, r.createdAt as createdAt, r.createdBy as createdBy
                """;
        return connection.query(query, Map.of("entityId", entityId));
    }

    /**
     * Find relationships between two specific entities.
     */
    public List<Map<String, Object>> findRelationshipsBetween(String sourceEntityId, String targetEntityId) {
        String query = """
                MATCH (source:Entity {id: $sourceEntityId})-[r:LIBRARY_REL]->(target:Entity {id: $targetEntityId})
                RETURN r.id as id, source.id as sourceEntityId, target.id as targetEntityId,
                       r.type as relationshipType, r.createdAt as createdAt, r.createdBy as createdBy
                """;
        return connection.query(query, Map.of(
                "sourceEntityId", sourceEntityId,
                "targetEntityId", targetEntityId
        ));
    }

    /**
     * Delete a relationship by ID.
     */
    public void deleteRelationship(String relationshipId) {
        String query = """
                MATCH ()-[r:LIBRARY_REL {id: $relationshipId}]->()
                DELETE r
                """;
        connection.execute(query, Map.of("relationshipId", relationshipId));
        log.debug("Deleted relationship {}", relationshipId);
    }

    /**
     * Migrate library-managed relationships from source entity to target entity.
     */
    public void migrateLibraryRelationships(String sourceEntityId, String targetEntityId) {
        // Migrate outgoing relationships
        String migrateOutgoing = """
                MATCH (source:Entity {id: $sourceEntityId})-[r:LIBRARY_REL]->(target:Entity)
                WHERE target.id <> $targetEntityId
                WITH r, target
                MATCH (newSource:Entity {id: $targetEntityId})
                CREATE (newSource)-[newR:LIBRARY_REL]->(target)
                SET newR = properties(r)
                DELETE r
                """;

        // Migrate incoming relationships
        String migrateIncoming = """
                MATCH (source:Entity)-[r:LIBRARY_REL]->(target:Entity {id: $sourceEntityId})
                WHERE source.id <> $targetEntityId
                WITH r, source
                MATCH (newTarget:Entity {id: $targetEntityId})
                CREATE (source)-[newR:LIBRARY_REL]->(newTarget)
                SET newR = properties(r)
                DELETE r
                """;

        Map<String, Object> params = Map.of(
                "sourceEntityId", sourceEntityId,
                "targetEntityId", targetEntityId
        );

        try {
            connection.execute(migrateOutgoing, params);
            connection.execute(migrateIncoming, params);
            log.debug("Migrated library relationships from {} to {}", sourceEntityId, targetEntityId);
        } catch (Exception e) {
            log.warn("Library relationship migration may have partial results: {}", e.getMessage());
        }
    }
}
