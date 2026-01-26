package com.entity.resolution.graph;

/**
 * Connection pool interface for managing multiple {@link GraphConnection} instances.
 * Provides borrow/release semantics for safe concurrent access to FalkorDB.
 */
public interface GraphConnectionPool extends AutoCloseable {

    /**
     * Borrows a connection from the pool. Blocks until a connection is available
     * or the configured timeout expires.
     *
     * @return a graph connection
     * @throws IllegalStateException if the pool is closed or timeout expires
     */
    GraphConnection borrow();

    /**
     * Returns a connection to the pool.
     *
     * @param connection the connection to release
     */
    void release(GraphConnection connection);

    /**
     * Returns current pool statistics.
     */
    PoolStats getStats();

    /**
     * Closes the pool and all managed connections.
     */
    @Override
    void close();
}
