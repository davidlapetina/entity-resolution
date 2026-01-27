# ADR-003: Caching Strategy

## Status
Accepted

## Context
Entity resolution involves repeated lookups against the graph database for:
- Normalized name lookups (exact match candidates)
- Synonym lookups
- Entity-by-ID fetches (for EntityReference resolution)

In read-heavy workloads (e.g., an API receiving duplicate queries), the same entities are looked up repeatedly. Without caching, each resolution requires 2-4 graph queries.

## Decision
We introduced an optional **`ResolutionCache`** interface with a Caffeine-backed implementation:

- **Cache key**: Composite of normalized name + entity type
- **Cache value**: `EntityResolutionResult` (including entity, synonyms, decision)
- **Eviction**: Time-based TTL (default 300s) + size-based (default 10,000 entries)
- **Invalidation**: On merge, both source and target entity cache entries are evicted
- **Configuration**: Opt-in via `ResolutionOptions.cachingEnabled(true)`

### Cache Scope
The cache is scoped to the `EntityResolutionService` instance. It does not cache across multiple resolver instances.

### What Is Cached
- Resolved entity results (successful matches)
- Entity-by-ID lookups

### What Is NOT Cached
- Fuzzy match computations (too variable to cache effectively)
- Relationship queries (low hit rate due to diverse query patterns)
- Audit entries (append-only, no benefit from caching)

## Consequences

### Positive
- 10-100x latency reduction for repeated lookups of the same entity
- Configurable TTL and size limits prevent unbounded memory growth
- Automatic invalidation on merge prevents stale data
- Optional dependency: works without Caffeine on the classpath (falls back to no-op)

### Negative
- Memory overhead proportional to cache size (~1KB per cached entry)
- Stale data possible within the TTL window if external mutations occur
- Cache invalidation on merge adds ~0.1ms to merge operations
- Not distributed: in multi-instance deployments, each instance has its own cache

## Alternatives Considered
- **Redis-backed cache**: Better for multi-instance but adds infrastructure dependency
- **Graph-level query caching**: FalkorDB has its own query cache, but it's opaque and not entity-aware
- **LRU without TTL**: Simpler but risks serving indefinitely stale data
