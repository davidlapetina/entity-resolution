package com.entity.resolution.api;

/**
 * Pagination request specifying offset, limit, and optional sorting.
 */
public record PageRequest(int offset, int limit, String sortBy, SortDirection sortDirection) {

    public enum SortDirection {
        ASC, DESC
    }

    private static final int MAX_LIMIT = 10_000;

    public PageRequest {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be > 0 and <= " + MAX_LIMIT);
        }
    }

    /**
     * Creates a page request from page number and size.
     * Page numbering starts at 0.
     */
    public static PageRequest of(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        return new PageRequest(page * size, size, null, SortDirection.ASC);
    }

    /**
     * Creates a request for the first page of the given size.
     */
    public static PageRequest first(int size) {
        return of(0, size);
    }

    /**
     * Creates a page request with sorting.
     */
    public static PageRequest of(int page, int size, String sortBy, SortDirection direction) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        return new PageRequest(page * size, size, sortBy, direction);
    }

    /**
     * Returns the page number (derived from offset and limit).
     */
    public int pageNumber() {
        return offset / limit;
    }
}
