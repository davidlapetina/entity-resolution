package com.entity.resolution.lock;

/**
 * No-op lock implementation. Always succeeds immediately.
 * Used as the default when distributed locking is not needed.
 */
public class NoOpDistributedLock implements DistributedLock {

    @Override
    public boolean tryLock(String key) {
        return true;
    }

    @Override
    public void unlock(String key) {
        // no-op
    }
}
