package com.entity.resolution.cache;

/**
 * Listener for entity merge events. Implementations can react to merges,
 * e.g., by invalidating cache entries.
 */
public interface MergeListener {

    /**
     * Called after a successful merge operation.
     *
     * @param sourceEntityId the entity that was merged (became duplicate)
     * @param targetEntityId the entity that was merged into (canonical)
     */
    void onMerge(String sourceEntityId, String targetEntityId);
}
