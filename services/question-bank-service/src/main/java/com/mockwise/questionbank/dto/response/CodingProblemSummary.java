package com.mockwise.questionbank.dto.response;

import com.mockwise.questionbank.enums.Difficulty;

import java.util.List;

/**
 * Lightweight row for the practice catalog browse list. Carries only what a
 * LeetCode-style problem table renders — never the description / testcases /
 * starter code (those are fetched on open via the detail endpoint). Backs
 * {@code GET /internal/coding-problems}.
 */
public record CodingProblemSummary(
        String id,
        String title,
        Difficulty difficulty,
        List<String> tags,
        String optimalTimeComplexity,
        String optimalSpaceComplexity
) {}
