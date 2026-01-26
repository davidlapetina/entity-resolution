package com.entity.resolution.lock;

/**
 * Distributed lock interface for preventing duplicate entity creation
 * during concurrent resolution.
 */
public interface DistributedLock {

    /**
     * Attempts to acquire a lock on the given key.
     *
     * @param key the lock key (typically normalizedName:entityType)
     * @return true if the lock was acquired
     * @throws LockAcquisitionException if lock acquisition fails after retries
     */
    boolean tryLock(String key);

    /**
     * Releases a lock on the given key.
     *
     * @param key the lock key
     */
    void unlock(String key);
}
