package com.entity.resolution.lock;

/**
 * Runtime exception thrown when a distributed lock cannot be acquired
 * within the configured timeout.
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
