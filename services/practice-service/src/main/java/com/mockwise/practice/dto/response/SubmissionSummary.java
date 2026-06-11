package com.mockwise.practice.dto.response;

import com.mockwise.practice.enums.SubmissionMode;
import com.mockwise.practice.enums.SubmissionStatus;

import java.time.LocalDateTime;

/**
 * One row in the submissions history. {@code verdict} is the raw judge code
 * (AC | WA | TLE | MLE | RE | CE) and is null until the submission is DONE.
 */
public record SubmissionSummary(
        String id,
        String problemId,
        String problemTitle,
        String language,
        SubmissionMode mode,
        SubmissionStatus status,
        String verdict,
        int passedCases,
        int totalCases,
        Integer runtimeMs,
        LocalDateTime createdAt
) {}
