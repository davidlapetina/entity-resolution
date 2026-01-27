package com.entity.resolution.review;

import com.entity.resolution.api.Page;
import com.entity.resolution.api.PageRequest;
import com.entity.resolution.graph.GraphConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Graph-backed implementation of {@link ReviewQueue} using FalkorDB.
 * Stores review items as nodes in the graph database.
 */
public class GraphReviewQueue implements ReviewQueue {
    private static final Logger log = LoggerFactory.getLogger(GraphReviewQueue.class);

    private final GraphConnection connection;

    public GraphReviewQueue(GraphConnection connection) {
        this.connection = connection;
        createIndexes();
    }

    private void createIndexes() {
        try {
            connection.execute("CREATE INDEX FOR (ri:ReviewItem) ON (ri.id)");
        } catch (Exception e) {
            log.debug("ReviewItem id index may already exist: {}", e.getMessage());
        }
        try {
            connection.execute("CREATE INDEX FOR (ri:ReviewItem) ON (ri.status)");
        } catch (Exception e) {
            log.debug("ReviewItem status index may already exist: {}", e.getMessage());
        }
        try {
            connection.execute("CREATE INDEX FOR (ri:ReviewItem) ON (ri.entityType)");
        } catch (Exception e) {
            log.debug("ReviewItem entityType index may already exist: {}", e.getMessage());
        }
    }

    @Override
    public ReviewItem submit(ReviewItem item) {
        String query = """
                CREATE (ri:ReviewItem {
                    id: $id,
                    sourceEntityId: $sourceEntityId,
                    candidateEntityId: $candidateEntityId,
                    sourceEntityName: $sourceEntityName,
                    candidateEntityName: $candidateEntityName,
                    entityType: $entityType,
                    similarityScore: $similarityScore,
                    status: $status,
                    submittedAt: $submittedAt
                })
                """;
        connection.execute(query, Map.of(
                "id", item.getId(),
                "sourceEntityId", item.getSourceEntityId(),
                "candidateEntityId", item.getCandidateEntityId(),
                "sourceEntityName", item.getSourceEntityName() != null ? item.getSourceEntityName() : "",
                "candidateEntityName", item.getCandidateEntityName() != null ? item.getCandidateEntityName() : "",
                "entityType", item.getEntityType() != null ? item.getEntityType() : "",
                "similarityScore", item.getSimilarityScore(),
                "status", ReviewStatus.PENDING.name(),
                "submittedAt", item.getSubmittedAt().toString()
        ));
        log.debug("Submitted review item {} to graph", item.getId());
        return item;
    }

    @Override
    public Page<ReviewItem> getPending(PageRequest page) {
        long total = countPending();
        String query = """
                MATCH (ri:ReviewItem)
                WHERE ri.status = 'PENDING'
                RETURN ri.id as id, ri.sourceEntityId as sourceEntityId,
                       ri.candidateEntityId as candidateEntityId,
                       ri.sourceEntityName as sourceEntityName,
                       ri.candidateEntityName as candidateEntityName,
                       ri.entityType as entityType,
                       ri.similarityScore as similarityScore,
                       ri.status as status, ri.submittedAt as submittedAt
                ORDER BY ri.submittedAt ASC
                SKIP $offset LIMIT $limit
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of(
                "offset", page.offset(),
                "limit", page.limit()
        ));
        List<ReviewItem> items = results.stream().map(this::mapToReviewItem).toList();
        return new Page<>(items, total, page.pageNumber(), page.limit());
    }

    @Override
    public Page<ReviewItem> getPendingByEntityType(String entityType, PageRequest page) {
        long total = countPendingByEntityType(entityType);
        String query = """
                MATCH (ri:ReviewItem)
                WHERE ri.status = 'PENDING' AND ri.entityType = $entityType
                RETURN ri.id as id, ri.sourceEntityId as sourceEntityId,
                       ri.candidateEntityId as candidateEntityId,
                       ri.sourceEntityName as sourceEntityName,
                       ri.candidateEntityName as candidateEntityName,
                       ri.entityType as entityType,
                       ri.similarityScore as similarityScore,
                       ri.status as status, ri.submittedAt as submittedAt
                ORDER BY ri.submittedAt ASC
                SKIP $offset LIMIT $limit
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of(
                "entityType", entityType,
                "offset", page.offset(),
                "limit", page.limit()
        ));
        List<ReviewItem> items = results.stream().map(this::mapToReviewItem).toList();
        return new Page<>(items, total, page.pageNumber(), page.limit());
    }

    @Override
    public Page<ReviewItem> getPendingByScoreRange(double minScore, double maxScore, PageRequest page) {
        long total = countPendingByScoreRange(minScore, maxScore);
        String query = """
                MATCH (ri:ReviewItem)
                WHERE ri.status = 'PENDING'
                  AND ri.similarityScore >= $minScore
                  AND ri.similarityScore <= $maxScore
                RETURN ri.id as id, ri.sourceEntityId as sourceEntityId,
                       ri.candidateEntityId as candidateEntityId,
                       ri.sourceEntityName as sourceEntityName,
                       ri.candidateEntityName as candidateEntityName,
                       ri.entityType as entityType,
                       ri.similarityScore as similarityScore,
                       ri.status as status, ri.submittedAt as submittedAt
                ORDER BY ri.similarityScore DESC
                SKIP $offset LIMIT $limit
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of(
                "minScore", minScore,
                "maxScore", maxScore,
                "offset", page.offset(),
                "limit", page.limit()
        ));
        List<ReviewItem> items = results.stream().map(this::mapToReviewItem).toList();
        return new Page<>(items, total, page.pageNumber(), page.limit());
    }

    @Override
    public void approve(String reviewId, String reviewerId, String notes) {
        ReviewItem item = get(reviewId);
        if (item == null) {
            throw new IllegalArgumentException("Review item not found: " + reviewId);
        }
        if (!item.isPending()) {
            throw new IllegalStateException("Review item is not pending: " + reviewId);
        }
        String query = """
                MATCH (ri:ReviewItem {id: $reviewId})
                SET ri.status = 'APPROVED',
                    ri.reviewedAt = $reviewedAt,
                    ri.reviewerId = $reviewerId,
                    ri.notes = $notes
                """;
        connection.execute(query, Map.of(
                "reviewId", reviewId,
                "reviewedAt", Instant.now().toString(),
                "reviewerId", reviewerId != null ? reviewerId : "",
                "notes", notes != null ? notes : ""
        ));
        log.info("Review item {} approved by {}", reviewId, reviewerId);
    }

    @Override
    public void reject(String reviewId, String reviewerId, String notes) {
        ReviewItem item = get(reviewId);
        if (item == null) {
            throw new IllegalArgumentException("Review item not found: " + reviewId);
        }
        if (!item.isPending()) {
            throw new IllegalStateException("Review item is not pending: " + reviewId);
        }
        String query = """
                MATCH (ri:ReviewItem {id: $reviewId})
                SET ri.status = 'REJECTED',
                    ri.reviewedAt = $reviewedAt,
                    ri.reviewerId = $reviewerId,
                    ri.notes = $notes
                """;
        connection.execute(query, Map.of(
                "reviewId", reviewId,
                "reviewedAt", Instant.now().toString(),
                "reviewerId", reviewerId != null ? reviewerId : "",
                "notes", notes != null ? notes : ""
        ));
        log.info("Review item {} rejected by {}", reviewId, reviewerId);
    }

    @Override
    public ReviewItem get(String reviewId) {
        String query = """
                MATCH (ri:ReviewItem {id: $reviewId})
                RETURN ri.id as id, ri.sourceEntityId as sourceEntityId,
                       ri.candidateEntityId as candidateEntityId,
                       ri.sourceEntityName as sourceEntityName,
                       ri.candidateEntityName as candidateEntityName,
                       ri.entityType as entityType,
                       ri.similarityScore as similarityScore,
                       ri.status as status, ri.submittedAt as submittedAt,
                       ri.reviewedAt as reviewedAt, ri.reviewerId as reviewerId,
                       ri.notes as notes
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("reviewId", reviewId));
        if (results.isEmpty()) {
            return null;
        }
        return mapToReviewItem(results.get(0));
    }

    @Override
    public long countPending() {
        String query = """
                MATCH (ri:ReviewItem)
                WHERE ri.status = 'PENDING'
                RETURN count(ri) as total
                """;
        List<Map<String, Object>> results = connection.query(query);
        if (results.isEmpty()) return 0;
        return ((Number) results.get(0).get("total")).longValue();
    }

    private long countPendingByEntityType(String entityType) {
        String query = """
                MATCH (ri:ReviewItem)
                WHERE ri.status = 'PENDING' AND ri.entityType = $entityType
                RETURN count(ri) as total
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of("entityType", entityType));
        if (results.isEmpty()) return 0;
        return ((Number) results.get(0).get("total")).longValue();
    }

    private long countPendingByScoreRange(double minScore, double maxScore) {
        String query = """
                MATCH (ri:ReviewItem)
                WHERE ri.status = 'PENDING'
                  AND ri.similarityScore >= $minScore
                  AND ri.similarityScore <= $maxScore
                RETURN count(ri) as total
                """;
        List<Map<String, Object>> results = connection.query(query, Map.of(
                "minScore", minScore, "maxScore", maxScore));
        if (results.isEmpty()) return 0;
        return ((Number) results.get(0).get("total")).longValue();
    }

    private ReviewItem mapToReviewItem(Map<String, Object> row) {
        ReviewItem.Builder builder = ReviewItem.builder()
                .id((String) row.get("id"))
                .sourceEntityId((String) row.get("sourceEntityId"))
                .candidateEntityId((String) row.get("candidateEntityId"))
                .sourceEntityName((String) row.get("sourceEntityName"))
                .candidateEntityName((String) row.get("candidateEntityName"))
                .entityType((String) row.get("entityType"))
                .similarityScore(((Number) row.get("similarityScore")).doubleValue())
                .status(ReviewStatus.valueOf((String) row.get("status")));

        Object submittedAt = row.get("submittedAt");
        if (submittedAt instanceof String s && !s.isEmpty()) {
            builder.submittedAt(Instant.parse(s));
        }
        Object reviewedAt = row.get("reviewedAt");
        if (reviewedAt instanceof String s && !s.isEmpty()) {
            builder.reviewedAt(Instant.parse(s));
        }
        Object reviewerId = row.get("reviewerId");
        if (reviewerId instanceof String s && !s.isEmpty()) {
            builder.reviewerId(s);
        }
        Object notes = row.get("notes");
        if (notes instanceof String s && !s.isEmpty()) {
            builder.notes(s);
        }

        return builder.build();
    }
}
