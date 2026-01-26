# Entity Resolution - Enterprise Readiness Tasks

## Phase 1: Critical Fixes

### 1.1 Security - SQL Injection Prevention

**Current Problem:** `FalkorDBConnection.formatValue()` (line 119-134) uses manual string escaping with basic quote replacement (`replace("'", "\\'")`). This is vulnerable to Unicode escaping bypass, null byte injection, and sophisticated attack vectors.

**Affected Files:**
- `src/main/java/com/entity/resolution/graph/FalkorDBConnection.java:119-134`

- [DONE] **Create `InputSanitizer` class with validation for entity names**
  - Location: `src/main/java/com/entity/resolution/graph/InputSanitizer.java`
  - Implemented max length validation (1000 characters for names, 4000 for Cypher values)
  - Rejects null/blank, control characters
  - Also validates relationship types (alphanumeric + underscore only)
  - Example:
    ```java
    public class InputSanitizer {
        private static final int MAX_LENGTH = 500;
        private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{N}\\s.,&'-]+$");

        public String sanitize(String input) {
            if (input == null || input.length() > MAX_LENGTH) {
                throw new InvalidInputException("Input exceeds maximum length");
            }
            if (!ALLOWED.matcher(input).matches()) {
                throw new InvalidInputException("Input contains invalid characters");
            }
            return input.trim();
        }
    }
    ```

- [DONE] **Harden `FalkorDBConnection.formatValue()` with length checks and type safety**
  - Added `InputSanitizer.sanitizeForCypher()` length check (max 4000 chars)
  - Added `List` support for Cypher `IN` queries (blocking keys)
  - Reject `Collection`/`Map` types (other than List) to prevent injection
  - Example:
    ```java
    public class CypherQueryBuilder {
        private final StringBuilder query = new StringBuilder();
        private final Map<String, Object> params = new HashMap<>();

        public CypherQueryBuilder match(String pattern) { ... }
        public CypherQueryBuilder where(String field, String paramName, Object value) {
            params.put(paramName, value);
            query.append(" WHERE ").append(field).append(" = $").append(paramName);
            return this;
        }
        public QueryResult execute(Graph graph) {
            return graph.query(query.toString(), params);
        }
    }
    ```

- [DONE] **Add unit tests for input validation**
  - Location: `src/test/java/com/entity/resolution/graph/InputSanitizerTest.java`
  - 13 tests covering: null/blank, max length, control characters, valid names, relationship type validation, Cypher sanitization

---

### 1.2 Transaction Management

**Current Problem:** `MergeEngine.merge()` (lines 100-162) performs 6 independent operations that each auto-commit. If step 3 fails (relationship migration), steps 1-2 are already persisted, leaving inconsistent state (orphaned synonyms, partial merge records).

**Current Flow (No Transactions):**
```
1. createSynonymForSourceName() - COMMITTED
2. createDuplicateRecord() - COMMITTED
3. migrateLibraryRelationships() - COMMITTED (can fail here!)
4. entityRepository.recordMerge() - COMMITTED
5. mergeLedger.recordMerge() - COMMITTED (in-memory)
6. auditService.record() - COMMITTED (in-memory)
```

**Affected Files:**
- `src/main/java/com/entity/resolution/merge/MergeEngine.java:91-162`
- `src/main/java/com/entity/resolution/graph/FalkorDBConnection.java`

- [DONE] **Create `MergeTransaction` class implementing compensating transaction pattern**
  - Location: `src/main/java/com/entity/resolution/merge/MergeTransaction.java`
  - AutoCloseable with `execute(description, operation, compensation)` and `executeNoCompensation()`
  - Compensations run in reverse order on failure; `markSuccess()` prevents compensations on close

- [DONE] **Add compensating CypherExecutor methods for rollback**
  - Added `deleteSynonym()`, `deleteDuplicateEntity()`, `revertMerge()` to CypherExecutor
  - These are used as compensation actions during merge rollback

- [DONE] **Refactor `MergeEngine.merge()` to use `MergeTransaction` (atomic merge)**
  - All 6 merge steps wrapped in MergeTransaction with compensating actions
  - On failure, completed steps are reversed in order
  - Example:
    ```java
    public MergeResult merge(Entity source, Entity target, String reason) {
        return transactionTemplate.execute(tx -> {
            try {
                synonymRepository.createSynonymForSourceName(source, target);
                duplicateRepository.createDuplicateRecord(source, target);
                relationshipRepository.migrateRelationships(source, target);
                entityRepository.recordMerge(source, target);
                mergeLedger.recordMerge(source, target, reason);
                auditService.record(ENTITY_MERGED, target.getId(), ...);
                return MergeResult.success(...);
            } catch (Exception e) {
                tx.setRollbackOnly();
                throw new MergeException("Merge failed, rolling back", e);
            }
        });
    }
    ```

- [DONE] **Compensating transaction logic built into `MergeTransaction`**
  - Uses `Deque<CompensatingAction>` stack with reverse-order execution
  - Each step registers its compensation; on failure all compensations run
  - `markSuccess()` prevents compensations on close

- [DONE] **Add tests for transaction rollback scenarios**
  - Location: `src/test/java/com/entity/resolution/merge/MergeTransactionTest.java`
  - 11 tests: successful tx, failed operation rollback, close-without-success, no-compensation steps,
    compensation failure continues remaining, execute after close, empty transaction

---

### 1.3 Query Optimization - Blocking Key Indexing

**Current Problem:** `EntityResolutionService.findBestFuzzyMatch()` (lines 226-270) loads ALL active entities into memory, then computes similarity in a Java loop. For 100K entities, this means one large query + 100K object allocations + 100K similarity computations = ~1 second per resolution.

**Current Flow (O(n)):**
```java
// Line 226-270 in EntityResolutionService
List<Entity> all = executor.findAllActiveEntities(type);  // Loads 100K entities!
for (Entity e : all) {
    double score = scorer.compute(normalized, e.getNormalizedName());  // 100K computations!
}
```

**Solution:** Blocking keys reduce candidates from O(n) to O(candidates) where candidates << n (typically <100).

**Affected Files:**
- `src/main/java/com/entity/resolution/api/EntityResolutionService.java:226-270`
- `src/main/java/com/entity/resolution/graph/CypherExecutor.java:63-71`
- `src/main/java/com/entity/resolution/graph/EntityRepository.java`

- [DONE] **Create `BlockingKeyStrategy` interface and `DefaultBlockingKeyStrategy` implementation**
  - Location: `src/main/java/com/entity/resolution/similarity/BlockingKeyStrategy.java`
  - Location: `src/main/java/com/entity/resolution/similarity/DefaultBlockingKeyStrategy.java`
  - Three complementary key types: prefix (`pfx:mic`), sorted token (`tok:corp|microsoft`), bigram (`bg:mi`)
  - Pluggable via `EntityResolver.Builder.blockingKeyStrategy()`

- [DONE] **Add `BlockingKey` node type and `HAS_BLOCKING_KEY` relationship to graph schema**
  - CypherExecutor: `createBlockingKeys()` uses MERGE for BlockingKey nodes + HAS_BLOCKING_KEY edges
  - CypherExecutor: `findCandidatesByBlockingKeys()` queries candidates sharing any key
  - EntityRepository: `save(entity, blockingKeys)` overload, `findCandidatesByBlockingKeys()`

- [DONE] **Create indexes on `BlockingKey.value` in `FalkorDBConnection.createIndexes()`**
  - Added `CREATE INDEX FOR (bk:BlockingKey) ON (bk.value)`
  - Also added AuditEntry indexes (id, entityId, action, timestamp)

- [DONE] **Refactor `EntityResolutionService.findBestFuzzyMatch()` to query by blocking keys**
  - Generates blocking keys, queries candidates via graph, falls back to full scan if no candidates
  - New flow:
    ```java
    // Generate blocking keys for input
    Set<String> blockingKeys = blockingKeyGenerator.generateKeys(normalizedName);

    // Query only entities sharing blocking keys (typically <100 vs 100K)
    String query = """
        MATCH (e:Entity)-[:HAS_BLOCKING_KEY]->(b:BlockingKey)
        WHERE b.value IN $blockingKeys AND e.type = $type AND e.status = 'ACTIVE'
        RETURN DISTINCT e
        """;
    List<Entity> candidates = executor.query(query, Map.of("blockingKeys", blockingKeys, "type", type));

    // Compute similarity only on candidates
    for (Entity candidate : candidates) {
        double score = scorer.compute(normalizedName, candidate.getNormalizedName());
    }
    ```

- [DONE] **Add blocking key generation on entity creation in `EntityRepository`**
  - Updated `EntityResolutionService.createEntityNode()` to generate and persist blocking keys
  - Added `EntityRepository.save(entity, blockingKeys)` overload
  - Blocking keys generated via `DefaultBlockingKeyStrategy` and persisted with entity

- [DONE] **Add performance benchmark tests comparing old vs new fuzzy match**
  - Location: `src/test/java/com/entity/resolution/benchmark/FuzzyMatchBenchmarkTest.java`
  - 8 tests validating correctness (same best match) and measuring candidate reduction
  - Tests at 100, 1K, 5K, and 10K entity scales
  - Verifies >50% candidate reduction with blocking keys

---

### 1.4 Memory Management

**Current Problem:** `BatchContext` uses unbounded `ConcurrentHashMap` (line 42) and `synchronizedList` (line 43). Entities are never evicted until commit. For large batches (10M entities), this causes OOM.

**Affected Files:**
- `src/main/java/com/entity/resolution/api/BatchContext.java:42-43, 85-139`

- [DONE] **Add `maxBatchSize` configuration to `BatchContext` with default limit (10,000)**
  - Added `maxBatchSize` (default 10,000) and `batchCommitChunkSize` (default 1,000) to `ResolutionOptions`
  - `BatchContext` reads limits from `ResolutionOptions`

- [DONE] **Implement batch size validation in `BatchContext.resolve()` throwing `IllegalStateException`**
  - Checks `resolvedEntities.size() >= maxBatchSize` before adding new entries
  - Duplicates bypass the size check (already resolved entities don't count)
  - Throws `IllegalStateException` with descriptive message including current size and limit

- [DONE] **Add memory guard in `BatchContext` to track estimated heap usage**
  - Added `ESTIMATED_BYTES_PER_ENTITY = 500L` constant
  - Added `maxBatchMemoryBytes` (default 100MB) to `ResolutionOptions` with builder method
  - `checkMemory()` called after each new entity resolution; warns at 80% threshold
  - Warning issued only once per batch via `memoryWarningIssued` flag
  - Added `getEstimatedMemoryUsage()` public method for monitoring

- [DONE] **Implement chunked commit in `BatchContext.commit()` for large relationship batches**
  - Processes relationships in chunks of `batchCommitChunkSize` (default 1,000)
  - Logs chunk progress when total exceeds chunk size
  - Logs batch summary on commit

---

### 1.5 Audit Trail Persistence

**Current Problem:** `AuditService` (line 24) stores entries in `CopyOnWriteArrayList` in-memory only. All audit data is lost on JVM shutdown. This violates compliance requirements (GDPR, SOX, HIPAA).

**Affected Files:**
- `src/main/java/com/entity/resolution/audit/AuditService.java:24, 30-48`

- [DONE] **Create `AuditRepository` interface for persistent audit storage**
  - Location: `src/main/java/com/entity/resolution/audit/AuditRepository.java`
  - Methods: save, findAll, findByEntityId, findByAction, findByActorId, findBetween, count, findRecent

- [DONE] **Implement `GraphAuditRepository` storing audit entries as nodes in FalkorDB**
  - Location: `src/main/java/com/entity/resolution/audit/GraphAuditRepository.java`
  - Persists AuditEntry as `:AuditEntry` nodes with JSON-serialized details
  - Creates indexes on id, entityId, action, timestamp in constructor
  - Tests use `StubGraphConnection` (not Mockito, for Java 25 compatibility)

- [DONE] **Create `AuditEntry` node schema with indexes on `entityId`, `timestamp`, `action`**
  - Added to `FalkorDBConnection.createIndexes()`: indexes on id, entityId, action, timestamp
  - Also added `GraphAuditRepository` constructor creates same indexes for standalone use

- [DONE] **Refactor `AuditService` to use `AuditRepository` instead of in-memory list**
  - Delegates all persistence to `AuditRepository`
  - No-arg constructor creates `InMemoryAuditRepository` for backward compatibility
  - New constructor accepts `AuditRepository` for custom persistence (e.g., `GraphAuditRepository`)
  - All existing tests pass unchanged

- [DONE] **Add `getAuditTrail(entityId, fromDate, toDate)` pagination support**
  - Added `findByEntityIdBetween(entityId, start, end)` to `AuditRepository` interface
  - Implemented in both `InMemoryAuditRepository` and `GraphAuditRepository`
  - Added `getAuditTrail(entityId, from, to)` to `AuditService`
  - Supports entity-scoped time-range queries for compliance reporting

- [DONE] **Add integration tests for audit persistence and retrieval**
  - Location: `src/test/java/com/entity/resolution/audit/AuditPersistenceTest.java`
  - 11 tests covering: full audit flow, time-range queries, entity-scoped audit trail,
    concurrent writes (10 threads x 100 entries), details preservation, default constructor,
    findByEntityIdBetween filtering, empty results
  - Also added `findByEntityIdBetween` test to `GraphAuditRepositoryTest`

---

## Phase 2: Scalability

### 2.1 Connection Pooling

**Current Problem:** `FalkorDBConnection` creates a single connection instance reused for all requests. This causes thread contention under concurrent load and provides no failover capability.

**Affected Files:**
- `src/main/java/com/entity/resolution/graph/FalkorDBConnection.java`

- [DONE] **Create `GraphConnectionPool` interface with `borrow()`, `release()` methods**
  - Location: `src/main/java/com/entity/resolution/graph/GraphConnectionPool.java`
  - AutoCloseable interface with `borrow()`, `release()`, `getStats()`, `close()`

- [DONE] **Implement `SimpleGraphConnectionPool` using `Semaphore` + `ConcurrentLinkedDeque`**
  - Location: `src/main/java/com/entity/resolution/graph/SimpleGraphConnectionPool.java`
  - Custom pool using `java.util.concurrent` (no external dependency)
  - `Semaphore` for borrow flow control, `ConcurrentLinkedDeque` for idle connections
  - Pre-creates `minIdle` connections on startup
  - Validates connections on borrow when `testOnBorrow` is enabled

- [DONE] **Create `PoolConfig` builder with `maxTotal`, `maxIdle`, `minIdle`, `maxWaitMillis`**
  - Location: `src/main/java/com/entity/resolution/graph/PoolConfig.java`
  - Defaults: maxTotal=20, maxIdle=10, minIdle=2, maxWaitMillis=5000, testOnBorrow=true
  - Validation: maxIdle <= maxTotal, minIdle <= maxIdle

- [DONE] **Create `PooledFalkorDBConnection` that borrows/releases per operation**
  - Location: `src/main/java/com/entity/resolution/graph/PooledFalkorDBConnection.java`
  - Transparent to `CypherExecutor` — borrows on each execute/query, releases in finally block
  - Ensures connections are returned even on exceptions

- [DONE] **Create `PoolStats` record for pool metrics**
  - Location: `src/main/java/com/entity/resolution/graph/PoolStats.java`
  - Tracks: totalConnections, activeConnections, idleConnections, totalBorrowed, totalReleased, totalCreated

- [DONE] **Integrate pool into `EntityResolver.Builder`**
  - Added `falkorDBPool(PoolConfig)` to create owned pool
  - Added `connectionPool(pool, graphName)` to use existing pool
  - Pool closed automatically when resolver is closed (if owned)

- [DONE] **Add unit tests for connection pooling**
  - Location: `src/test/java/com/entity/resolution/graph/ConnectionPoolTest.java`
  - 10 tests covering: config defaults, config validation, borrow/release behavior,
    release on exception, graph name delegation, stats tracking, custom settings

---

### 2.2 Async Resolution API

**Current Problem:** All resolution operations are synchronous, blocking the calling thread. This limits throughput for I/O-bound operations (graph queries, LLM calls).

**Affected Files:**
- `src/main/java/com/entity/resolution/api/EntityResolver.java`
- `src/main/java/com/entity/resolution/api/EntityResolutionService.java`

- [DONE] **Create `AsyncEntityResolver` interface with `CompletableFuture<EntityResolutionResult>` methods**
  - Location: `src/main/java/com/entity/resolution/api/AsyncEntityResolver.java`
  - AutoCloseable interface with `resolveAsync()`, `resolveBatchAsync()`, and bounded-concurrency overload
  - Mirrors sync API with async equivalents returning `CompletableFuture`

- [DONE] **Implement `AsyncEntityResolverImpl` with Java 21 virtual threads**
  - Location: `src/main/java/com/entity/resolution/api/AsyncEntityResolverImpl.java`
  - Uses `Executors.newVirtualThreadPerTaskExecutor()` for lightweight async execution
  - `CompletableFuture.supplyAsync(...).orTimeout(timeoutMs, MILLISECONDS)` for each resolution
  - Bounded-concurrency batch via `Semaphore` for backpressure control
  - Graceful shutdown with `awaitTermination(5s)` then `shutdownNow()`

- [DONE] **Create `ResolutionRequest` record for batch operations**
  - Location: `src/main/java/com/entity/resolution/api/ResolutionRequest.java`
  - Record: `(entityName, entityType, options)` with `of()` factory methods
  - Supports optional per-request `ResolutionOptions` override

- [DONE] **Add `resolveBatchAsync(List<ResolutionRequest>)` for parallel batch resolution**
  - Resolves all requests in parallel using `CompletableFuture.allOf()`
  - Collects results maintaining order via `stream().map(join).toList()`
  - Bounded overload `resolveBatchAsync(requests, maxConcurrency)` uses `Semaphore`

- [DONE] **Add timeout configuration for async operations**
  - Added `asyncTimeoutMs` (default 30,000ms) to `ResolutionOptions`
  - Applied to all `CompletableFuture` via `orTimeout()` method
  - Configurable via `ResolutionOptions.builder().asyncTimeoutMs(ms)`

- [DONE] **Add `async()` factory method to `EntityResolver`**
  - `resolver.async()` returns `AsyncEntityResolverImpl` wrapping the sync resolver
  - Uses default `ResolutionOptions` from the resolver

- [DONE] **Add unit tests for async resolution**
  - Location: `src/test/java/com/entity/resolution/api/AsyncEntityResolverTest.java`
  - 9 tests covering: factory creation, single async resolve, resolve with options,
    batch async, bounded-concurrency batch, invalid max concurrency validation,
    ResolutionRequest defaults/options, graceful close
  - Uses `MockGraphConnection` inner class for testing without FalkorDB

---

### 2.3 Distributed Locking

**Current Problem:** Multiple JVM instances can create duplicate entities simultaneously. `EntityResolutionService` creates entity before checking for exact match (line 293), causing race conditions.

**Affected Files:**
- `src/main/java/com/entity/resolution/api/EntityResolutionService.java`

- [DONE] **Create `DistributedLock` interface with `tryLock()`, `unlock()` methods**
  - Location: `src/main/java/com/entity/resolution/lock/DistributedLock.java`
  - Simple interface: `boolean tryLock(String key)`, `void unlock(String key)`

- [DONE] **Create `LockConfig` record for lock configuration**
  - Location: `src/main/java/com/entity/resolution/lock/LockConfig.java`
  - Record: `(timeoutMs, maxRetries, retryDelayMs, lockTtlSeconds)`
  - Defaults: timeout=5000ms, maxRetries=3, retryDelay=100ms, TTL=30s
  - Validates timeout > 0, retries >= 0

- [DONE] **Implement `LocalDistributedLock` for single-JVM locking**
  - Location: `src/main/java/com/entity/resolution/lock/LocalDistributedLock.java`
  - Uses `ConcurrentHashMap<String, ReentrantLock>` for per-key locks
  - Configurable timeout via `LockConfig`
  - Re-entrant: same thread can acquire same key multiple times
  - Graceful handling of unlock for non-existent keys

- [DONE] **Implement `GraphDistributedLock` using FalkorDB advisory locks (multi-JVM)**
  - Location: `src/main/java/com/entity/resolution/lock/GraphDistributedLock.java`
  - Uses FalkorDB `MERGE` with `ON CREATE`/`ON MATCH` for atomic lock acquisition
  - TTL-based expiration: expired locks are automatically reclaimed
  - Retry loop with configurable attempts and delay
  - UUID-based holder identification for lock ownership

- [DONE] **Implement `NoOpDistributedLock` for lock-disabled scenarios**
  - Location: `src/main/java/com/entity/resolution/lock/NoOpDistributedLock.java`
  - Always returns true for `tryLock()`, no-op for `unlock()`

- [DONE] **Create `LockAcquisitionException` for lock timeout failures**
  - Location: `src/main/java/com/entity/resolution/lock/LockAcquisitionException.java`
  - RuntimeException with message and optional cause

- [DONE] **Add locking around entity creation in `EntityResolutionService.resolve()`**
  - Double-check pattern: cache check → lock → re-check cache → resolve → populate cache → unlock
  - Lock key format: `{entityType}:{normalizedName}` (e.g., `COMPANY:acmecorp`)
  - Lock acquired after cache miss, released in finally block
  - Extracted `resolveInternal()` for the actual resolution logic

- [DONE] **Add lock timeout configuration to `ResolutionOptions`**
  - Added `lockTimeoutMs` (default 5,000ms) to `ResolutionOptions`
  - Configurable via `ResolutionOptions.builder().lockTimeoutMs(ms)`

- [DONE] **Integrate lock into `EntityResolver.Builder`**
  - Added `distributedLock(DistributedLock)` builder method
  - Defaults to `NoOpDistributedLock` when not explicitly configured

- [DONE] **Add unit tests for distributed locking**
  - Location: `src/test/java/com/entity/resolution/lock/DistributedLockTest.java`
  - 12 tests in nested classes: NoOpTests (2 - always acquires, unlock no-op),
    LocalLockTests (5 - acquire/release, re-entrant, different keys, concurrent blocking with 5 threads, non-existent key unlock),
    LockConfigTests (3 - defaults, invalid timeout, negative retries),
    ExceptionTests (2 - message, cause)

---

### 2.4 Query Result Caching

**Current Problem:** Every resolution performs graph queries even for recently resolved entities. Repeated lookups for common entities (e.g., "Microsoft") waste resources.

**Affected Files:**
- `src/main/java/com/entity/resolution/api/EntityResolutionService.java`

- [DONE] **Create `ResolutionCache` interface for caching resolution results**
  - Location: `src/main/java/com/entity/resolution/cache/ResolutionCache.java`
  - Methods: `get(normalizedName, entityType)`, `put(normalizedName, entityType, result)`, `invalidate(entityId)`, `invalidateAll()`, `getStats()`
  - Also created `NoOpResolutionCache` for when caching is disabled
  - Also created `CacheStats` record (hitCount, missCount, evictionCount, size) with `hitRate()` method

- [DONE] **Implement `CaffeineResolutionCache` with TTL and max size configuration**
  - Location: `src/main/java/com/entity/resolution/cache/CaffeineResolutionCache.java`
  - Uses Caffeine 3.1.8 (optional dependency) with `maximumSize()`, `expireAfterWrite()`, `recordStats()`
  - Implements `ResolutionCache` and `MergeListener` for merge-triggered invalidation
  - Uses `CacheKey` record (normalizedName, entityType) and secondary `entityIndex` for entity-based invalidation
  - Also created `CacheConfig` record (maxSize, ttlSeconds, enabled) with defaults: 10,000 entries, 300s TTL

- [DONE] **Add cache key generation based on normalized name and entity type**
  - Uses `CacheKey` record with `(normalizedName, entityType)` composite key
  - Consistent key generation across lookups via record equals/hashCode

- [DONE] **Add cache invalidation on entity merge events**
  - Created `MergeListener` interface in cache package with `onMerge(sourceEntityId, targetEntityId)` callback
  - `CaffeineResolutionCache` implements `MergeListener`, invalidating both source and target on merge
  - Secondary `entityIndex` (`ConcurrentMap<String, Set<CacheKey>>`) enables efficient entity-based invalidation
  - Removal listener cleans up secondary index on eviction

- [DONE] **Add cache hit/miss metrics**
  - `CacheStats` record exposes hitCount, missCount, evictionCount, size, and `hitRate()` method
  - Caffeine `recordStats()` enabled; `getStats()` converts Caffeine stats to library `CacheStats`
  - 13 tests in `ResolutionCacheTest.java` covering NoOp, Caffeine, config, and stats behavior

- [DONE] **Add configuration to enable/disable caching in `ResolutionOptions`**
  - Added `cachingEnabled` (default false), `cacheMaxSize` (default 10,000), `cacheTtlSeconds` (default 300)
  - Builder methods with validation (maxSize > 0, ttlSeconds > 0)
  - `EntityResolutionService` integrates with `NoOpResolutionCache` when caching disabled
  - Double-check locking pattern with `DistributedLock` prevents cache stampede

---

### 2.5 Pagination Support

**Current Problem:** Repository methods return unbounded lists. Large result sets (100K+ relationships, audit entries) can cause OOM.

**Affected Files:**
- `src/main/java/com/entity/resolution/graph/EntityRepository.java`
- `src/main/java/com/entity/resolution/graph/RelationshipRepository.java`
- `src/main/java/com/entity/resolution/audit/AuditService.java`

- [DONE] **Create `PageRequest` class with `offset`, `limit`, `sortBy`, `sortDirection`**
  - Location: `src/main/java/com/entity/resolution/api/PageRequest.java`
  - Record with fields: offset, limit, sortBy, sortDirection (nested `SortDirection` enum: ASC, DESC)
  - Compact constructor validates offset >= 0, 0 < limit <= 10,000
  - Factory methods: `of(page, size)`, `first(size)`, `of(page, size, sortBy, direction)`
  - `pageNumber()` method calculates page number from offset and limit

- [DONE] **Create `Page<T>` class with `content`, `totalElements`, `hasNext`, `hasPrevious`**
  - Location: `src/main/java/com/entity/resolution/api/Page.java`
  - Generic record with fields: content (defensive copy), totalElements, pageNumber, pageSize
  - Methods: `hasNext()`, `hasPrevious()`, `totalPages()`, `numberOfElements()`, `hasContent()`
  - `static <T> Page<T> empty(PageRequest request)` factory for empty results
  - Validates totalElements >= 0 in compact constructor

- [DONE] **Add paginated methods to `EntityRepository`: `findAllActive(EntityType, PageRequest)`**
  - Uses `CypherExecutor.countActiveEntities()` for total count
  - Uses `CypherExecutor.findAllActiveEntitiesPaginated()` with `SKIP $offset LIMIT $limit`
  - Orders by `e.canonicalName ASC`
  - Returns `Page<Entity>` with correct pagination metadata

- [DONE] **Add cursor-based pagination for `getAuditTrail()` using timestamp cursor**
  - Location: `src/main/java/com/entity/resolution/api/CursorPage.java`
  - `CursorPage<T>` record with content (defensive copy), nextCursor (ISO-8601 timestamp), hasMore
  - `AuditService.getAuditTrailPaginated(entityId, cursor, limit)` delegates to `AuditRepository.findByEntityIdAfter()`
  - Default implementation in `AuditRepository` interface; overridden in both `InMemoryAuditRepository` and `GraphAuditRepository`
  - Fetches `limit + 1` to detect hasMore; next cursor set to last entry's timestamp
  - `CypherExecutor.findAuditEntriesByEntityAfterCursor()` supports both with-cursor and without-cursor paths

- [DONE] **Add pagination to `getRelationships()` methods in `RelationshipRepository`**
  - `findBySourceEntity(entityId, PageRequest)` - paginated outgoing relationships
  - `findByTargetEntity(entityId, PageRequest)` - paginated incoming relationships
  - `findByEntity(entityId, PageRequest)` - paginated all relationships (source or target)
  - Each uses corresponding `CypherExecutor` count and paginated query methods with `SKIP`/`LIMIT`
  - Orders by `r.createdAt ASC`
  - Location: `src/test/java/com/entity/resolution/api/PaginationTest.java` - 14 tests covering PageRequest, Page, CursorPage, and audit cursor pagination

---

## Phase 3: Observability

### 3.1 Metrics Integration

**Current Problem:** No visibility into system behavior. Cannot measure resolution latency, entity creation rate, or identify performance bottlenecks.

- [DONE] **Add Micrometer dependency to `pom.xml` (optional)**
  - Add as optional dependency for applications that want metrics
  - Example:
    ```xml
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
        <version>1.12.2</version>
        <optional>true</optional>
    </dependency>
    ```

- [DONE] **Create `MetricsService` interface for recording metrics**
  - Location: `src/main/java/com/entity/resolution/metrics/MetricsService.java`
  - Example:
    ```java
    public interface MetricsService {
        void recordResolutionDuration(EntityType type, MatchDecision decision, Duration duration);
        void incrementEntityCreated(EntityType type);
        void incrementEntityMerged(EntityType type);
        void incrementSynonymMatched(EntityType type);
        void recordSimilarityScore(double score);
        void recordBatchSize(int size);
        void recordCacheHit();
        void recordCacheMiss();
    }
    ```

- [DONE] **Implement `MicrometerMetricsService` with standard metric types**
  - Location: `src/main/java/com/entity/resolution/metrics/MicrometerMetricsService.java`
  - Example:
    ```java
    public class MicrometerMetricsService implements MetricsService {
        private final MeterRegistry registry;

        @Override
        public void recordResolutionDuration(EntityType type, MatchDecision decision, Duration duration) {
            Timer.builder("entity.resolution.duration")
                .tag("entityType", type.name())
                .tag("decision", decision.name())
                .register(registry)
                .record(duration);
        }
    }
    ```

- [DONE] **Add timer metric: `entity.resolution.duration` (with tags: entityType, decision)**
  - Measure end-to-end resolution time
  - Tags: entityType (COMPANY, PERSON), decision (AUTO_MERGE, SYNONYM_ONLY, NO_MATCH)

- [DONE] **Add counter metrics: `entity.created`, `entity.merged`, `entity.matched.synonym`**
  - Track entity lifecycle events
  - Enable alerting on unusual patterns (e.g., spike in merges)

- [DONE] **Add gauge metrics: `batch.size`, `connection.pool.active`, `cache.size`**
  - Monitor resource utilization
  - Alert on pool exhaustion or cache overflow

- [DONE] **Add histogram metric: `similarity.score` for match score distribution**
  - Track distribution of similarity scores
  - Identify tuning opportunities for thresholds

- [DONE] **Implement `NoOpMetricsService` for when metrics are disabled**
  - Location: `src/main/java/com/entity/resolution/metrics/NoOpMetricsService.java`
  - Empty implementations for all methods
  - Default when Micrometer not on classpath

---

### 3.2 Health Checks

**Current Problem:** No way to detect FalkorDB failures until first query fails. Load balancers and orchestrators cannot assess service health.

- [DONE] **Create `HealthCheck` interface with `check()` returning `HealthStatus`**
  - Location: `src/main/java/com/entity/resolution/health/HealthCheck.java`
  - Example:
    ```java
    public interface HealthCheck {
        String getName();
        HealthStatus check();
    }

    public record HealthStatus(Status status, String message, Map<String, Object> details) {
        public enum Status { UP, DOWN, DEGRADED }

        public static HealthStatus up() { return new HealthStatus(Status.UP, "OK", Map.of()); }
        public static HealthStatus down(String reason) { return new HealthStatus(Status.DOWN, reason, Map.of()); }
    }
    ```

- [DONE] **Implement `FalkorDBHealthCheck` verifying graph connectivity**
  - Location: `src/main/java/com/entity/resolution/health/FalkorDBHealthCheck.java`
  - Execute simple query with timeout
  - Example:
    ```java
    public class FalkorDBHealthCheck implements HealthCheck {
        @Override
        public HealthStatus check() {
            try {
                long start = System.currentTimeMillis();
                connection.query("RETURN 1");
                long latency = System.currentTimeMillis() - start;
                return HealthStatus.up().withDetail("latencyMs", latency);
            } catch (Exception e) {
                return HealthStatus.down("FalkorDB connection failed: " + e.getMessage());
            }
        }
    }
    ```

- [DONE] **Implement `MemoryHealthCheck` monitoring heap usage**
  - Location: `src/main/java/com/entity/resolution/health/MemoryHealthCheck.java`
  - Warn at 80% heap usage, fail at 95%
  - Example:
    ```java
    public class MemoryHealthCheck implements HealthCheck {
        @Override
        public HealthStatus check() {
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memory.getHeapMemoryUsage();
            double usagePercent = (double) heap.getUsed() / heap.getMax() * 100;

            if (usagePercent > 95) return HealthStatus.down("Heap exhausted: " + usagePercent + "%");
            if (usagePercent > 80) return HealthStatus.degraded("Heap warning: " + usagePercent + "%");
            return HealthStatus.up().withDetail("heapUsagePercent", usagePercent);
        }
    }
    ```

- [DONE] **Create `HealthCheckRegistry` aggregating all health checks**
  - Location: `src/main/java/com/entity/resolution/health/HealthCheckRegistry.java`
  - Aggregate status: UP only if all checks UP, DOWN if any DOWN
  - Example:
    ```java
    public class HealthCheckRegistry {
        private final List<HealthCheck> checks = new ArrayList<>();

        public HealthStatus checkAll() {
            Map<String, HealthStatus> results = checks.stream()
                .collect(Collectors.toMap(HealthCheck::getName, HealthCheck::check));

            boolean anyDown = results.values().stream().anyMatch(s -> s.status() == Status.DOWN);
            boolean anyDegraded = results.values().stream().anyMatch(s -> s.status() == Status.DEGRADED);

            Status overall = anyDown ? Status.DOWN : anyDegraded ? Status.DEGRADED : Status.UP;
            return new HealthStatus(overall, "", Map.of("checks", results));
        }
    }
    ```

- [DONE] **Add `EntityResolver.health()` method returning aggregate health status**
  - Expose health check to library users
  - Example: `HealthStatus status = resolver.health();`

- [DONE] **Add health check for connection pool exhaustion**
  - Check pool stats: warn if >80% active, fail if 100% active and waiting > threshold

---

### 3.3 Distributed Tracing

**Current Problem:** Cannot trace resolution flow across components. Debugging complex resolution paths requires manual log correlation.

- [DONE] **Add OpenTelemetry dependency to `pom.xml` (optional)**
  - Example:
    ```xml
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
        <version>1.34.1</version>
        <optional>true</optional>
    </dependency>
    ```

- [DONE] **Create `TracingService` interface for span management**
  - Location: `src/main/java/com/entity/resolution/tracing/TracingService.java`
  - Example:
    ```java
    public interface TracingService {
        Span startSpan(String operationName);
        void endSpan(Span span);
        void addAttribute(String key, String value);
        void recordException(Span span, Throwable t);
    }

    public interface Span extends AutoCloseable {
        void setAttribute(String key, String value);
        void setStatus(SpanStatus status);
    }
    ```

- [DONE] **Implement `OpenTelemetryTracingService` with span creation/propagation**
  - Location: `src/main/java/com/entity/resolution/tracing/OpenTelemetryTracingService.java`
  - Create spans for major operations
  - Propagate trace context through async operations

- [DONE] **Add tracing spans to: `resolve()`, `merge()`, `findBestFuzzyMatch()`, `createRelationship()`**
  - Wrap key methods with spans
  - Include relevant attributes (entityType, decision, score)
  - Example:
    ```java
    public EntityResolutionResult resolve(String name, EntityType type) {
        Span span = tracingService.startSpan("EntityResolver.resolve");
        try {
            span.setAttribute("entityType", type.name());
            span.setAttribute("inputName", name);
            EntityResolutionResult result = doResolve(name, type);
            span.setAttribute("decision", result.getMatchDecision().name());
            return result;
        } catch (Exception e) {
            tracingService.recordException(span, e);
            throw e;
        } finally {
            tracingService.endSpan(span);
        }
    }
    ```

- [DONE] **Add trace context propagation through `BatchContext`**
  - Store trace context in batch
  - Restore context for each operation in batch

- [DONE] **Implement `NoOpTracingService` for when tracing is disabled**
  - Location: `src/main/java/com/entity/resolution/tracing/NoOpTracingService.java`
  - Return no-op spans
  - Default when OpenTelemetry not on classpath

---

### 3.4 Structured Logging

**Current Problem:** Log statements use unstructured messages. Log aggregation and analysis is difficult.

**Affected Files:**
- All classes using `log.info()`, `log.debug()`, etc.

- [DONE] **Create `LogContext` class for MDC (Mapped Diagnostic Context) management**
  - Location: `src/main/java/com/entity/resolution/logging/LogContext.java`
  - Example:
    ```java
    public class LogContext implements AutoCloseable {
        public static LogContext forResolution(String correlationId, String entityType) {
            LogContext ctx = new LogContext();
            MDC.put("correlationId", correlationId);
            MDC.put("entityType", entityType);
            return ctx;
        }

        @Override
        public void close() {
            MDC.clear();
        }
    }
    ```

- [DONE] **Add correlation ID generation and propagation**
  - Generate UUID for each resolution request
  - Propagate through all nested operations
  - Include in all log statements via MDC

- [DONE] **Add structured log fields: `entityId`, `entityType`, `operation`, `duration`, `decision`**
  - Use key-value pairs in log messages
  - Configure Logback for JSON output (optional)
  - Example:
    ```java
    log.info("Resolution completed entityId={} entityType={} decision={} durationMs={}",
        result.getEntityId(), type, result.getMatchDecision(), duration);
    ```

- [DONE] **Refactor existing log statements to use structured format**
  - Audit all log statements in codebase
  - Convert to structured format with consistent field names

- [DONE] **Add log context to `BatchContext` for batch correlation**
  - Generate batch correlation ID
  - Include batch ID in all operations within batch
  - Example: `batchId={} operation=resolve index=42/1000`

---

## Phase 4: Enterprise Features

### 4.1 REST API Layer

**Purpose:** Enable integration with non-Java systems and provide web-based administration.

- [NOT STARTED] **Add Quarkus REST dependency (optional module)**
  - Create separate Maven module: `entity-resolution-rest`
  - Depend on core library
  - Use Quarkus REST (RESTEasy Reactive)
  - Example:
    ```xml
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-resteasy-reactive</artifactId>
    </dependency>
    ```

- [NOT STARTED] **Create `EntityResolutionResource` with REST endpoints**
  - Location: `entity-resolution-rest/src/main/java/.../resource/EntityResolutionResource.java`
  - Use Jakarta REST (JAX-RS) annotations
  - Use CDI for dependency injection
  - Example:
    ```java
    import jakarta.inject.Inject;
    import jakarta.ws.rs.Consumes;
    import jakarta.ws.rs.POST;
    import jakarta.ws.rs.Path;
    import jakarta.ws.rs.Produces;
    import jakarta.ws.rs.core.MediaType;

    @Path("/api/v1")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public class EntityResolutionResource {

        @Inject
        EntityResolver resolver;

        @POST
        @Path("/entities/resolve")
        public ResolutionResponse resolve(ResolutionRequest request) {
            EntityResolutionResult result =
                    resolver.resolve(request.name(), request.entityType());
            return ResolutionResponse.from(result);
        }
    }
    ```


- [NOT STARTED] **Implement `POST /api/v1/entities/resolve` endpoint**
  - Request: `{ "name": "Acme Corp", "entityType": "COMPANY", "options": {...} }`
  - Response: `{ "entityId": "...", "isNew": true, "confidence": 0.95, ... }`

- [NOT STARTED] **Implement `POST /api/v1/entities/batch` for batch resolution**
  - Accept array of resolution requests
  - Return array of results in same order
  - Support async processing with callback URL

- [NOT STARTED] **Implement `GET /api/v1/entities/{id}` for entity lookup**
  - Return full entity details including synonyms
  - Support `?includeAudit=true` for audit trail

- [NOT STARTED] **Implement `POST /api/v1/relationships` for relationship creation**
  - Request: `{ "sourceId": "...", "targetId": "...", "type": "PARTNER", "properties": {...} }`
  - Validate entity references exist

- [NOT STARTED] **Implement `GET /api/v1/entities/{id}/relationships` for relationship query**
  - Support filtering by relationship type
  - Support pagination

- [NOT STARTED] **Add OpenAPI / Swagger documentation**
  - Use Quarkus SmallRye OpenAPI for automatic documentation
  - Include request/response schemas via Jakarta annotations
  - Example dependency:
    ```xml
    <dependency>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-smallrye-openapi</artifactId>
    </dependency>
    ```


- [NOT STARTED] **Add request validation and error response DTOs**
  - Validate all inputs
  - Return consistent error format: `{ "error": "...", "code": "...", "details": {...} }`

---

### 4.2 Manual Review Queue

**Purpose:** Enable human-in-the-loop for uncertain matches (0.60 <= score < 0.80).

- [NOT STARTED] **Create `ReviewQueue` interface for managing review items**
  - Location: `src/main/java/com/entity/resolution/review/ReviewQueue.java`
  - Example:
    ```java
    public interface ReviewQueue {
        void submit(ReviewItem item);
        Page<ReviewItem> getPending(PageRequest page);
        void approve(String reviewId, String reviewerId, String notes);
        void reject(String reviewId, String reviewerId, String notes);
        ReviewItem get(String reviewId);
    }
    ```

- [NOT STARTED] **Implement `GraphReviewQueue` storing review items as nodes**
  - Location: `src/main/java/com/entity/resolution/review/GraphReviewQueue.java`
  - Schema:
    ```cypher
    (:ReviewItem {
        id: "uuid",
        sourceEntityId: "...",
        candidateEntityId: "...",
        similarityScore: 0.72,
        status: "PENDING",  // PENDING, APPROVED, REJECTED
        submittedAt: datetime(),
        reviewedAt: datetime(),
        reviewerId: "...",
        notes: "..."
    })
    ```

- [NOT STARTED] **Create `ReviewItem` model with required fields**
  - Location: `src/main/java/com/entity/resolution/review/ReviewItem.java`
  - Fields: id, sourceEntity, candidateEntity, score, status, assignee, submittedAt, reviewedAt, reviewerId, notes

- [NOT STARTED] **Add `submitForReview()` method in `EntityResolutionService`**
  - Automatically submit when decision is REVIEW
  - Example:
    ```java
    if (decision == MatchDecision.REVIEW) {
        reviewQueue.submit(ReviewItem.builder()
            .sourceEntityId(newEntity.getId())
            .candidateEntityId(bestMatch.entity().getId())
            .similarityScore(bestMatch.score())
            .build());
    }
    ```

- [NOT STARTED] **Add `approveMatch()`, `rejectMatch()` methods for review decisions**
  - On approve: trigger merge of source into candidate
  - On reject: mark as no match, optionally create "NOT_SAME_AS" relationship

- [NOT STARTED] **Add `getReviewQueue(PageRequest)` for listing pending reviews**
  - Support filtering by entityType, assignee, score range
  - Support sorting by submittedAt, score

- [NOT STARTED] **Add review decision audit trail**
  - Record all review decisions in audit service
  - Include reviewer, decision, notes, timestamp

---

### 4.3 Bulk Import/Export

**Purpose:** Support large-scale data migration and backup/restore operations.

- [NOT STARTED] **Create `BulkImporter` interface for large-scale data ingestion**
  - Location: `src/main/java/com/entity/resolution/bulk/BulkImporter.java`
  - Example:
    ```java
    public interface BulkImporter {
        ImportResult importEntities(InputStream source, ImportOptions options);
        ImportResult importEntities(Path file, ImportOptions options);
    }

    public record ImportResult(
        long totalRecords,
        long successCount,
        long failureCount,
        long duplicateCount,
        List<ImportError> errors
    ) {}
    ```

- [NOT STARTED] **Implement `CsvBulkImporter` for CSV file import**
  - Location: `src/main/java/com/entity/resolution/bulk/CsvBulkImporter.java`
  - Support configurable column mapping
  - Use streaming for memory efficiency
  - Example CSV format: `name,type,synonyms`

- [NOT STARTED] **Implement `JsonBulkImporter` for JSON/JSONL file import**
  - Location: `src/main/java/com/entity/resolution/bulk/JsonBulkImporter.java`
  - Support JSON array and JSONL (line-delimited) formats
  - Use Jackson streaming API for large files

- [NOT STARTED] **Add progress tracking with callback for import operations**
  - Support progress callback: `onProgress(long processed, long total)`
  - Support error callback: `onError(long lineNumber, String error)`
  - Example:
    ```java
    importer.importEntities(file, ImportOptions.builder()
        .onProgress((processed, total) -> log.info("Progress: {}/{}", processed, total))
        .onError((line, error) -> log.warn("Line {}: {}", line, error))
        .build());
    ```

- [NOT STARTED] **Create `BulkExporter` interface for data export**
  - Location: `src/main/java/com/entity/resolution/bulk/BulkExporter.java`
  - Example:
    ```java
    public interface BulkExporter {
        void exportEntities(OutputStream destination, ExportOptions options);
        void exportEntities(Path file, ExportOptions options);
    }
    ```

- [NOT STARTED] **Implement `CsvBulkExporter` for CSV export with configurable columns**
  - Support column selection
  - Support filtering by entityType, status, date range

- [NOT STARTED] **Add streaming export for large datasets**
  - Use pagination internally
  - Write directly to output stream without buffering all data

---

### 4.4 Multi-Tenancy Support

**Purpose:** Enable single deployment to serve multiple isolated customers.

- [NOT STARTED] **Create `TenantContext` for tenant identification**
  - Location: `src/main/java/com/entity/resolution/tenant/TenantContext.java`
  - Use ThreadLocal for tenant propagation
  - Example:
    ```java
    public class TenantContext {
        private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

        public static void setTenant(String tenantId) { currentTenant.set(tenantId); }
        public static String getTenant() { return currentTenant.get(); }
        public static void clear() { currentTenant.remove(); }
    }
    ```

- [NOT STARTED] **Add `tenantId` field to `Entity`, `Synonym`, `Relationship` nodes**
  - Add field to all node types
  - Index on tenantId for query performance
  - Example schema:
    ```cypher
    (:Entity {id: "...", tenantId: "tenant-123", ...})
    CREATE INDEX FOR (e:Entity) ON (e.tenantId)
    ```

- [NOT STARTED] **Create `TenantAwareEntityRepository` filtering queries by tenant**
  - Location: `src/main/java/com/entity/resolution/tenant/TenantAwareEntityRepository.java`
  - Automatically add tenant filter to all queries
  - Example:
    ```java
    public class TenantAwareEntityRepository extends EntityRepository {
        @Override
        public Optional<Entity> findByNormalizedName(String name, EntityType type) {
            String tenantId = TenantContext.getTenant();
            return super.findByNormalizedNameAndTenant(name, type, tenantId);
        }
    }
    ```

- [NOT STARTED] **Add tenant isolation in `BatchContext`**
  - Capture tenant at batch creation
  - Apply tenant to all operations in batch

- [NOT STARTED] **Add tenant-specific configuration (thresholds, rules) support**
  - Allow different thresholds per tenant
  - Allow custom normalization rules per tenant
  - Example:
    ```java
    public interface TenantConfigurationService {
        ResolutionOptions getOptionsForTenant(String tenantId);
        List<NormalizationRule> getRulesForTenant(String tenantId);
    }
    ```

- [NOT STARTED] **Add integration tests for tenant isolation**
  - Test: tenant A cannot see tenant B's entities
  - Test: resolution only matches within same tenant
  - Test: relationships cannot cross tenant boundaries

---

### 4.5 Data Retention

**Purpose:** Comply with data retention regulations (GDPR, CCPA) and manage storage growth.

- [NOT STARTED] **Create `RetentionPolicy` configuration class with TTL settings**
  - Location: `src/main/java/com/entity/resolution/retention/RetentionPolicy.java`
  - Example:
    ```java
    public record RetentionPolicy(
        Duration mergedEntityRetention,    // How long to keep MERGED entities
        Duration auditEntryRetention,      // How long to keep audit entries
        Duration reviewItemRetention,      // How long to keep completed reviews
        boolean softDeleteEnabled          // Use soft delete vs hard delete
    ) {
        public static RetentionPolicy defaults() {
            return new RetentionPolicy(
                Duration.ofDays(365),   // 1 year for merged entities
                Duration.ofDays(2555),  // 7 years for audit (compliance)
                Duration.ofDays(90),    // 90 days for reviews
                true
            );
        }
    }
    ```

- [NOT STARTED] **Implement `RetentionService` for applying retention policies**
  - Location: `src/main/java/com/entity/resolution/retention/RetentionService.java`
  - Example:
    ```java
    public class RetentionService {
        public RetentionResult applyRetention(RetentionPolicy policy) {
            long mergedDeleted = cleanupMergedEntities(policy.mergedEntityRetention());
            long auditDeleted = cleanupAuditEntries(policy.auditEntryRetention());
            long reviewsDeleted = cleanupReviewItems(policy.reviewItemRetention());
            return new RetentionResult(mergedDeleted, auditDeleted, reviewsDeleted);
        }
    }
    ```

- [NOT STARTED] **Add retention policy for: merged entities, audit entries, review items**
  - Merged entities: delete after retention period (preserving merge ledger)
  - Audit entries: configurable per compliance requirements
  - Review items: delete completed reviews after period

- [NOT STARTED] **Implement scheduled cleanup job for expired data**
  - Run periodically (daily recommended)
  - Process in batches to avoid long-running transactions
  - Example:
    ```java
    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    public void runRetentionCleanup() {
        RetentionResult result = retentionService.applyRetention(policy);
        log.info("Retention cleanup completed: {}", result);
    }
    ```

- [NOT STARTED] **Add soft-delete support with `deletedAt` timestamp**
  - Add `deletedAt` field to nodes
  - Filter soft-deleted records from queries
  - Allow restoration within grace period
  - Example:
    ```java
    public void softDelete(String entityId) {
        executor.execute("""
            MATCH (e:Entity {id: $id})
            SET e.deletedAt = datetime()
            """, Map.of("id", entityId));
    }
    ```

- [NOT STARTED] **Add retention policy audit trail**
  - Record all retention deletions
  - Include what was deleted, when, and by which policy

---

## Phase 5: Testing & Documentation

### 5.1 Performance Testing

**Purpose:** Establish performance baselines and identify regressions.

- [NOT STARTED] **Create JMH benchmark suite for resolution operations**
  - Location: `src/test/java/com/entity/resolution/benchmark/`
  - Use JMH (Java Microbenchmark Harness)
  - Example:
    ```java
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public class ResolutionBenchmark {
        @Benchmark
        public EntityResolutionResult resolveNewEntity(BenchmarkState state) {
            return state.resolver.resolve("New Company " + System.nanoTime(), EntityType.COMPANY);
        }
    }
    ```

- [NOT STARTED] **Add benchmark: single entity resolution at 10K, 100K, 1M entity scale**
  - Measure resolution time with varying database sizes
  - Target: <50ms at 100K, <200ms at 1M (with blocking keys)

- [NOT STARTED] **Add benchmark: batch resolution throughput**
  - Measure entities resolved per second in batch mode
  - Target: >1000 entities/second

- [NOT STARTED] **Add benchmark: fuzzy match with blocking keys vs full scan**
  - Compare old O(n) approach vs new blocking key approach
  - Document improvement factor

- [NOT STARTED] **Add load test with concurrent resolution requests**
  - Use JMeter or Gatling
  - Test: 100 concurrent users, 1000 requests/second
  - Measure: p50, p95, p99 latency, error rate

- [NOT STARTED] **Document performance characteristics and tuning guidelines**
  - Create `PERFORMANCE.md` with:
    - Benchmark results at various scales
    - Recommended configurations for different workloads
    - Tuning parameters (thresholds, pool sizes, cache settings)

---

### 5.2 Integration Testing

**Purpose:** Verify system behavior with real FalkorDB instance.

- [NOT STARTED] **Add Testcontainers setup for FalkorDB integration tests**
  - Location: `src/test/java/com/entity/resolution/integration/`
  - Use Testcontainers for isolated test environment
  - Example:
    ```java
    @Testcontainers
    class EntityResolutionIntegrationTest {
        @Container
        static GenericContainer<?> falkordb = new GenericContainer<>("falkordb/falkordb:latest")
            .withExposedPorts(6379);

        @BeforeAll
        static void setup() {
            resolver = EntityResolver.builder()
                .falkorDB(falkordb.getHost(), falkordb.getFirstMappedPort(), "test-graph")
                .build();
        }
    }
    ```

- [NOT STARTED] **Add integration tests for full resolution workflow**
  - Test: create entity -> resolve same name -> verify match
  - Test: create entity -> resolve similar name -> verify fuzzy match
  - Test: create entity -> add synonym -> resolve via synonym

- [NOT STARTED] **Add integration tests for merge with relationship migration**
  - Test: entities with relationships -> merge -> verify relationships migrated
  - Test: verify no orphaned relationships after merge

- [NOT STARTED] **Add integration tests for concurrent batch operations**
  - Test: multiple batches running concurrently
  - Test: batch with relationships referencing entities from other batch

- [NOT STARTED] **Add chaos testing for connection failures**
  - Test: FalkorDB restart during batch -> verify recovery
  - Test: network partition -> verify timeout and retry
  - Use Toxiproxy for network fault injection

---

### 5.3 Documentation

**Purpose:** Enable adoption and operational success.

- [NOT STARTED] **Update CLAUDE.md with new enterprise features**
  - Document all new APIs
  - Add examples for new features
  - Update architecture diagrams

- [NOT STARTED] **Create architecture decision records (ADRs) for key decisions**
  - Location: `docs/adr/`
  - ADRs for: blocking key strategy, transaction approach, caching strategy
  - Example format:
    ```markdown
    # ADR-001: Blocking Key Strategy for Fuzzy Matching
    ## Context
    ## Decision
    ## Consequences
    ```

- [NOT STARTED] **Create deployment guide with configuration reference**
  - Location: `docs/DEPLOYMENT.md`
  - Include: system requirements, configuration options, environment variables
  - Include: Kubernetes/Docker deployment examples

- [NOT STARTED] **Create operations runbook with troubleshooting guide**
  - Location: `docs/OPERATIONS.md`
  - Include: common issues and resolutions
  - Include: monitoring dashboards setup
  - Include: incident response procedures

- [NOT STARTED] **Add Javadoc to all public API classes**
  - Document all public methods in `api/` package
  - Include usage examples in class-level Javadoc
  - Generate Javadoc site with `mvn javadoc:javadoc`

---

## Summary

| Phase | Tasks | Priority | Estimated Effort |
|-------|-------|----------|------------------|
| Phase 1: Critical Fixes | 18 tasks | **CRITICAL** | 2-3 sprints |
| Phase 2: Scalability | 21 tasks | HIGH | 2-3 sprints |
| Phase 3: Observability | 16 tasks | MEDIUM | 1-2 sprints |
| Phase 4: Enterprise Features | 24 tasks | MEDIUM | 2-3 sprints |
| Phase 5: Testing & Documentation | 14 tasks | HIGH | 1-2 sprints |
| **Total** | **93 tasks** | | **8-13 sprints** |

## Task Status Legend

- `[NOT STARTED]` - Task not yet begun
- `[IN PROGRESS]` - Task currently being worked on
- `[BLOCKED]` - Task blocked by dependency or issue
- `[DONE]` - Task completed
