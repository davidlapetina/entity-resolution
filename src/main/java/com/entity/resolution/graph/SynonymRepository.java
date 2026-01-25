package com.entity.resolution.graph;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.core.model.SynonymSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for Synonym node CRUD operations.
 */
public class SynonymRepository {
    private static final Logger log = LoggerFactory.getLogger(SynonymRepository.class);

    private final CypherExecutor executor;

    public SynonymRepository(CypherExecutor executor) {
        this.executor = executor;
    }

    public SynonymRepository(GraphConnection connection) {
        this(new CypherExecutor(connection));
    }

    /**
     * Creates a synonym and links it to an entity.
     */
    public Synonym createForEntity(Synonym synonym, String entityId) {
        executor.createSynonym(
                synonym.getId(),
                synonym.getValue(),
                synonym.getNormalizedValue(),
                synonym.getSource().name(),
                synonym.getConfidence(),
                entityId
        );
        return synonym;
    }

    /**
     * Finds synonyms for a given entity.
     */
    public List<Synonym> findByEntityId(String entityId) {
        List<Map<String, Object>> results = executor.getSynonymsForEntity(entityId);
        return results.stream().map(this::mapToSynonym).toList();
    }

    /**
     * Finds entities that have a matching synonym.
     */
    public List<SynonymMatch> findEntitiesBySynonym(String normalizedValue, EntityType type) {
        List<Map<String, Object>> results = executor.findCandidatesBySynonym(
                normalizedValue, type.name());
        return results.stream().map(row -> new SynonymMatch(
                (String) row.get("id"),
                (String) row.get("canonicalName"),
                (String) row.get("matchedSynonym")
        )).toList();
    }

    private Synonym mapToSynonym(Map<String, Object> row) {
        Synonym.Builder builder = Synonym.builder()
                .id((String) row.get("id"))
                .value((String) row.get("value"))
                .normalizedValue((String) row.get("normalizedValue"));

        Object source = row.get("source");
        if (source != null) {
            builder.source(SynonymSource.valueOf((String) source));
        }

        Object confidence = row.get("confidence");
        if (confidence != null) {
            builder.confidence(((Number) confidence).doubleValue());
        }

        return builder.build();
    }

    /**
     * Record of a synonym match with the entity it points to.
     */
    public record SynonymMatch(String entityId, String entityName, String matchedSynonym) {}

    public CypherExecutor getExecutor() {
        return executor;
    }
}
