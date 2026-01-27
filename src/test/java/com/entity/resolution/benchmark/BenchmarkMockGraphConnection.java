package com.entity.resolution.benchmark;

import com.entity.resolution.graph.GraphConnection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Performance-optimized mock graph connection for JMH benchmarks.
 * Uses HashMap indexed by normalized name for O(1) lookup.
 * Supports pre-populating large entity sets for realistic benchmarking.
 */
public class BenchmarkMockGraphConnection implements GraphConnection {

    private final String graphName;
    private final Map<String, Map<String, Object>> entitiesById = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> entitiesByNormalizedName = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> entitiesByType = new ConcurrentHashMap<>();
    private final Map<String, String> synonymToEntityId = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> synonymsByEntityId = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> relationshipsBySource = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> blockingKeyIndex = new ConcurrentHashMap<>();

    public BenchmarkMockGraphConnection(String graphName) {
        this.graphName = graphName;
    }

    /**
     * Pre-populates the mock with a specified number of entities for benchmarking.
     *
     * @param count      number of entities to create
     * @param entityType the entity type label (e.g., "Company")
     */
    public void prePopulate(int count, String entityType) {
        for (int i = 0; i < count; i++) {
            String id = UUID.randomUUID().toString();
            String name = "entity-" + i;
            String normalizedName = name.toLowerCase();

            Map<String, Object> entity = new HashMap<>();
            entity.put("id", id);
            entity.put("canonicalName", "Entity " + i);
            entity.put("normalizedName", normalizedName);
            entity.put("type", entityType);
            entity.put("confidenceScore", 1.0);
            entity.put("status", "ACTIVE");

            entitiesById.put(id, entity);
            entitiesByNormalizedName
                    .computeIfAbsent(normalizedName + "|" + entityType, k -> new CopyOnWriteArrayList<>())
                    .add(entity);
            entitiesByType
                    .computeIfAbsent(entityType, k -> new CopyOnWriteArrayList<>())
                    .add(entity);

            // Create blocking keys
            if (normalizedName.length() >= 3) {
                blockingKeyIndex
                        .computeIfAbsent("pfx:" + normalizedName.substring(0, 3), k -> ConcurrentHashMap.newKeySet())
                        .add(id);
            }
            if (normalizedName.length() >= 2) {
                blockingKeyIndex
                        .computeIfAbsent("bg:" + normalizedName.substring(0, 2), k -> ConcurrentHashMap.newKeySet())
                        .add(id);
            }
        }
    }

    /**
     * Returns the number of entities currently stored.
     */
    public int entityCount() {
        return entitiesById.size();
    }

    @Override
    public void execute(String query, Map<String, Object> params) {
        if (query.contains("CREATE") && query.contains(":Synonym")) {
            // Check Synonym first because synonym queries also contain ":Entity" in MATCH clause
            String synonymId = (String) params.get("synonymId");
            String entityId = (String) params.get("entityId");
            Map<String, Object> synonym = new HashMap<>();
            synonym.put("id", synonymId);
            synonym.put("value", params.get("value"));
            synonym.put("normalizedValue", params.get("normalizedValue"));
            synonym.put("source", params.get("source"));
            synonym.put("confidence", params.get("confidence"));
            String normalizedValue = (String) params.get("normalizedValue");
            if (normalizedValue != null && entityId != null) {
                synonymToEntityId.put(normalizedValue, entityId);
            }
            if (entityId != null) {
                synonymsByEntityId
                        .computeIfAbsent(entityId, k -> new CopyOnWriteArrayList<>())
                        .add(synonym);
            }
        } else if (query.contains("CREATE") && query.contains(":Entity")) {
            String id = (String) params.get("id");
            if (id != null) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("id", id);
                entity.put("canonicalName", params.get("canonicalName"));
                entity.put("normalizedName", params.get("normalizedName"));
                entity.put("type", params.get("type"));
                entity.put("confidenceScore", params.get("confidenceScore"));
                entity.put("status", "ACTIVE");

                entitiesById.put(id, entity);
                String key = params.get("normalizedName") + "|" + params.get("type");
                entitiesByNormalizedName
                        .computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                        .add(entity);
                entitiesByType
                        .computeIfAbsent((String) params.get("type"), k -> new CopyOnWriteArrayList<>())
                        .add(entity);
            }
        } else if (query.contains("CREATE") && query.contains("RELATED_TO")) {
            String sourceId = (String) params.get("sourceEntityId");
            Map<String, Object> rel = new HashMap<>(params);
            relationshipsBySource
                    .computeIfAbsent(sourceId, k -> new CopyOnWriteArrayList<>())
                    .add(rel);
        } else if (query.contains("SET") && query.contains("status")) {
            String sourceId = (String) params.get("sourceEntityId");
            if (sourceId != null && entitiesById.containsKey(sourceId)) {
                entitiesById.get(sourceId).put("status", "MERGED");
            }
        } else if (query.contains("BlockingKey")) {
            // Handle blocking key creation
            String entityId = (String) params.get("entityId");
            if (entityId != null && params.containsKey("keys")) {
                @SuppressWarnings("unchecked")
                Set<String> keys = (Set<String>) params.get("keys");
                for (String key : keys) {
                    blockingKeyIndex
                            .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                            .add(entityId);
                }
            }
        }
    }

    @Override
    public List<Map<String, Object>> query(String query, Map<String, Object> params) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (query.contains("e.normalizedName = $normalizedName")) {
            String normalizedName = (String) params.get("normalizedName");
            String entityType = (String) params.get("entityType");
            String key = normalizedName + "|" + entityType;
            List<Map<String, Object>> candidates = entitiesByNormalizedName.get(key);
            if (candidates != null) {
                for (Map<String, Object> entity : candidates) {
                    if ("ACTIVE".equals(entity.get("status"))) {
                        results.add(new HashMap<>(entity));
                    }
                }
            }
        } else if (query.contains("s.normalizedValue = $normalizedValue")) {
            String normalizedValue = (String) params.get("normalizedValue");
            String entityId = synonymToEntityId.get(normalizedValue);
            if (entityId != null && entitiesById.containsKey(entityId)) {
                Map<String, Object> entity = entitiesById.get(entityId);
                if ("ACTIVE".equals(entity.get("status"))) {
                    Map<String, Object> result = new HashMap<>(entity);
                    result.put("matchedSynonym", normalizedValue);
                    results.add(result);
                }
            }
        } else if (query.contains("BlockingKey") && query.contains("$keys")) {
            @SuppressWarnings("unchecked")
            Set<String> keys = params.get("keys") instanceof Set
                    ? (Set<String>) params.get("keys")
                    : new HashSet<>();
            Set<String> candidateIds = new HashSet<>();
            for (String key : keys) {
                Set<String> ids = blockingKeyIndex.get(key);
                if (ids != null) {
                    candidateIds.addAll(ids);
                }
            }
            String entityType = (String) params.get("entityType");
            for (String id : candidateIds) {
                Map<String, Object> entity = entitiesById.get(id);
                if (entity != null && "ACTIVE".equals(entity.get("status"))
                        && entityType.equals(entity.get("type"))) {
                    results.add(new HashMap<>(entity));
                }
            }
        } else if (query.contains("e.type = $entityType") && query.contains("e.status = 'ACTIVE'")) {
            String entityType = (String) params.get("entityType");
            List<Map<String, Object>> candidates = entitiesByType.get(entityType);
            if (candidates != null) {
                for (Map<String, Object> entity : candidates) {
                    if ("ACTIVE".equals(entity.get("status"))) {
                        results.add(new HashMap<>(entity));
                    }
                }
            }
        } else if (query.contains("e.id = $id") || query.contains("{id: $id}")) {
            String id = (String) params.get("id");
            if (entitiesById.containsKey(id)) {
                results.add(new HashMap<>(entitiesById.get(id)));
            }
        } else if (query.contains("SYNONYM_OF") && query.contains("$entityId")) {
            String entityId = (String) params.get("entityId");
            List<Map<String, Object>> syns = synonymsByEntityId.get(entityId);
            if (syns != null) {
                for (Map<String, Object> syn : syns) {
                    results.add(new HashMap<>(syn));
                }
            }
        } else if (query.contains("MERGED_INTO") && params.containsKey("entityId")) {
            // No merge chain in mock - return empty
        } else if (query.contains("count(")) {
            String entityType = (String) params.get("entityType");
            long count = entitiesByType.getOrDefault(entityType, List.of()).stream()
                    .filter(e -> "ACTIVE".equals(e.get("status")))
                    .count();
            results.add(Map.of("count", count));
        }

        return results;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public String getGraphName() {
        return graphName;
    }

    @Override
    public void createIndexes() {
        // No-op for benchmark mock
    }

    @Override
    public void close() {
        entitiesById.clear();
        entitiesByNormalizedName.clear();
        entitiesByType.clear();
        synonymToEntityId.clear();
        synonymsByEntityId.clear();
        relationshipsBySource.clear();
        blockingKeyIndex.clear();
    }
}
