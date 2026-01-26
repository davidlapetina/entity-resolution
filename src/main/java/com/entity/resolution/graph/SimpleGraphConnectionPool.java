package com.entity.resolution.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe connection pool using {@link Semaphore} for flow control
 * and {@link ConcurrentLinkedDeque} for idle connection management.
 *
 * <p>Each pooled connection is a separate {@link FalkorDBConnection} instance
 * because JFalkorDB's {@code Graph} is not thread-safe.</p>
 */
public class SimpleGraphConnectionPool implements GraphConnectionPool {
    private static final Logger log = LoggerFactory.getLogger(SimpleGraphConnectionPool.class);

    private final PoolConfig config;
    private final Semaphore permits;
    private final ConcurrentLinkedDeque<GraphConnection> idleConnections = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong totalBorrowed = new AtomicLong(0);
    private final AtomicLong totalReleased = new AtomicLong(0);
    private final AtomicLong totalCreated = new AtomicLong(0);

    public SimpleGraphConnectionPool(PoolConfig config) {
        this.config = config;
        this.permits = new Semaphore(config.getMaxTotal(), true);

        // Pre-create minIdle connections
        for (int i = 0; i < config.getMinIdle(); i++) {
            try {
                GraphConnection conn = createConnection();
                idleConnections.addLast(conn);
            } catch (Exception e) {
                log.warn("Failed to pre-create connection {}/{}: {}",
                        i + 1, config.getMinIdle(), e.getMessage());
            }
        }

        log.info("Connection pool initialized: {}", config);
    }

    @Override
    public GraphConnection borrow() {
        if (closed.get()) {
            throw new IllegalStateException("Pool is closed");
        }

        try {
            if (!permits.tryAcquire(config.getMaxWaitMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Timeout waiting for connection (maxWait=" + config.getMaxWaitMillis() + "ms)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for connection", e);
        }

        GraphConnection conn = idleConnections.pollFirst();

        if (conn != null) {
            if (config.isTestOnBorrow() && !conn.isConnected()) {
                log.debug("Idle connection failed validation, creating new one");
                closeQuietly(conn);
                conn = createConnection();
            }
        } else {
            conn = createConnection();
        }

        totalBorrowed.incrementAndGet();
        log.debug("Connection borrowed (active={}, idle={})",
                getActiveCount(), idleConnections.size());
        return conn;
    }

    @Override
    public void release(GraphConnection connection) {
        if (connection == null) {
            return;
        }

        totalReleased.incrementAndGet();

        if (closed.get() || idleConnections.size() >= config.getMaxIdle()) {
            closeQuietly(connection);
        } else {
            idleConnections.addLast(connection);
        }

        permits.release();
        log.debug("Connection released (active={}, idle={})",
                getActiveCount(), idleConnections.size());
    }

    @Override
    public PoolStats getStats() {
        int idle = idleConnections.size();
        int active = getActiveCount();
        return new PoolStats(
                active + idle,
                active,
                idle,
                totalBorrowed.get(),
                totalReleased.get(),
                totalCreated.get()
        );
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Closing connection pool...");
            GraphConnection conn;
            while ((conn = idleConnections.pollFirst()) != null) {
                closeQuietly(conn);
            }
            log.info("Connection pool closed");
        }
    }

    private GraphConnection createConnection() {
        totalCreated.incrementAndGet();
        return new FalkorDBConnection(config.getHost(), config.getPort(), config.getGraphName());
    }

    private int getActiveCount() {
        return config.getMaxTotal() - permits.availablePermits() - idleConnections.size();
    }

    private void closeQuietly(GraphConnection connection) {
        try {
            connection.close();
        } catch (Exception e) {
            log.warn("Error closing connection: {}", e.getMessage());
        }
    }
}
