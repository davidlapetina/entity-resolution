package com.entity.resolution.decision;

import com.entity.resolution.graph.GraphConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Repository for persisting and querying ReviewDecision nodes in FalkorDB.
 *
 * <p>Graph model:</p>
 * <pre>
 * (:ReviewDecision {id, action, reviewerId, rationale, decidedAt})
 *   -[:DECIDES]-&gt;(:ReviewItem)         // linked by reviewId property match
 *   -[:CONFIRMS]-&gt;(:MatchDecision)     // linked to the original match decision
 * </pre>
 *
 * <p>ReviewDecisions are immutable and append-only. They never mutate the
 * MatchDecision they reference.</p>
 */
public class ReviewDecisionRepository {
    private static final Logger log = LoggerFactory.getLogger(ReviewDecisionRepository.class);

    private final GraphConnection connection;

    public ReviewDecisionRepository(GraphConnection connection) {
        this.connection = connection;
    }

    /**
     * Persists a ReviewDecision node and creates edges to the review and match decision.
     *
     * @param decision           the review decision to persist
     * @param matchDecisionId    the ID of the MatchDecisionRecord this decision confirms/rejects (nullable)
     */
    public void save(ReviewDecision decision, String matchDecisionId) {
        String query = """
                CREATE (rd:ReviewDecision {
                    id: $id,
                    reviewId: $reviewId,
                    action: $action,
                    reviewerId: $reviewerId,
                    rationale: $rationale,
                    decidedAt: $decidedAt
                })
                """;

        var params = new java.util.HashMap<String, Object>();
        params.put("id", decision.getId());
        params.put("reviewId", decision.getReviewId());
        params.put("action", decision.getAction().name());
        params.put("reviewerId", decision.getReviewerId());
        params.put("rationale", decision.getRationale() != null ? decision.getRationale() : "");
        params.put("decidedAt", decision.getDecidedAt().toString());

        connection.execute(query, params);

        // Link to MatchDecision if available
        if (matchDecisionId != null) {
            try {
                String linkQuery = """
                        MATCH (rd:ReviewDecision {id: $rdId})
                        MATCH (md:MatchDecision {id: $mdId})
                        CREATE (rd)-[:CONFIRMS]->(md)
                        """;
                connection.execute(linkQuery, Map.of(
                        "rdId", decision.getId(),
                        "mdId", matchDecisionId
                ));
            } catch (Exception e) {
                log.debug("Could not link ReviewDecision to MatchDecision {}: {}",
                        matchDecisionId, e.getMessage());
            }
        }

        log.debug("Persisted ReviewDecision {} action={} reviewId={}",
                decision.getId(), decision.getAction(), decision.getReviewId());
    }

    /**
     * Finds review decisions for a given review item ID.
     */
    public List<ReviewDecision> findByReviewId(String reviewId) {
        String query = """
                MATCH (rd:ReviewDecision)
                WHERE rd.reviewId = $reviewId
                RETURN rd.id as id, rd.reviewId as reviewId, rd.action as action,
                       rd.reviewerId as reviewerId, rd.rationale as rationale,
                       rd.decidedAt as decidedAt
                ORDER BY rd.decidedAt DESC
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("reviewId", reviewId));
        return results.stream().map(this::mapToDecision).toList();
    }

    /**
     * Finds the review decision for a given review item ID (most recent).
     */
    public ReviewDecision findLatestByReviewId(String reviewId) {
        List<ReviewDecision> decisions = findByReviewId(reviewId);
        return decisions.isEmpty() ? null : decisions.get(0);
    }

    /**
     * Finds a review decision by its ID.
     */
    public ReviewDecision findById(String decisionId) {
        String query = """
                MATCH (rd:ReviewDecision {id: $id})
                RETURN rd.id as id, rd.reviewId as reviewId, rd.action as action,
                       rd.reviewerId as reviewerId, rd.rationale as rationale,
                       rd.decidedAt as decidedAt
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("id", decisionId));
        if (results.isEmpty()) {
            return null;
        }
        return mapToDecision(results.get(0));
    }

    private ReviewDecision mapToDecision(Map<String, Object> row) {
        String rationale = (String) row.get("rationale");
        if (rationale != null && rationale.isEmpty()) {
            rationale = null;
        }
        return ReviewDecision.builder()
                .id((String) row.get("id"))
                .reviewId((String) row.get("reviewId"))
                .action(ReviewAction.valueOf((String) row.get("action")))
                .reviewerId((String) row.get("reviewerId"))
                .rationale(rationale)
                .decidedAt(Instant.parse((String) row.get("decidedAt")))
                .build();
    }
}
