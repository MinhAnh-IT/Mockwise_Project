package com.mockwise.iam.audit.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Minimal page envelope so the FE gets a stable JSON shape independent of Spring Data internals. */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <E, T> PagedResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
