package com.mockwise.practice.dto.response;

import java.time.LocalDateTime;

/**
 * One problem's roll-up across the user's graded SUBMITs — the LeetCode
 * "progress" row. {@code solved} is true once any submit was accepted;
 * {@code bestRuntimeMs} is the fastest accepted run (null until solved).
 */
public record ProblemSubmissionGroup(
        String problemId,
        String problemTitle,
        String difficulty,
        boolean solved,
        long submissionCount,
        long acceptedCount,
        Integer bestRuntimeMs,
        LocalDateTime firstSolvedAt,
        LocalDateTime lastSubmittedAt
) {}
