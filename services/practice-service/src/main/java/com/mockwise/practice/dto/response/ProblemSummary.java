package com.mockwise.practice.dto.response;

import com.mockwise.practice.enums.ProblemStatus;

import java.util.List;

/**
 * One row in the practice problem-list UI.
 *
 * <p>{@code myStatus} is the caller's solved/attempted state; {@code acceptanceRate}
 * is the global accept ratio (accepted/graded SUBMITs, 0..1), {@code null} until a
 * problem has its first graded submission.
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
