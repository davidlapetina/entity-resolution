package com.entity.resolution.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-process distributed lock using {@link ReentrantLock}.
 * Suitable for single-JVM deployments. This is the default lock implementation.
 */
public class LocalDistributedLock implements DistributedLock {
    private static final Logger log = LoggerFactory.getLogger(LocalDistributedLock.class);

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final LockConfig config;

    public LocalDistributedLock() {
        this(LockConfig.defaults());
    }

    public LocalDistributedLock(LockConfig config) {
        this.config = config;
    }

    @Override
    public boolean tryLock(String key) {
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        try {
            boolean acquired = lock.tryLock(config.timeoutMs(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new LockAcquisitionException(
                        "Failed to acquire lock for key '" + key + "' within " + config.timeoutMs() + "ms");
            }
            log.debug("Lock acquired: {}", key);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while acquiring lock for key: " + key, e);
        }
    }

    @Override
    public void unlock(String key) {
        ReentrantLock lock = locks.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("Lock released: {}", key);
        }
    }
}
