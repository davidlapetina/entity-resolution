package com.entity.resolution.decision;

import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.graph.GraphConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository for persisting and querying MatchDecisionRecord nodes in FalkorDB.
 *
 * <p>Graph model:</p>
 * <pre>
 * (:MatchDecision {id, finalScore, outcome, evaluatedAt, evaluator, ...})
 *   -[:EVALUATED_INPUT]-&gt;(:Entity)      // the input entity (temp or new)
 *   -[:EVALUATED_CANDIDATE]-&gt;(:Entity)  // the existing candidate entity
 * </pre>
 *
 * <p>All decisions are persisted, including NO_MATCH outcomes,
 * to provide full explainability and decision reconstruction.</p>
 */
public class MatchDecisionRepository {
    private static final Logger log = LoggerFactory.getLogger(MatchDecisionRepository.class);

    private final GraphConnection connection;

    public MatchDecisionRepository(GraphConnection connection) {
        this.connection = connection;
    }

    /**
     * Persists a MatchDecisionRecord node and creates edges to input and candidate entities.
     */
    public void save(MatchDecisionRecord decision) {
        String query = """
                CREATE (md:MatchDecision {
                    id: $id,
                    inputEntityTempId: $inputEntityTempId,
                    candidateEntityId: $candidateEntityId,
                    entityType: $entityType,
                    exactScore: $exactScore,
                    levenshteinScore: $levenshteinScore,
                    jaroWinklerScore: $jaroWinklerScore,
                    jaccardScore: $jaccardScore,
                    llmScore: $llmScore,
                    graphContextScore: $graphContextScore,
                    finalScore: $finalScore,
                    outcome: $outcome,
                    autoMergeThreshold: $autoMergeThreshold,
                    synonymThreshold: $synonymThreshold,
                    reviewThreshold: $reviewThreshold,
                    evaluatedAt: $evaluatedAt,
                    evaluator: $evaluator
                })
                """;

        var params = new java.util.HashMap<String, Object>();
        params.put("id", decision.getId());
        params.put("inputEntityTempId", decision.getInputEntityTempId());
        params.put("candidateEntityId", decision.getCandidateEntityId());
        params.put("entityType", decision.getEntityType().name());
        params.put("exactScore", decision.getExactScore());
        params.put("levenshteinScore", decision.getLevenshteinScore());
        params.put("jaroWinklerScore", decision.getJaroWinklerScore());
        params.put("jaccardScore", decision.getJaccardScore());
        params.put("llmScore", decision.getLlmScore() != null ? decision.getLlmScore() : -1.0);
        params.put("graphContextScore", decision.getGraphContextScore() != null ? decision.getGraphContextScore() : -1.0);
        params.put("finalScore", decision.getFinalScore());
        params.put("outcome", decision.getOutcome().name());
        params.put("autoMergeThreshold", decision.getAutoMergeThreshold());
        params.put("synonymThreshold", decision.getSynonymThreshold());
        params.put("reviewThreshold", decision.getReviewThreshold());
        params.put("evaluatedAt", decision.getEvaluatedAt().toString());
        params.put("evaluator", decision.getEvaluator());

        connection.execute(query, params);

        // Create edges to input and candidate entities (best-effort)
        linkToEntities(decision);

        log.debug("Persisted MatchDecision {} outcome={} score={}",
                decision.getId(), decision.getOutcome(), decision.getFinalScore());
    }

    private void linkToEntities(MatchDecisionRecord decision) {
        // Link to input entity
        try {
            String linkInput = """
                    MATCH (md:MatchDecision {id: $decisionId})
                    MATCH (e:Entity {id: $entityId})
                    CREATE (md)-[:EVALUATED_INPUT]->(e)
                    """;
            connection.execute(linkInput, Map.of(
                    "decisionId", decision.getId(),
                    "entityId", decision.getInputEntityTempId()
            ));
        } catch (Exception e) {
            log.debug("Could not link MatchDecision to input entity {}: {}",
                    decision.getInputEntityTempId(), e.getMessage());
        }

        // Link to candidate entity
        try {
            String linkCandidate = """
                    MATCH (md:MatchDecision {id: $decisionId})
                    MATCH (e:Entity {id: $entityId})
                    CREATE (md)-[:EVALUATED_CANDIDATE]->(e)
                    """;
            connection.execute(linkCandidate, Map.of(
                    "decisionId", decision.getId(),
                    "entityId", decision.getCandidateEntityId()
            ));
        } catch (Exception e) {
            log.debug("Could not link MatchDecision to candidate entity {}: {}",
                    decision.getCandidateEntityId(), e.getMessage());
        }
    }

    /**
     * Finds all match decisions that evaluated a given entity as a candidate.
     */
    public List<MatchDecisionRecord> findByCandidateEntityId(String entityId) {
        String query = """
                MATCH (md:MatchDecision)
                WHERE md.candidateEntityId = $entityId
                RETURN md.id as id, md.inputEntityTempId as inputEntityTempId,
                       md.candidateEntityId as candidateEntityId, md.entityType as entityType,
                       md.exactScore as exactScore, md.levenshteinScore as levenshteinScore,
                       md.jaroWinklerScore as jaroWinklerScore, md.jaccardScore as jaccardScore,
                       md.llmScore as llmScore, md.graphContextScore as graphContextScore,
                       md.finalScore as finalScore, md.outcome as outcome,
                       md.autoMergeThreshold as autoMergeThreshold,
                       md.synonymThreshold as synonymThreshold,
                       md.reviewThreshold as reviewThreshold,
                       md.evaluatedAt as evaluatedAt, md.evaluator as evaluator
                ORDER BY md.evaluatedAt DESC
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("entityId", entityId));
        return results.stream().map(this::mapToRecord).toList();
    }

    /**
     * Finds all match decisions that evaluated a given entity as the input.
     */
    public List<MatchDecisionRecord> findByInputEntityId(String entityId) {
        String query = """
                MATCH (md:MatchDecision)
                WHERE md.inputEntityTempId = $entityId
                RETURN md.id as id, md.inputEntityTempId as inputEntityTempId,
                       md.candidateEntityId as candidateEntityId, md.entityType as entityType,
                       md.exactScore as exactScore, md.levenshteinScore as levenshteinScore,
                       md.jaroWinklerScore as jaroWinklerScore, md.jaccardScore as jaccardScore,
                       md.llmScore as llmScore, md.graphContextScore as graphContextScore,
                       md.finalScore as finalScore, md.outcome as outcome,
                       md.autoMergeThreshold as autoMergeThreshold,
                       md.synonymThreshold as synonymThreshold,
                       md.reviewThreshold as reviewThreshold,
                       md.evaluatedAt as evaluatedAt, md.evaluator as evaluator
                ORDER BY md.evaluatedAt DESC
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("entityId", entityId));
        return results.stream().map(this::mapToRecord).toList();
    }

    /**
     * Finds all match decisions involving a given entity (as input or candidate).
     */
    public List<MatchDecisionRecord> findByEntityId(String entityId) {
        String query = """
                MATCH (md:MatchDecision)
                WHERE md.candidateEntityId = $entityId OR md.inputEntityTempId = $entityId
                RETURN md.id as id, md.inputEntityTempId as inputEntityTempId,
                       md.candidateEntityId as candidateEntityId, md.entityType as entityType,
                       md.exactScore as exactScore, md.levenshteinScore as levenshteinScore,
                       md.jaroWinklerScore as jaroWinklerScore, md.jaccardScore as jaccardScore,
                       md.llmScore as llmScore, md.graphContextScore as graphContextScore,
                       md.finalScore as finalScore, md.outcome as outcome,
                       md.autoMergeThreshold as autoMergeThreshold,
                       md.synonymThreshold as synonymThreshold,
                       md.reviewThreshold as reviewThreshold,
                       md.evaluatedAt as evaluatedAt, md.evaluator as evaluator
                ORDER BY md.evaluatedAt DESC
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("entityId", entityId));
        return results.stream().map(this::mapToRecord).toList();
    }

    /**
     * Finds a match decision by its ID.
     */
    public MatchDecisionRecord findById(String decisionId) {
        String query = """
                MATCH (md:MatchDecision {id: $id})
                RETURN md.id as id, md.inputEntityTempId as inputEntityTempId,
                       md.candidateEntityId as candidateEntityId, md.entityType as entityType,
                       md.exactScore as exactScore, md.levenshteinScore as levenshteinScore,
                       md.jaroWinklerScore as jaroWinklerScore, md.jaccardScore as jaccardScore,
                       md.llmScore as llmScore, md.graphContextScore as graphContextScore,
                       md.finalScore as finalScore, md.outcome as outcome,
                       md.autoMergeThreshold as autoMergeThreshold,
                       md.synonymThreshold as synonymThreshold,
                       md.reviewThreshold as reviewThreshold,
                       md.evaluatedAt as evaluatedAt, md.evaluator as evaluator
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("id", decisionId));
        if (results.isEmpty()) {
            return null;
        }
        return mapToRecord(results.get(0));
    }

    /**
     * Finds the match decision linked to a specific review item.
     * Looks up by input entity ID and candidate entity ID matching the review.
     */
    public MatchDecisionRecord findByReviewEntities(String sourceEntityId, String candidateEntityId) {
        String query = """
                MATCH (md:MatchDecision)
                WHERE md.inputEntityTempId = $sourceEntityId
                  AND md.candidateEntityId = $candidateEntityId
                RETURN md.id as id, md.inputEntityTempId as inputEntityTempId,
                       md.candidateEntityId as candidateEntityId, md.entityType as entityType,
                       md.exactScore as exactScore, md.levenshteinScore as levenshteinScore,
                       md.jaroWinklerScore as jaroWinklerScore, md.jaccardScore as jaccardScore,
                       md.llmScore as llmScore, md.graphContextScore as graphContextScore,
                       md.finalScore as finalScore, md.outcome as outcome,
                       md.autoMergeThreshold as autoMergeThreshold,
                       md.synonymThreshold as synonymThreshold,
                       md.reviewThreshold as reviewThreshold,
                       md.evaluatedAt as evaluatedAt, md.evaluator as evaluator
                ORDER BY md.evaluatedAt DESC
                LIMIT 1
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of(
                "sourceEntityId", sourceEntityId,
                "candidateEntityId", candidateEntityId
        ));
        if (results.isEmpty()) {
            return null;
        }
        return mapToRecord(results.get(0));
    }

    private MatchDecisionRecord mapToRecord(Map<String, Object> row) {
        Double llmScore = null;
        Object llmScoreObj = row.get("llmScore");
        if (llmScoreObj != null) {
            double val = ((Number) llmScoreObj).doubleValue();
            if (val >= 0.0) llmScore = val;
        }

        Double graphContextScore = null;
        Object graphCtxObj = row.get("graphContextScore");
        if (graphCtxObj != null) {
            double val = ((Number) graphCtxObj).doubleValue();
            if (val >= 0.0) graphContextScore = val;
        }

        return MatchDecisionRecord.builder()
                .id((String) row.get("id"))
                .inputEntityTempId((String) row.get("inputEntityTempId"))
                .candidateEntityId((String) row.get("candidateEntityId"))
                .entityType(EntityType.valueOf((String) row.get("entityType")))
                .exactScore(((Number) row.get("exactScore")).doubleValue())
                .levenshteinScore(((Number) row.get("levenshteinScore")).doubleValue())
                .jaroWinklerScore(((Number) row.get("jaroWinklerScore")).doubleValue())
                .jaccardScore(((Number) row.get("jaccardScore")).doubleValue())
                .llmScore(llmScore)
                .graphContextScore(graphContextScore)
                .finalScore(((Number) row.get("finalScore")).doubleValue())
                .outcome(DecisionOutcome.valueOf((String) row.get("outcome")))
                .autoMergeThreshold(((Number) row.get("autoMergeThreshold")).doubleValue())
                .synonymThreshold(((Number) row.get("synonymThreshold")).doubleValue())
                .reviewThreshold(((Number) row.get("reviewThreshold")).doubleValue())
                .evaluatedAt(Instant.parse((String) row.get("evaluatedAt")))
                .evaluator((String) row.get("evaluator"))
                .build();
    }
}
