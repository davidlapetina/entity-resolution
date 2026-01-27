# ADR-005: Distributed Locking

## Status
Accepted

## Context
Entity resolution operations are not idempotent: resolving the same name concurrently from multiple threads (or instances) could create duplicate entities before either thread detects the other's write. Similarly, concurrent merge operations on the same entity could corrupt the graph.

Critical sections requiring mutual exclusion:
1. **Entity creation**: Check-then-create must be atomic for a given normalized name
2. **Merge operations**: Two concurrent merges involving the same entity must be serialized
3. **Synonym creation**: Duplicate synonyms should not be created

## Decision
We introduced a **`DistributedLock`** interface with two implementations:

1. **`NoOpDistributedLock`** (default): No locking; suitable for single-instance deployments where the JVM's `ConcurrentHashMap` provides sufficient concurrency control
2. **Pluggable implementations**: Clients can provide Redis-based, ZooKeeper-based, or other distributed lock implementations

### Lock Granularity
- Lock key: `entity-resolution:<normalizedName>:<entityType>` for resolution operations
- Lock key: `entity-resolution:merge:<entityId>` for merge operations
- Lock timeout: Configurable via `ResolutionOptions.lockTimeoutMs` (default 5000ms)

### Lock Protocol
```
1. Acquire lock (with timeout)
2. Perform check (query for existing entity)
3. Perform action (create/merge if needed)
4. Release lock (in finally block)
```

## Consequences

### Positive
- Prevents duplicate entity creation under concurrent load
- Prevents merge conflicts and corrupted merge chains
- Pluggable: no distributed lock dependency for single-instance deployments
- Lock granularity is per-entity, not global (high concurrency for different entities)

### Negative
- Lock acquisition adds latency (~1-5ms for distributed locks)
- Lock contention under high write rates for the same entity
- Distributed lock implementations require external infrastructure (Redis, ZooKeeper)
- Deadlock possible if lock timeout is too long and operations hang

## Alternatives Considered
- **Optimistic concurrency control**: Detect conflicts after the fact and retry; simpler but requires idempotent operations
- **Database-level locks**: FalkorDB does not support row-level locks
- **Single-writer pattern**: Route all writes through a single thread; eliminates concurrency issues but limits throughput
