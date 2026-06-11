package com.mockwise.practice.dto.response;

import com.mockwise.practice.enums.ProblemStatus;

import java.util.List;

/**
 * One row in the practice problem-list UI.
 *
 * <p>{@code myStatus} is the caller's solved/attempted state; {@code acceptanceRate}
 * is the global accept ratio. Both are placeholders in Phase 0 ({@code NONE} /
 * {@code null}) until submission history (Phase 2/3) backs them.
 */
public record ProblemSummary(
        String id,
        String title,
        String difficulty,
        List<String> tags,
        String optimalTimeComplexity,
        String optimalSpaceComplexity,
        Double acceptanceRate,
        ProblemStatus myStatus
) {}
