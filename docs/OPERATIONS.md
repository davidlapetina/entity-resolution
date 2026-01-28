# Operations Guide

## Health Monitoring

### Health Check API

The library provides a built-in health check system:

```java
HealthStatus status = resolver.health();

// Overall status
System.out.println("Status: " + status.status());    // UP, DEGRADED, or DOWN
System.out.println("Message: " + status.message());

// Component details
Map<String, Object> details = status.details();
```

### Health Check Components

The `HealthCheckRegistry` aggregates individual checks:

| Check | What It Validates | DOWN When |
|-------|-------------------|-----------|
| Database Connectivity | FalkorDB connection is alive | `isConnected()` returns false |
| Connection Pool | Pool has available connections | All connections exhausted |
| Memory | JVM heap usage is within limits | Heap usage > 90% |

**Aggregation rules:**
- Any DOWN check → overall status is **DOWN**
- Any DEGRADED check (none DOWN) → overall status is **DEGRADED**
- All UP → overall status is **UP**

### Custom Health Checks

Register custom checks:

```java
HealthCheckRegistry registry = new HealthCheckRegistry();
registry.register(new HealthCheck() {
    @Override
    public String getName() { return "custom-check"; }

    @Override
    public HealthStatus check() {
        return myService.isHealthy()
            ? HealthStatus.up()
            : HealthStatus.down("Service unavailable");
    }
});
```

## Key Metrics

When Micrometer is on the classpath, the library emits metrics through `MetricsService`:

### Resolution Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `entity.resolution.count` | Counter | Total resolution attempts |
| `entity.resolution.new` | Counter | New entities created |
| `entity.resolution.matched` | Counter | Entities matched to existing |
| `entity.resolution.merged` | Counter | Entities merged |
| `entity.resolution.duration` | Timer | Resolution latency |

### Batch Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `entity.batch.count` | Counter | Total batch operations |
| `entity.batch.size` | Summary | Entities per batch |
| `entity.batch.duration` | Timer | Batch commit latency |
| `entity.batch.relationships` | Counter | Relationships created in batches |

### Merge Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `entity.merge.count` | Counter | Total merge operations |
| `entity.merge.duration` | Timer | Merge latency |
| `entity.merge.compensation` | Counter | Compensating transactions triggered |

### Cache Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `entity.cache.hits` | Counter | Cache hit count |
| `entity.cache.misses` | Counter | Cache miss count |
| `entity.cache.evictions` | Counter | Cache eviction count |
| `entity.cache.size` | Gauge | Current cache size |

### Connection Pool Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `entity.pool.active` | Gauge | Active connections |
| `entity.pool.idle` | Gauge | Idle connections |
| `entity.pool.waiting` | Gauge | Threads waiting for connection |

## Common Issues and Troubleshooting

### Querying the Decision Graph (v1.1)

The decision graph provides full explainability for match evaluations and human review decisions:

```java
// Get all match decisions involving an entity
List<MatchDecisionRecord> decisions = resolver.getDecisionsForEntity(entityId);

// Inspect score breakdown
for (MatchDecisionRecord d : decisions) {
    System.out.printf("Candidate: %s | Final: %.3f | Outcome: %s%n",
        d.getCandidateEntityId(), d.getFinalScore(), d.getOutcome());
    System.out.printf("  Levenshtein: %.3f | Jaro-Winkler: %.3f | Jaccard: %.3f%n",
        d.getLevenshteinScore(), d.getJaroWinklerScore(), d.getJaccardScore());
    System.out.printf("  Thresholds: merge=%.2f synonym=%.2f review=%.2f%n",
        d.getAutoMergeThreshold(), d.getSynonymThreshold(), d.getReviewThreshold());
}

// Get review decision for a review item
ReviewDecision reviewDecision = resolver.getReviewDecisionRepository()
    .findLatestByReviewId(reviewId);
```

### Synonym Confidence Decay

Synonym effective confidence is computed lazily using exponential decay:

```
effectiveConfidence = baseConfidence * exp(-lambda * daysSinceLastConfirmed) + reinforcementBoost
```

To check which synonyms have decayed below thresholds:

```java
ConfidenceDecayEngine engine = resolver.getConfidenceDecayEngine();
List<Synonym> synonyms = resolver.getService().getSynonymRepository().findByEntityId(entityId);

for (Synonym s : synonyms) {
    double effective = engine.computeEffectiveConfidence(s);
    boolean needsReview = engine.shouldTriggerReview(s, 0.80);
    boolean isStale = engine.isStale(s, 0.60);
    System.out.printf("Synonym: %s | base=%.3f | effective=%.3f | stale=%s%n",
        s.getValue(), s.getConfidence(), effective, isStale);
}
```

### Issue: Connection Refused

**Symptoms:**
- `java.net.ConnectException: Connection refused`
- Health check returns DOWN

**Causes and fixes:**
1. FalkorDB not running → Start FalkorDB: `docker run -p 6379:6379 falkordb/falkordb:latest`
2. Wrong host/port → Verify connection parameters match FalkorDB configuration
3. Firewall blocking port → Check firewall rules for port 6379

### Issue: Slow Resolution

**Symptoms:**
- Resolution latency > 100ms for individual operations
- Batch processing taking longer than expected

**Causes and fixes:**
1. No blocking keys → Ensure `BlockingKeyStrategy` is configured (default is enabled)
2. Large dataset without indexes → Call `resolver.getConnection().createIndexes()`
3. Cache disabled → Enable caching: `ResolutionOptions.builder().cachingEnabled(true)`
4. Connection pool too small → Increase `PoolConfig.maxTotal` for concurrent workloads
5. FalkorDB under memory pressure → Monitor FalkorDB memory usage; increase container memory limits

### Issue: Duplicate Entities Created

**Symptoms:**
- Same entity name appearing multiple times with different IDs
- Normalization not matching expected variations

**Causes and fixes:**
1. Missing normalization rules → Add domain-specific rules to `NormalizationEngine`
2. Threshold too high → Lower `autoMergeThreshold` (default 0.92)
3. Concurrent creation race → Enable distributed locking for multi-instance deployments
4. Entity type mismatch → Verify the same `EntityType` is used consistently

### Issue: Merge Not Triggering

**Symptoms:**
- Similar entities not being automatically merged
- Match scores below threshold

**Causes and fixes:**
1. `autoMergeEnabled` is false → Set to true in `ResolutionOptions`
2. Threshold too high → Lower `autoMergeThreshold`
3. Poor normalization → Review normalization rules; add missing suffix/prefix rules
4. Different entity types → Entities of different types are never merged

### Issue: OutOfMemoryError During Batch

**Symptoms:**
- `java.lang.OutOfMemoryError: Java heap space` during large batches
- Batch memory limit exceeded warnings

**Causes and fixes:**
1. Batch too large → Reduce `maxBatchSize` or increase `maxBatchMemoryBytes`
2. JVM heap too small → Increase heap: `-Xmx2g`
3. Too many pending relationships → Reduce batch size; commit more frequently

### Issue: Stale Cache Data

**Symptoms:**
- Resolved entity doesn't reflect recent merges
- EntityReference returns outdated canonical ID

**Causes and fixes:**
1. Cache TTL too long → Reduce `cacheTtlSeconds`
2. External mutation → Cache doesn't track mutations outside the library; disable caching or reduce TTL
3. Multi-instance deployment → Each instance has its own cache; consider shorter TTLs

## Incident Response

### Severity Levels

| Level | Definition | Response Time | Example |
|-------|-----------|---------------|---------|
| P1 (Critical) | Data corruption, complete outage | Immediate | Merge corrupted entity graph |
| P2 (High) | Feature degraded, data integrity at risk | < 1 hour | Connection pool exhausted |
| P3 (Medium) | Performance degraded | < 4 hours | Slow resolution latency |
| P4 (Low) | Minor issue, workaround available | Next business day | Cache hit rate low |

### P1: Data Corruption

1. **Stop** all write operations (disable auto-merge, stop batch jobs)
2. **Assess** damage: Query the audit trail for recent merge operations
   ```java
   List<AuditEntry> recent = auditService.getRecentEntries(100);
   List<AuditEntry> merges = auditService.getEntriesByAction(AuditAction.ENTITY_MERGED);
   ```
3. **Identify** the problematic merge using the merge ledger
   ```java
   List<MergeRecord> history = resolver.getService().getMergeHistory(entityId);
   ```
4. **Recover**: Merged entities are never deleted (status=MERGED). Manual re-activation is possible by updating the entity status back to ACTIVE in FalkorDB

### P2: Connection Pool Exhausted

1. **Check** pool metrics: `entity.pool.active`, `entity.pool.waiting`
2. **Identify** long-running operations holding connections
3. **Increase** pool size if legitimate load:
   ```java
   PoolConfig config = PoolConfig.builder().maxTotal(30).build();
   ```
4. **Investigate** connection leaks: Ensure all `BatchContext` and `EntityResolver` instances are properly closed

### P3: Slow Resolution

1. **Check** blocking key usage: Ensure `BlockingKeyStrategy` is configured
2. **Review** cache metrics: Low hit rate may indicate cache is too small or TTL too short
3. **Profile** FalkorDB: Check `INFO` command output for memory usage and slow queries
4. **Tune** similarity weights if fuzzy matching is the bottleneck
