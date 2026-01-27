package com.entity.resolution.benchmark;

import com.entity.resolution.api.*;
import com.entity.resolution.core.model.EntityReference;
import com.entity.resolution.core.model.EntityType;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for batch entity resolution operations.
 * Measures throughput and latency of batch processing with varying batch sizes.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BatchBenchmark {

    @Param({"100", "1000", "5000"})
    private int batchSize;

    private EntityResolver resolver;
    private BenchmarkMockGraphConnection connection;
    private int batchCounter;

    @Setup(Level.Trial)
    public void setUp() {
        connection = new BenchmarkMockGraphConnection("batch-bench-graph");
        resolver = EntityResolver.builder()
                .graphConnection(connection)
                .createIndexes(false)
                .build();
        batchCounter = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (resolver != null) {
            resolver.close();
        }
    }

    /**
     * Benchmark batch resolution of entities without relationships.
     */
    @Benchmark
    public void batchResolution(Blackhole bh) {
        int offset = batchCounter++ * batchSize;
        try (BatchContext batch = resolver.beginBatch()) {
            for (int i = 0; i < batchSize; i++) {
                EntityResolutionResult result = batch.resolve(
                        "BatchCompany-" + (offset + i), EntityType.COMPANY);
                bh.consume(result);
            }
            BatchResult result = batch.commit();
            bh.consume(result);
        }
    }

    /**
     * Benchmark batch resolution with relationship creation between consecutive entities.
     */
    @Benchmark
    public void batchWithRelationships(Blackhole bh) {
        int offset = batchCounter++ * batchSize;
        try (BatchContext batch = resolver.beginBatch()) {
            EntityReference previous = null;
            for (int i = 0; i < batchSize; i++) {
                EntityResolutionResult result = batch.resolve(
                        "RelBatchCompany-" + (offset + i), EntityType.COMPANY);
                EntityReference current = result.getEntityReference();

                if (previous != null) {
                    batch.createRelationship(previous, current, "PARTNER");
                }
                previous = current;
                bh.consume(result);
            }
            BatchResult result = batch.commit();
            bh.consume(result);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BatchBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
