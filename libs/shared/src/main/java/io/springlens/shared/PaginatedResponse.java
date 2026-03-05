package io.springlens.shared;

import java.util.List;

/**
 * Generic paginated response wrapper used across SpringLens APIs.
 *
 * @param <T> the type of items in the page
 */
public record PaginatedResponse<T>(
        List<T> data,
        String cursor,
        long total) {

    public static <T> PaginatedResponse<T> of(List<T> data, String cursor, long total) {
        return new PaginatedResponse<>(data, cursor, total);
    }

    public static <T> PaginatedResponse<T> of(List<T> data, long total) {
        return new PaginatedResponse<>(data, null, total);
    }
}
