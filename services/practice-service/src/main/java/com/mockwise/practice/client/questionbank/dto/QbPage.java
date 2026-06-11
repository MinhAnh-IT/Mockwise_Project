package com.mockwise.practice.client.questionbank.dto;

import java.util.List;

/** Mirrors question-bank's {@code PageResponse<T>} wire shape. */
public record QbPage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
