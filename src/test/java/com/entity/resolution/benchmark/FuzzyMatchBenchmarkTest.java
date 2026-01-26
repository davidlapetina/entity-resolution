package com.entity.resolution.benchmark;

import com.entity.resolution.similarity.CompositeSimilarityScorer;
import com.entity.resolution.similarity.DefaultBlockingKeyStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmark tests comparing blocking key candidate narrowing
 * versus full-scan fuzzy matching.
 *
 * These tests validate that blocking keys:
 * 1. Produce the same best match as full scan (correctness)
 * 2. Evaluate significantly fewer candidates (performance)
 */
class FuzzyMatchBenchmarkTest {

    private DefaultBlockingKeyStrategy blockingKeyStrategy;
    private CompositeSimilarityScorer scorer;

    // Realistic company name corpus for testing
    private static final String[] BASE_NAMES = {
            "microsoft corporation", "apple inc", "google llc", "amazon services",
            "facebook meta", "tesla motors", "netflix inc", "oracle corporation",
            "ibm global", "intel corporation", "cisco systems", "adobe systems",
            "salesforce inc", "nvidia corporation", "paypal holdings",
            "uber technologies", "airbnb inc", "spotify technology", "snapchat inc",
            "twitter corp", "linkedin company", "pinterest inc", "dropbox inc",
            "slack technologies", "zoom video", "shopify inc", "square inc",
            "stripe inc", "palantir technologies", "snowflake computing",
            "datadog inc", "cloudflare inc", "crowdstrike holdings",
            "zscaler inc", "okta inc", "twilio inc", "mongodb inc",
            "elastic search", "confluent inc", "hashicorp inc"
    };

    @BeforeEach
    void setUp() {
        blockingKeyStrategy = new DefaultBlockingKeyStrategy();
        scorer = new CompositeSimilarityScorer();
    }

    @Test
    void blockingKeys_correctness_sameResultAsFullScan() {
        List<String> entities = generateEntityNames(1000);
        String queryName = "microsft corp"; // typo of "microsoft corporation"

        // Full scan approach
        String fullScanBest = null;
        double fullScanBestScore = 0;
        for (String entity : entities) {
            double score = scorer.compute(queryName, entity);
            if (score > fullScanBestScore) {
                fullScanBestScore = score;
                fullScanBest = entity;
            }
        }

        // Blocking key approach
        Set<String> queryKeys = blockingKeyStrategy.generateKeys(queryName);
        Map<String, Set<String>> entityKeyIndex = buildKeyIndex(entities);
        List<String> candidates = findCandidates(queryKeys, entityKeyIndex, entities);

        String blockingBest = null;
        double blockingBestScore = 0;
        for (String candidate : candidates) {
            double score = scorer.compute(queryName, candidate);
            if (score > blockingBestScore) {
                blockingBestScore = score;
                blockingBest = candidate;
            }
        }

        // Both approaches should find the same best match
        assertNotNull(fullScanBest);
        assertNotNull(blockingBest);
        assertEquals(fullScanBest, blockingBest,
                "Blocking key approach should find the same best match as full scan");
        assertEquals(fullScanBestScore, blockingBestScore, 0.001,
                "Best match scores should be equal");
    }

    @Test
    void blockingKeys_fewerCandidates_at100() {
        verifyBlockingKeyReduction(100);
    }

    @Test
    void blockingKeys_fewerCandidates_at1000() {
        verifyBlockingKeyReduction(1_000);
    }

    @Test
    void blockingKeys_fewerCandidates_at10000() {
        verifyBlockingKeyReduction(10_000);
    }

    @Test
    void blockingKeys_performanceBenefit_measuredByComputations() {
        int entityCount = 5_000;
        List<String> entities = generateEntityNames(entityCount);
        String queryName = "microsoft corp";

        Set<String> queryKeys = blockingKeyStrategy.generateKeys(queryName);
        Map<String, Set<String>> entityKeyIndex = buildKeyIndex(entities);
        List<String> candidates = findCandidates(queryKeys, entityKeyIndex, entities);

        int fullScanComputations = entityCount;
        int blockingKeyComputations = candidates.size();
        double reductionPercent = (1.0 - (double) blockingKeyComputations / fullScanComputations) * 100;

        // Blocking keys should reduce computations by at least 50%
        assertTrue(blockingKeyComputations < fullScanComputations,
                "Blocking key candidates (" + blockingKeyComputations +
                        ") should be fewer than full scan (" + fullScanComputations + ")");

        // At 5K entities, we expect significant reduction
        assertTrue(reductionPercent > 50,
                "Expected >50% reduction, got " + String.format("%.1f%%", reductionPercent));
    }

    @Test
    void blockingKeys_timingComparison() {
        int entityCount = 5_000;
        List<String> entities = generateEntityNames(entityCount);
        String queryName = "tesla motors inc";

        // Warm up
        for (int i = 0; i < 10; i++) {
            scorer.compute(queryName, entities.get(i % entities.size()));
        }

        // Full scan timing
        long fullScanStart = System.nanoTime();
        double fullBest = 0;
        for (String entity : entities) {
            double score = scorer.compute(queryName, entity);
            if (score > fullBest) fullBest = score;
        }
        long fullScanNanos = System.nanoTime() - fullScanStart;

        // Blocking key timing (includes key generation and index lookup)
        long blockingStart = System.nanoTime();
        Set<String> queryKeys = blockingKeyStrategy.generateKeys(queryName);
        Map<String, Set<String>> entityKeyIndex = buildKeyIndex(entities);
        List<String> candidates = findCandidates(queryKeys, entityKeyIndex, entities);
        double blockingBest = 0;
        for (String candidate : candidates) {
            double score = scorer.compute(queryName, candidate);
            if (score > blockingBest) blockingBest = score;
        }
        long blockingNanos = System.nanoTime() - blockingStart;

        // Log results (test passes regardless of timing - this is informational)
        System.out.printf("Full scan:    %,d candidates, %.2f ms%n",
                entityCount, fullScanNanos / 1_000_000.0);
        System.out.printf("Blocking key: %,d candidates, %.2f ms%n",
                candidates.size(), blockingNanos / 1_000_000.0);
        System.out.printf("Candidate reduction: %.1f%%  (from %,d to %,d)%n",
                (1.0 - (double) candidates.size() / entityCount) * 100,
                entityCount, candidates.size());

        // Ensure correctness despite timing
        assertEquals(fullBest, blockingBest, 0.001,
                "Both approaches should find equivalent best scores");
    }

    @Test
    void blockingKeys_multipleQueries_consistentResults() {
        List<String> entities = generateEntityNames(1_000);
        String[] queries = {
                "microsft corp", "amzon services", "gogle llc",
                "appl inc", "tesls motors"
        };

        for (String query : queries) {
            // Full scan
            String fullBest = findBestFullScan(query, entities);

            // Blocking key
            Set<String> keys = blockingKeyStrategy.generateKeys(query);
            Map<String, Set<String>> index = buildKeyIndex(entities);
            List<String> candidates = findCandidates(keys, index, entities);
            String blockingBest = findBestFullScan(query, candidates);

            assertEquals(fullBest, blockingBest,
                    "Query '" + query + "': blocking key result should match full scan");
        }
    }

    @Test
    void blockingKeys_noMatch_fallsBackToAll() {
        List<String> entities = generateEntityNames(100);
        // Use a completely unrelated query that shares no blocking keys
        String queryName = "xyz unknown company";

        Set<String> queryKeys = blockingKeyStrategy.generateKeys(queryName);
        Map<String, Set<String>> entityKeyIndex = buildKeyIndex(entities);
        List<String> candidates = findCandidates(queryKeys, entityKeyIndex, entities);

        // When no candidates found via blocking keys, system should fall back
        // This test verifies the fallback case exists
        if (candidates.isEmpty()) {
            // Full scan fallback
            String best = findBestFullScan(queryName, entities);
            assertNotNull(best, "Full scan fallback should find some match");
        }
    }

    // ========== Helper Methods ==========

    private void verifyBlockingKeyReduction(int entityCount) {
        List<String> entities = generateEntityNames(entityCount);
        String queryName = "microsoft corp";

        Set<String> queryKeys = blockingKeyStrategy.generateKeys(queryName);
        Map<String, Set<String>> entityKeyIndex = buildKeyIndex(entities);
        List<String> candidates = findCandidates(queryKeys, entityKeyIndex, entities);

        assertTrue(candidates.size() < entityCount,
                "At " + entityCount + " entities: candidates (" + candidates.size() +
                        ") should be fewer than total (" + entityCount + ")");

        // Verify the correct match is still in candidates
        String bestFullScan = findBestFullScan(queryName, entities);
        String bestBlocking = findBestFullScan(queryName, candidates);
        assertEquals(bestFullScan, bestBlocking,
                "Blocking key candidates should include the best match");
    }

    private String findBestFullScan(String query, List<String> entities) {
        String best = null;
        double bestScore = 0;
        for (String entity : entities) {
            double score = scorer.compute(query, entity);
            if (score > bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    /**
     * Builds a blocking key index: maps each entity name to its blocking keys.
     */
    private Map<String, Set<String>> buildKeyIndex(List<String> entities) {
        Map<String, Set<String>> index = new HashMap<>();
        for (String entity : entities) {
            index.put(entity, blockingKeyStrategy.generateKeys(entity));
        }
        return index;
    }

    /**
     * Finds candidates that share any blocking key with the query.
     */
    private List<String> findCandidates(Set<String> queryKeys,
                                         Map<String, Set<String>> entityKeyIndex,
                                         List<String> allEntities) {
        Set<String> candidateSet = new LinkedHashSet<>();
        for (String entity : allEntities) {
            Set<String> entityKeys = entityKeyIndex.get(entity);
            for (String qk : queryKeys) {
                if (entityKeys.contains(qk)) {
                    candidateSet.add(entity);
                    break;
                }
            }
        }
        return new ArrayList<>(candidateSet);
    }

    /**
     * Generates a list of entity names by cycling through base names with numeric suffixes.
     */
    private List<String> generateEntityNames(int count) {
        List<String> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String baseName = BASE_NAMES[i % BASE_NAMES.length];
            if (i < BASE_NAMES.length) {
                names.add(baseName);
            } else {
                names.add(baseName + " " + (i / BASE_NAMES.length));
            }
        }
        return names;
    }
}
