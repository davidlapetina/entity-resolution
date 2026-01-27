package com.entity.resolution.benchmark;

import com.entity.resolution.api.EntityResolutionResult;
import com.entity.resolution.api.EntityResolver;
import com.entity.resolution.core.model.EntityType;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for entity resolution operations.
 * Measures throughput and latency of resolving entities against
 * pre-populated datasets of varying sizes.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ResolutionBenchmark {

    @Param({"10000", "100000"})
    private int entityCount;

    private EntityResolver resolver;
    private BenchmarkMockGraphConnection connection;
    private int resolveCounter;

    @Setup(Level.Trial)
    public void setUp() {
        connection = new BenchmarkMockGraphConnection("bench-graph");
        connection.prePopulate(entityCount, "Company");

        resolver = EntityResolver.builder()
                .graphConnection(connection)
                .createIndexes(false)
                .build();

        resolveCounter = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
    }

    /**
     * Benchmark resolving an entity that already exists (exact match on normalized name).
     * Pre-populated entities use the format "entity-N", so resolving "Entity 0" should
     * normalize to "entity-0" and find an exact match.
     */
    @Benchmark
    public void resolveExistingEntity(Blackhole bh) {
        int idx = resolveCounter++ % entityCount;
        EntityResolutionResult result = resolver.resolve("Entity " + idx, EntityType.COMPANY);
        bh.consume(result);
    }

    /**
     * Benchmark resolving an entity that does not exist, requiring a full scan
     * and creation of a new entity.
     */
    @Benchmark
    public void resolveNewEntity(Blackhole bh) {
        String uniqueName = "NewUniqueCompany-" + resolveCounter++;
        EntityResolutionResult result = resolver.resolve(uniqueName, EntityType.COMPANY);
        bh.consume(result);
    }

    /**
     * Benchmark resolving an entity with a name similar to existing entities,
     * triggering fuzzy matching logic.
     */
    @Benchmark
    public void resolveFuzzyMatch(Blackhole bh) {
        int idx = resolveCounter++ % entityCount;
        // Slightly modified name to trigger fuzzy matching
        String fuzzyName = "Entty " + idx + " Corp";
        EntityResolutionResult result = resolver.resolve(fuzzyName, EntityType.COMPANY);
        bh.consume(result);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(ResolutionBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
