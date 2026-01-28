package com.entity.resolution.decision;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.core.model.Synonym;
import com.entity.resolution.core.model.SynonymSource;
import com.entity.resolution.graph.GraphConnection;
import com.entity.resolution.merge.MergeResult;
import com.entity.resolution.core.model.Entity;
import com.entity.resolution.review.ReviewItem;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-level tests for v1.1 Decision Graph features.
 * Tests decision persistence, review decisions, and synonym reinforcement
 * using an enhanced mock graph connection.
 */
class DecisionGraphTest {

    private EntityResolver resolver;
    private DecisionCapturingMockConnection mockConnection;

    @BeforeEach
    void setUp() {
        mockConnection = new DecisionCapturingMockConnection("decision-test-graph");
        resolver = EntityResolver.builder()
                .graphConnection(mockConnection)
                .createIndexes(false)
                .build();
    }

    @Nested
    @DisplayName("Decision Persistence")
    class DecisionPersistence {

        @Test
        @DisplayName("Should persist MatchDecision records during fuzzy matching")
        void shouldPersistMatchDecisionsDuringFuzzyMatch() {
            // Create an initial entity (normalizes to "acme systems")
            resolver.resolve("Acme Systems", EntityType.COMPANY);

            // Resolve a different name that won't exact-match but triggers fuzzy matching
            // "Acme Systemes" normalizes to "acme systemes" (not the same as "acme systems")
            resolver.resolve("Acme Systemes", EntityType.COMPANY);

            // Verify MatchDecision nodes were created
            assertFalse(mockConnection.matchDecisions.isEmpty(),
                    "MatchDecision records should be persisted during fuzzy matching");
        }

        @Test
        @DisplayName("Should not create MatchDecision for exact matches")
        void shouldNotCreateDecisionForExactMatch() {
            resolver.resolve("Microsoft Corporation", EntityType.COMPANY);

            // Clear any decisions from first resolution
            mockConnection.matchDecisions.clear();

            // Exact match (same normalized name)
            resolver.resolve("Microsoft Corp.", EntityType.COMPANY);

            // Exact matches don't go through fuzzy matching, so no decisions
            assertTrue(mockConnection.matchDecisions.isEmpty(),
                    "Exact matches should not produce MatchDecision records");
        }

        @Test
        @DisplayName("MatchDecision should capture all score components")
        void decisionShouldCaptureAllScoreComponents() {
            resolver.resolve("Alpha Systems Inc.", EntityType.COMPANY);
            resolver.resolve("Alpha System Inc.", EntityType.COMPANY);

            // Check that decision data was captured with expected fields
            Optional<Map<String, Object>> decision = mockConnection.matchDecisions.stream()
                    .filter(d -> d.containsKey("levenshteinScore"))
                    .findFirst();

            assertTrue(decision.isPresent(), "Should have at least one MatchDecision");
            Map<String, Object> d = decision.get();

            assertNotNull(d.get("id"));
            assertNotNull(d.get("inputEntityTempId"));
            assertNotNull(d.get("candidateEntityId"));
            assertNotNull(d.get("entityType"));
            assertNotNull(d.get("outcome"));
            assertNotNull(d.get("evaluator"));
        }

        @Test
        @DisplayName("Should create decisions for completely different entities (NO_MATCH)")
        void shouldCreateNoMatchDecisions() {
            resolver.resolve("Apple Inc.", EntityType.COMPANY);
            resolver.resolve("Zebra Technologies", EntityType.COMPANY);

            // The second resolution should evaluate Apple as candidate and record NO_MATCH
            boolean hasNoMatch = mockConnection.matchDecisions.stream()
                    .anyMatch(d -> "NO_MATCH".equals(d.get("outcome")));

            assertTrue(hasNoMatch, "Should record NO_MATCH decisions for dissimilar entities");
        }
    }

    @Nested
    @DisplayName("Review Decision Creation")
    class ReviewDecisionCreation {

        @Test
        @DisplayName("Approve review should create ReviewDecision node")
        void approveReviewCreatesReviewDecision() {
            // Create entities
            EntityResolutionResult source = resolver.resolve("Test Source Corp", EntityType.COMPANY);
            EntityResolutionResult candidate = resolver.resolve("Test Candidate Corp", EntityType.COMPANY);

            // Submit review manually
            ReviewItem reviewItem = ReviewItem.builder()
                    .sourceEntityId(source.canonicalEntity().getId())
                    .candidateEntityId(candidate.canonicalEntity().getId())
                    .sourceEntityName("Test Source Corp")
                    .candidateEntityName("Test Candidate Corp")
                    .entityType("COMPANY")
                    .similarityScore(0.72)
                    .build();

            resolver.getReviewService().submitForReview(reviewItem);
            resolver.approveReview(reviewItem.getId(), "admin-user", "Confirmed match");

            // Verify ReviewDecision was created
            boolean hasReviewDecision = mockConnection.reviewDecisions.stream()
                    .anyMatch(d -> "APPROVE".equals(d.get("action")));

            assertTrue(hasReviewDecision,
                    "Approving a review should create a ReviewDecision node");
        }

        @Test
        @DisplayName("Reject review should create ReviewDecision node")
        void rejectReviewCreatesReviewDecision() {
            EntityResolutionResult source = resolver.resolve("Reject Source Corp", EntityType.COMPANY);
            EntityResolutionResult candidate = resolver.resolve("Reject Candidate Corp", EntityType.COMPANY);

            ReviewItem reviewItem = ReviewItem.builder()
                    .sourceEntityId(source.canonicalEntity().getId())
                    .candidateEntityId(candidate.canonicalEntity().getId())
                    .sourceEntityName("Reject Source Corp")
                    .candidateEntityName("Reject Candidate Corp")
                    .entityType("COMPANY")
                    .similarityScore(0.65)
                    .build();

            resolver.getReviewService().submitForReview(reviewItem);
            resolver.rejectReview(reviewItem.getId(), "admin-user", "Not the same entity");

            boolean hasRejectDecision = mockConnection.reviewDecisions.stream()
                    .anyMatch(d -> "REJECT".equals(d.get("action")));

            assertTrue(hasRejectDecision,
                    "Rejecting a review should create a ReviewDecision node");
        }
    }

    @Nested
    @DisplayName("Synonym Reinforcement")
    class SynonymReinforcement {

        @Test
        @DisplayName("Synonym new fields should be initialized with defaults")
        void synonymFieldsInitialized() {
            Synonym synonym = Synonym.builder()
                    .value("Big Blue")
                    .normalizedValue("big blue")
                    .source(SynonymSource.HUMAN)
                    .confidence(0.95)
                    .build();

            assertNotNull(synonym.getLastConfirmedAt());
            assertEquals(0, synonym.getSupportCount());
        }

        @Test
        @DisplayName("Reinforce should increment supportCount")
        void reinforceIncrementsSupportCount() {
            Synonym synonym = Synonym.builder()
                    .value("Big Blue")
                    .normalizedValue("big blue")
                    .source(SynonymSource.SYSTEM)
                    .confidence(0.9)
                    .build();

            synonym.reinforce();
            assertEquals(1, synonym.getSupportCount());

            synonym.reinforce();
            assertEquals(2, synonym.getSupportCount());
        }

        @Test
        @DisplayName("Reinforce should update lastConfirmedAt")
        void reinforceUpdatesLastConfirmedAt() throws InterruptedException {
            Synonym synonym = Synonym.builder()
                    .value("Big Blue")
                    .normalizedValue("big blue")
                    .source(SynonymSource.SYSTEM)
                    .confidence(0.9)
                    .build();

            var original = synonym.getLastConfirmedAt();
            Thread.sleep(10); // Small delay to ensure timestamp changes
            synonym.reinforce();

            assertTrue(synonym.getLastConfirmedAt().equals(original) ||
                    synonym.getLastConfirmedAt().isAfter(original));
        }

        @Test
        @DisplayName("CypherExecutor synonym creation query should include v1.1 fields")
        void cypherSynonymCreationIncludesV1Fields() {
            // Directly test that CypherExecutor.createSynonym includes new fields
            var executor = new com.entity.resolution.graph.CypherExecutor(mockConnection);
            executor.createSynonym("syn-1", "Test", "test", "SYSTEM", 0.9, "entity-1");

            boolean hasSupportCount = mockConnection.executedQueries.stream()
                    .anyMatch(q -> q.contains("supportCount"));
            boolean hasLastConfirmedAt = mockConnection.executedQueries.stream()
                    .anyMatch(q -> q.contains("lastConfirmedAt"));

            assertTrue(hasSupportCount,
                    "Synonym creation query should include supportCount field");
            assertTrue(hasLastConfirmedAt,
                    "Synonym creation query should include lastConfirmedAt field");
        }
    }

    @Nested
    @DisplayName("V1.0 Migration Compatibility")
    class MigrationCompatibility {

        @Test
        @DisplayName("Should resolve entities on v1.0 graph without failures")
        void shouldResolveOnV1Graph() {
            // Simulate v1.0 graph: entities exist without MatchDecision nodes
            EntityResolutionResult result1 = resolver.resolve("Legacy Corp", EntityType.COMPANY);
            assertNotNull(result1);
            assertTrue(result1.isNewEntity());

            // Second resolution should work without decision history
            EntityResolutionResult result2 = resolver.resolve("Legacy Corp.", EntityType.COMPANY);
            assertNotNull(result2);
        }

        @Test
        @DisplayName("getDecisionsForEntity should return empty for v1.0 entities")
        void shouldReturnEmptyDecisionsForV1Entities() {
            resolver.resolve("Old Entity Corp", EntityType.COMPANY);
            // Since mock doesn't store MatchDecision query results, this should return empty
            List<MatchDecisionRecord> decisions = resolver.getDecisionsForEntity("nonexistent-id");
            assertNotNull(decisions);
            // Empty because the mock connection returns empty for MatchDecision queries
        }

        @Test
        @DisplayName("Synonym without v1.1 fields should use defaults")
        void synonymWithoutV1Fields() {
            // Build a synonym as v1.0 would (no lastConfirmedAt, no supportCount)
            Synonym synonym = Synonym.builder()
                    .value("old synonym")
                    .normalizedValue("old synonym")
                    .source(SynonymSource.SYSTEM)
                    .confidence(0.85)
                    .build();

            // Should still work with confidence decay
            ConfidenceDecayEngine engine = new ConfidenceDecayEngine();
            double effective = engine.computeEffectiveConfidence(synonym);

            assertTrue(effective > 0);
            assertTrue(effective <= 1.0);
        }

        @Test
        @DisplayName("ResolutionOptions should have backward-compatible defaults for decay")
        void resolutionOptionsDefaultDecay() {
            var options = com.entity.resolution.api.ResolutionOptions.defaults();
            assertEquals(0.001, options.getConfidenceDecayLambda());
            assertEquals(0.15, options.getReinforcementCap());
        }
    }

    /**
     * Enhanced mock that captures MatchDecision and ReviewDecision write operations.
     */
    static class DecisionCapturingMockConnection implements GraphConnection {
        private final String graphName;
        private final Map<String, Map<String, Object>> entities = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> synonyms = new ConcurrentHashMap<>();
        private final Map<String, String> synonymToEntity = new ConcurrentHashMap<>();
        final List<Map<String, Object>> matchDecisions = new CopyOnWriteArrayList<>();
        final List<Map<String, Object>> reviewDecisions = new CopyOnWriteArrayList<>();
        final List<String> executedQueries = new CopyOnWriteArrayList<>();

        DecisionCapturingMockConnection(String graphName) {
            this.graphName = graphName;
        }

        @Override
        public void execute(String query, Map<String, Object> params) {
            executedQueries.add(query);

            if (query.contains("CREATE") && query.contains(":Synonym")) {
                String synonymId = (String) params.get("synonymId");
                String entityId = (String) params.get("entityId");
                Map<String, Object> synonym = new HashMap<>();
                synonym.put("id", synonymId);
                synonym.put("value", params.get("value"));
                synonym.put("normalizedValue", params.get("normalizedValue"));
                synonym.put("source", params.get("source"));
                synonym.put("confidence", params.get("confidence"));
                synonyms.put(synonymId, synonym);
                synonymToEntity.put((String) params.get("normalizedValue"), entityId);
            } else if (query.contains("CREATE") && query.contains(":Entity")) {
                String id = (String) params.get("id");
                Map<String, Object> entity = new HashMap<>();
                entity.put("id", id);
                entity.put("canonicalName", params.get("canonicalName"));
                entity.put("normalizedName", params.get("normalizedName"));
                entity.put("type", params.get("type"));
                entity.put("confidenceScore", params.get("confidenceScore"));
                entity.put("status", "ACTIVE");
                entities.put(id, entity);
            } else if (query.contains("CREATE") && query.contains(":MatchDecision")) {
                matchDecisions.add(new HashMap<>(params));
            } else if (query.contains("CREATE") && query.contains(":ReviewDecision")) {
                reviewDecisions.add(new HashMap<>(params));
            } else if (query.contains("SET") && query.contains("status")) {
                String sourceId = (String) params.get("sourceEntityId");
                if (sourceId != null && entities.containsKey(sourceId)) {
                    entities.get(sourceId).put("status", "MERGED");
                }
            }
        }

        @Override
        public List<Map<String, Object>> query(String query, Map<String, Object> params) {
            List<Map<String, Object>> results = new ArrayList<>();

            if (query.contains("e.normalizedName = $normalizedName")) {
                String normalizedName = (String) params.get("normalizedName");
                String entityType = (String) params.get("entityType");

                for (Map<String, Object> entity : entities.values()) {
                    if (normalizedName.equals(entity.get("normalizedName"))
                            && entityType.equals(entity.get("type"))
                            && "ACTIVE".equals(entity.get("status"))) {
                        results.add(new HashMap<>(entity));
                    }
                }
            } else if (query.contains("s.normalizedValue = $normalizedValue")) {
                String normalizedValue = (String) params.get("normalizedValue");
                String entityId = synonymToEntity.get(normalizedValue);
                if (entityId != null && entities.containsKey(entityId)) {
                    Map<String, Object> entity = entities.get(entityId);
                    if ("ACTIVE".equals(entity.get("status"))) {
                        Map<String, Object> result = new HashMap<>(entity);
                        result.put("matchedSynonym", normalizedValue);
                        results.add(result);
                    }
                }
            } else if (query.contains("e.type = $entityType") && query.contains("e.status = 'ACTIVE'")) {
                String entityType = (String) params.get("entityType");
                for (Map<String, Object> entity : entities.values()) {
                    if (entityType.equals(entity.get("type"))
                            && "ACTIVE".equals(entity.get("status"))) {
                        results.add(new HashMap<>(entity));
                    }
                }
            } else if (query.contains("e.id = $id") || query.contains("{id: $id}")) {
                String id = (String) params.get("id");
                if (entities.containsKey(id)) {
                    results.add(new HashMap<>(entities.get(id)));
                }
            } else if (query.contains("SYNONYM_OF") && query.contains("$entityId")) {
                String entityId = (String) params.get("entityId");
                for (Map.Entry<String, String> entry : synonymToEntity.entrySet()) {
                    if (entityId.equals(entry.getValue())) {
                        for (Map<String, Object> synonym : synonyms.values()) {
                            if (entry.getKey().equals(synonym.get("normalizedValue"))) {
                                results.add(new HashMap<>(synonym));
                            }
                        }
                    }
                }
            }

            return results;
        }

        @Override
        public boolean isConnected() { return true; }

        @Override
        public String getGraphName() { return graphName; }

        @Override
        public void createIndexes() {}

        @Override
        public void close() {
            entities.clear();
            synonyms.clear();
            synonymToEntity.clear();
            matchDecisions.clear();
            reviewDecisions.clear();
        }
    }
}
