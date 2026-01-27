package com.entity.resolution.benchmark;

import com.entity.resolution.core.model.Entity;
import com.entity.resolution.core.model.EntityType;
import com.entity.resolution.rules.DefaultNormalizationRules;
import com.entity.resolution.rules.NormalizationEngine;
import com.entity.resolution.similarity.CompositeSimilarityScorer;
import com.entity.resolution.similarity.DefaultBlockingKeyStrategy;
import com.entity.resolution.similarity.SimilarityWeights;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks comparing full-scan fuzzy matching vs blocking-key-based
 * candidate narrowing at different dataset scales.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BlockingKeyBenchmark {

    @Param({"1000", "5000", "10000"})
    private int entityCount;

    private List<Entity> entities;
    private Map<String, Set<String>> blockingKeyIndex;
    private CompositeSimilarityScorer scorer;
    private DefaultBlockingKeyStrategy blockingKeyStrategy;
    private NormalizationEngine normalizationEngine;
    private int queryCounter;

    @Setup(Level.Trial)
    public void setUp() {
        scorer = new CompositeSimilarityScorer(new SimilarityWeights(0.4, 0.35, 0.25));

        blockingKeyStrategy = new DefaultBlockingKeyStrategy();
        normalizationEngine = new NormalizationEngine(DefaultNormalizationRules.getCompanyRules());

        entities = new ArrayList<>(entityCount);
        blockingKeyIndex = new HashMap<>();

        for (int i = 0; i < entityCount; i++) {
            String name = "Company " + i + " Corp";
            String normalized = normalizationEngine.normalize(name, EntityType.COMPANY);

            Entity entity = Entity.builder()
                    .canonicalName(name)
                    .normalizedName(normalized)
                    .type(EntityType.COMPANY)
                    .confidenceScore(1.0)
                    .build();
            entities.add(entity);

            Set<String> keys = blockingKeyStrategy.generateKeys(normalized);
            for (String key : keys) {
                blockingKeyIndex
                        .computeIfAbsent(key, k -> new HashSet<>())
                        .add(entity.getId());
            }
        }

        queryCounter = 0;
    }

    /**
     * Full-scan fuzzy matching: computes similarity against every entity.
     * This is O(n) in the number of entities.
     */
    @Benchmark
    public void fullScanFuzzyMatch(Blackhole bh) {
        int idx = queryCounter++ % entityCount;
        String queryName = "Companny " + idx + " Corporation";
        String normalizedQuery = normalizationEngine.normalize(queryName, EntityType.COMPANY);

        double bestScore = 0.0;
        Entity bestMatch = null;

        for (Entity entity : entities) {
            double score = scorer.compute(normalizedQuery, entity.getNormalizedName());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = entity;
            }
        }

        bh.consume(bestMatch);
        bh.consume(bestScore);
    }

    /**
     * Blocking-key fuzzy matching: first narrows candidates via blocking keys,
     * then computes similarity only against the candidate set.
     */
    @Benchmark
    public void blockingKeyFuzzyMatch(Blackhole bh) {
        int idx = queryCounter++ % entityCount;
        String queryName = "Companny " + idx + " Corporation";
        String normalizedQuery = normalizationEngine.normalize(queryName, EntityType.COMPANY);

        Set<String> queryKeys = blockingKeyStrategy.generateKeys(normalizedQuery);
        Set<String> candidateIds = new HashSet<>();
        for (String key : queryKeys) {
            Set<String> ids = blockingKeyIndex.get(key);
            if (ids != null) {
                candidateIds.addAll(ids);
            }
        }

        double bestScore = 0.0;
        Entity bestMatch = null;

        for (Entity entity : entities) {
            if (candidateIds.contains(entity.getId())) {
                double score = scorer.compute(normalizedQuery, entity.getNormalizedName());
                if (score > bestScore) {
                    bestScore = score;
                    bestMatch = entity;
                }
            }
        }

        bh.consume(bestMatch);
        bh.consume(bestScore);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BlockingKeyBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
