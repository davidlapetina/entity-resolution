package com.entity.resolution.bulk;

/**
 * Callback interface for tracking progress of bulk operations.
 */
@FunctionalInterface
public interface ProgressCallback {

    /**
     * Called to report progress.
     *
     * @param processed the number of records processed so far
     * @param total     the total number of records (may be -1 if unknown)
     * @param message   optional progress message
     */
    void onProgress(long processed, long total, String message);

    /**
     * A no-op progress callback.
     */
    ProgressCallback NOOP = (processed, total, message) -> {};
}
