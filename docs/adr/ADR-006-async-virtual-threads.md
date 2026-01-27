# ADR-006: Async Operations with Virtual Threads

## Status
Accepted

## Context
Some entity resolution workloads benefit from asynchronous execution:
- Bulk imports where the caller doesn't need immediate results
- LLM enrichment requests that have high latency (100ms-10s per call)
- Batch processing pipelines where multiple batches can run concurrently

Java 21 introduced virtual threads (Project Loom), which provide lightweight concurrency without the complexity of reactive programming or callback-based async APIs.

## Decision
We implemented **`AsyncEntityResolver`** as a thin wrapper around `EntityResolver` that:

1. Uses `Executors.newVirtualThreadPerTaskExecutor()` for all async operations
2. Returns `CompletableFuture<T>` for all resolution, batch, and relationship operations
3. Is accessed via `resolver.async()` from the main `EntityResolver`

### API Design
```java
AsyncEntityResolver async = resolver.async();
CompletableFuture<EntityResolutionResult> future = async.resolve("Company", EntityType.COMPANY);
future.thenAccept(result -> process(result));
```

### Thread Model
- Each async operation runs on its own virtual thread
- Virtual threads are lightweight (~1KB stack) and do not require thread pool sizing
- Blocking I/O (FalkorDB queries) is automatically handled by the virtual thread runtime
- Timeout is configurable via `ResolutionOptions.asyncTimeoutMs` (default 30s)

## Consequences

### Positive
- Non-blocking API for latency-sensitive callers
- Virtual threads scale to millions of concurrent operations without thread pool tuning
- No external dependency (uses `java.util.concurrent` built into Java 21)
- Simple programming model: synchronous code inside virtual threads
- LLM enrichment calls benefit significantly from async execution

### Negative
- Requires Java 21+ (virtual threads are a preview feature in Java 19-20)
- `synchronized` blocks can pin virtual threads to carrier threads; we use `ReentrantLock` instead
- Debugging is harder with many concurrent virtual threads
- Error handling requires `CompletableFuture` composition (`.exceptionally()`, `.handle()`)

## Alternatives Considered
- **Reactive Streams (Project Reactor / RxJava)**: More powerful but significantly more complex; steep learning curve
- **Platform thread pool**: Well-understood but requires pool sizing and doesn't scale as well
- **Kotlin Coroutines**: Good fit but adds a language dependency; not appropriate for a Java library
