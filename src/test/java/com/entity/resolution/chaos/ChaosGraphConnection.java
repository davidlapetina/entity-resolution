package com.entity.resolution.chaos;

import com.entity.resolution.graph.GraphConnection;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decorator around a {@link GraphConnection} that injects configurable failures
 * for chaos/resilience testing. Supports simulating connection failures,
 * intermittent errors, latency injection, and disconnects.
 */
public class ChaosGraphConnection implements GraphConnection {

    private final GraphConnection delegate;
    private final AtomicBoolean failOnExecute = new AtomicBoolean(false);
    private final AtomicBoolean failOnQuery = new AtomicBoolean(false);
    private final AtomicBoolean simulateDisconnect = new AtomicBoolean(false);
    private final AtomicInteger failAfterNOperations = new AtomicInteger(-1);
    private final AtomicInteger operationCount = new AtomicInteger(0);
    private volatile long injectDelayMs = 0;

    public ChaosGraphConnection(GraphConnection delegate) {
        this.delegate = delegate;
    }

    /**
     * When enabled, all execute() calls throw RuntimeException.
     */
    public void setFailOnExecute(boolean fail) {
        failOnExecute.set(fail);
    }

    /**
     * When enabled, all query() calls throw RuntimeException.
     */
    public void setFailOnQuery(boolean fail) {
        failOnQuery.set(fail);
    }

    /**
     * When enabled, isConnected() returns false.
     */
    public void setSimulateDisconnect(boolean disconnected) {
        simulateDisconnect.set(disconnected);
    }

    /**
     * Configures the connection to fail after N successful operations.
     * Set to -1 to disable.
     *
     * @param n the number of operations after which to start failing
     */
    public void setFailAfterNOperations(int n) {
        failAfterNOperations.set(n);
        operationCount.set(0);
    }

    /**
     * Injects a delay (in milliseconds) before each operation.
     *
     * @param delayMs delay in milliseconds (0 to disable)
     */
    public void setInjectDelayMs(long delayMs) {
        this.injectDelayMs = delayMs;
    }

    /**
     * Resets all failure modes to normal behavior.
     */
    public void reset() {
        failOnExecute.set(false);
        failOnQuery.set(false);
        simulateDisconnect.set(false);
        failAfterNOperations.set(-1);
        operationCount.set(0);
        injectDelayMs = 0;
    }

    @Override
    public void execute(String query, Map<String, Object> params) {
        injectDelay();
        checkOperationLimit();

        if (failOnExecute.get()) {
            throw new RuntimeException("ChaosGraphConnection: simulated execute failure");
        }

        delegate.execute(query, params);
    }

    @Override
    public List<Map<String, Object>> query(String query, Map<String, Object> params) {
        injectDelay();
        checkOperationLimit();

        if (failOnQuery.get()) {
            throw new RuntimeException("ChaosGraphConnection: simulated query failure");
        }

        return delegate.query(query, params);
    }

    @Override
    public boolean isConnected() {
        if (simulateDisconnect.get()) {
            return false;
        }
        return delegate.isConnected();
    }

    @Override
    public String getGraphName() {
        return delegate.getGraphName();
    }

    @Override
    public void createIndexes() {
        if (failOnExecute.get()) {
            throw new RuntimeException("ChaosGraphConnection: simulated createIndexes failure");
        }
        delegate.createIndexes();
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }

    private void injectDelay() {
        if (injectDelayMs > 0) {
            try {
                Thread.sleep(injectDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during chaos delay", e);
            }
        }
    }

    private void checkOperationLimit() {
        int limit = failAfterNOperations.get();
        if (limit >= 0) {
            int count = operationCount.incrementAndGet();
            if (count > limit) {
                throw new RuntimeException(
                        "ChaosGraphConnection: operation limit exceeded (" + count + " > " + limit + ")");
            }
        }
    }
}
