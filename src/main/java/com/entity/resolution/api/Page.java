package com.entity.resolution.api;

import java.util.List;

/**
 * A page of results from a paginated query.
 *
 * @param content       the content of this page (defensive copy)
 * @param totalElements total number of elements across all pages
 * @param pageNumber    the current page number (0-based)
 * @param pageSize      the requested page size
 * @param <T>           the element type
 */
public record Page<T>(List<T> content, long totalElements, int pageNumber, int pageSize) {

    public Page {
        content = content != null ? List.copyOf(content) : List.of();
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be >= 0");
        }
    }

    /**
     * Returns whether there is a next page.
     */
    public boolean hasNext() {
        return (long) (pageNumber + 1) * pageSize < totalElements;
    }

    /**
     * Returns whether there is a previous page.
     */
    public boolean hasPrevious() {
        return pageNumber > 0;
    }

    /**
     * Returns the total number of pages.
     */
    public int totalPages() {
        return pageSize == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
    }

    /**
     * Returns the number of elements on this page.
     */
    public int numberOfElements() {
        return content.size();
    }

    /**
     * Returns whether this page has content.
     */
    public boolean hasContent() {
        return !content.isEmpty();
    }

    /**
     * Returns an empty page for the given request.
     */
    public static <T> Page<T> empty(PageRequest request) {
        return new Page<>(List.of(), 0, request.pageNumber(), request.limit());
    }
}
