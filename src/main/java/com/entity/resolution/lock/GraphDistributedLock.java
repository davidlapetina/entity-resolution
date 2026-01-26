package com.entity.resolution.lock;

import com.entity.resolution.graph.GraphConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * FalkorDB MERGE-based advisory lock for multi-JVM distributed locking.
 *
 * <p>Uses a {@code :Lock} node with atomic MERGE for check-and-set semantics.
 * Expired locks are automatically reclaimed based on TTL.</p>
 */
public class GraphDistributedLock implements DistributedLock {
    private static final Logger log = LoggerFactory.getLogger(GraphDistributedLock.class);

    private final GraphConnection connection;
    private final LockConfig config;
    private final String ownerId;

    public GraphDistributedLock(GraphConnection connection) {
        this(connection, LockConfig.defaults());
    }

    public GraphDistributedLock(GraphConnection connection, LockConfig config) {
        this.connection = connection;
        this.config = config;
        this.ownerId = generateOwnerId();
        createLockIndex();
    }

    @Override
    public boolean tryLock(String key) {
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            if (attemptLock(key)) {
                log.debug("Lock acquired: {} (attempt {})", key, attempt + 1);
                return true;
            }

            if (attempt < config.maxRetries()) {
                try {
                    Thread.sleep(config.retryDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LockAcquisitionException("Interrupted while acquiring lock for: " + key, e);
                }
            }
        }

        throw new LockAcquisitionException(
                "Failed to acquire lock for key '" + key + "' after " + (config.maxRetries() + 1) + " attempts");
    }

    @Override
    public void unlock(String key) {
        String query = """
                MATCH (l:Lock {key: $key, owner: $owner})
                DELETE l
                """;
        try {
            connection.execute(query, Map.of("key", key, "owner", ownerId));
            log.debug("Lock released: {}", key);
        } catch (Exception e) {
            log.warn("Failed to release lock {}: {}", key, e.getMessage());
        }
    }

    private boolean attemptLock(String key) {
        String now = Instant.now().toString();
        String expiresAt = Instant.now().plusSeconds(config.lockTtlSeconds()).toString();

        // Try to create lock node; if it exists, check if expired
        String query = """
                MERGE (l:Lock {key: $key})
                ON CREATE SET l.owner = $owner, l.acquiredAt = $now, l.expiresAt = $expiresAt
                ON MATCH SET l.owner = CASE
                    WHEN l.expiresAt < $now THEN $owner
                    ELSE l.owner
                END,
                l.acquiredAt = CASE
                    WHEN l.expiresAt < $now THEN $now
                    ELSE l.acquiredAt
                END,
                l.expiresAt = CASE
                    WHEN l.expiresAt < $now THEN $expiresAt
                    ELSE l.expiresAt
                END
                RETURN l.owner as owner
                """;

        try {
            List<Map<String, Object>> results = connection.query(query, Map.of(
                    "key", key,
                    "owner", ownerId,
                    "now", now,
                    "expiresAt", expiresAt
            ));

            if (!results.isEmpty()) {
                String lockOwner = (String) results.get(0).get("owner");
                return ownerId.equals(lockOwner);
            }
            return false;
        } catch (Exception e) {
            log.warn("Lock acquisition attempt failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    private void createLockIndex() {
        try {
            connection.execute("CREATE INDEX FOR (l:Lock) ON (l.key)");
        } catch (Exception e) {
            log.debug("Lock index creation: {}", e.getMessage());
        }
    }

    private String generateOwnerId() {
        return ProcessHandle.current().pid() + "-" + Thread.currentThread().threadId()
                + "-" + System.nanoTime();
    }
}
