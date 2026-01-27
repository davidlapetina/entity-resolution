# Performance Guide

## Benchmark Environment

All benchmarks are run using JMH (Java Microbenchmark Harness) with the following defaults:
- **JMH version**: 1.37
- **JVM**: Java 21 (LTS)
- **Warmup**: 3 iterations, 1 second each
- **Measurement**: 5 iterations, 1 second each
- **Forks**: 1

Benchmarks use `BenchmarkMockGraphConnection`, an in-memory mock optimized for O(1) lookup by normalized name. Real FalkorDB performance will vary based on hardware, network, and dataset characteristics.

## Running Benchmarks

```bash
# Compile benchmark uber-jar
mvn package -Pbenchmark -DskipTests

# Run all benchmarks
java -jar target/benchmarks.jar

# Run specific benchmark class
java -jar target/benchmarks.jar ResolutionBenchmark

# Run with custom parameters
java -jar target/benchmarks.jar -p entityCount=50000 ResolutionBenchmark
```

## Resolution Performance

| Operation | 10K Entities | 100K Entities | Notes |
|-----------|-------------|---------------|-------|
| Exact match (existing) | ~0.01 ms | ~0.01 ms | O(1) via normalized name index |
| New entity creation | ~0.02 ms | ~0.02 ms | Constant time (no scan needed) |
| Fuzzy match | ~5 ms | ~50 ms | O(n) without blocking keys |
| Fuzzy match (blocking keys) | ~0.5 ms | ~1 ms | O(k) where k = candidates |

**Key takeaway**: Blocking keys reduce fuzzy match latency by 10-50x at scale.

## Batch Performance

| Batch Size | Throughput (entities/sec) | With Relationships | Notes |
|-----------|--------------------------|-------------------|-------|
| 100 | ~10,000 | ~7,000 | Small batches have low overhead |
| 1,000 | ~8,000 | ~5,500 | Optimal for most workloads |
| 5,000 | ~6,000 | ~4,000 | Memory pressure at larger sizes |

**Key takeaway**: Batch sizes of 500-2,000 offer the best throughput/memory tradeoff.

## Blocking Key Performance

| Dataset Size | Full Scan (ms) | Blocking Key (ms) | Speedup |
|-------------|----------------|-------------------|---------|
| 1,000 | ~0.5 | ~0.1 | 5x |
| 5,000 | ~2.5 | ~0.2 | 12x |
| 10,000 | ~5.0 | ~0.3 | 17x |

Blocking keys use a three-strategy approach (prefix, sorted tokens, bigrams) to narrow candidates before fuzzy matching.

## Concurrent Performance

Tested with 100 virtual threads, 100 operations per thread (10,000 total ops):

| Operation | p50 (ms) | p95 (ms) | p99 (ms) | Errors |
|-----------|----------|----------|----------|--------|
| Concurrent resolve | ~0.1 | ~0.5 | ~1.0 | 0 |
| Concurrent batch | ~5.0 | ~15.0 | ~25.0 | 0 |
| Mixed read/write | ~0.2 | ~0.8 | ~2.0 | 0 |

## Tuning Guidelines

### Connection Pool Sizing

```java
PoolConfig poolConfig = PoolConfig.builder()
    .maxTotal(20)       // Max connections
    .maxIdle(10)        // Max idle connections
    .minIdle(2)         // Min idle connections
    .build();

EntityResolver resolver = EntityResolver.builder()
    .falkorDBPool(poolConfig)
    .build();
```

**Rule of thumb**: Set `maxTotal` to 2x the expected concurrent thread count. For batch processing, fewer connections (5-10) may suffice since batches serialize writes.

### Cache Configuration

```java
ResolutionOptions options = ResolutionOptions.builder()
    .cachingEnabled(true)
    .cacheMaxSize(10000)     // Max cached entries
    .cacheTtlSeconds(300)    // 5-minute TTL
    .build();
```

| Workload | Recommended Cache Size | TTL |
|----------|----------------------|-----|
| Read-heavy (>80% reads) | 10,000-50,000 | 300s |
| Write-heavy | 1,000-5,000 | 60s |
| Mixed | 5,000-10,000 | 120s |

**Caution**: Large caches with long TTLs may return stale data after merges. The library invalidates cache entries on merge, but external mutations are not tracked.

### Batch Processing

```java
ResolutionOptions options = ResolutionOptions.builder()
    .maxBatchSize(10000)           // Max entities per batch
    .batchCommitChunkSize(1000)    // Entities per commit chunk
    .maxBatchMemoryBytes(100 * 1024 * 1024)  // 100 MB memory limit
    .build();
```

| Parameter | Default | Guidance |
|-----------|---------|----------|
| `maxBatchSize` | 10,000 | Increase for bulk imports, decrease for memory-constrained environments |
| `batchCommitChunkSize` | 1,000 | Smaller chunks = more frequent commits, lower memory usage |
| `maxBatchMemoryBytes` | 100 MB | Monitor JVM heap usage; reduce if GC pressure is high |

### Threshold Tuning

| Threshold | Default | Conservative | Aggressive |
|-----------|---------|-------------|-----------|
| `autoMergeThreshold` | 0.92 | 0.98 | 0.85 |
| `synonymThreshold` | 0.80 | 0.90 | 0.70 |
| `reviewThreshold` | 0.60 | 0.70 | 0.50 |

- **Conservative**: Fewer false merges, more manual reviews
- **Aggressive**: More automatic merges, higher risk of false positives
- **Default**: Balanced for general-purpose entity resolution

### Normalization Impact

Normalization rules significantly affect match quality and performance:
- More rules = better recall but slower normalization
- Rules are applied in priority order; high-priority rules run first
- Custom rules should target the most common variations in your domain
