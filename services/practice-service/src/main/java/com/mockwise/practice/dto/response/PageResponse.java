package com.mockwise.practice.dto.response;

import java.util.List;

/** Paginated envelope returned by practice list endpoints. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements, int totalPages) {
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
