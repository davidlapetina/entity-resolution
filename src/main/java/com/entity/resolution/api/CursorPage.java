package com.entity.resolution.api;

import java.util.List;

/**
 * A cursor-based page of results. The cursor is an ISO-8601 timestamp string
 * used for efficient pagination through time-ordered data (e.g., audit entries).
 *
 * @param content    the content of this page
 * @param nextCursor the cursor for fetching the next page (ISO-8601 timestamp), or null if no more
 * @param hasMore    whether there are more results after this page
 * @param <T>        the element type
 */
public record CursorPage<T>(List<T> content, String nextCursor, boolean hasMore) {

    public CursorPage {
        content = content != null ? List.copyOf(content) : List.of();
    }

    /**
     * Returns the number of elements on this page.
     */
    public int size() {
        return content.size();
    }

    /**
     * Returns whether this page has content.
     */
    public boolean hasContent() {
        return !content.isEmpty();
    }

    /**
     * Returns an empty cursor page.
     */
    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }
}
