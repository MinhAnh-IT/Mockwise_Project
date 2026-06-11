package com.mockwise.practice.client.questionbank.dto;

import java.util.List;

/** Mirrors question-bank's {@code CodingProblemSummary} browse row. */
public record QbProblemSummary(
        String id,
        String title,
        String difficulty,
        List<String> tags,
        String optimalTimeComplexity,
        String optimalSpaceComplexity
) {}
