package com.entity.resolution.rest.dto;

import java.util.List;

/**
 * Request DTO for batch entity resolution.
 */
public record BatchResolveRequest(
        List<BatchItem> items,
        String sourceSystem
) {
    private static final int MAX_BATCH_SIZE = 1000;

    public BatchResolveRequest {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        if (items.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "batch size " + items.size() + " exceeds maximum of " + MAX_BATCH_SIZE);
        }
    }

    /**
     * A single item in a batch resolve request.
     */
    public record BatchItem(String name, String entityType) {
        public BatchItem {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (entityType == null || entityType.isBlank()) {
                throw new IllegalArgumentException("entityType is required");
            }
        }
    }
}
