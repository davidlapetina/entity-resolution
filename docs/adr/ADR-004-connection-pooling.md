# ADR-004: Connection Pooling

## Status
Accepted

## Context
Each `FalkorDBConnection` holds a single TCP connection to FalkorDB. Under concurrent load (batch processing, parallel API calls), a single connection becomes a bottleneck due to:
- TCP head-of-line blocking
- FalkorDB serializing commands on a single connection
- Connection setup overhead for short-lived operations

## Decision
We implemented a **`GraphConnectionPool`** using Apache Commons Pool2 semantics:

- **Pool configuration** via `PoolConfig`:
  - `maxTotal`: Maximum active connections (default 10)
  - `maxIdle`: Maximum idle connections (default 5)
  - `minIdle`: Minimum idle connections maintained (default 1)
  - `maxWaitMs`: Maximum time to wait for a connection (default 5000ms)
- **`PooledFalkorDBConnection`**: Wraps individual connections with pool lifecycle management
- **Connection validation**: Idle connections are validated via `isConnected()` before being returned from the pool
- **Automatic eviction**: Connections idle beyond `maxIdleTimeMs` are closed

### Integration Point
The pool is injected via `EntityResolver.builder().falkorDBPool(poolConfig)`, which creates a `GraphConnectionPool` that the resolver uses for all operations.

## Consequences

### Positive
- Concurrent operations scale with pool size rather than being serialized
- Connection reuse eliminates per-operation TCP handshake overhead
- Configurable pool sizes allow tuning for different workloads
- Health checks can report pool utilization and availability

### Negative
- Pool management adds ~0.05ms overhead per operation (borrow/return)
- Misconfigured pool sizes can cause connection starvation (too small) or resource waste (too large)
- Pool exhaustion throws exceptions rather than queuing indefinitely
- Additional dependency on pooling infrastructure

## Alternatives Considered
- **Connection-per-thread (ThreadLocal)**: Simpler but wastes connections when threads are idle
- **Single shared connection with queue**: Eliminates pool complexity but serializes all operations
- **FalkorDB cluster mode**: Could distribute load but adds operational complexity; not yet supported by jfalkordb driver
