package com.mockwise.practice.dto.response;

import com.mockwise.practice.enums.SubmissionMode;
import com.mockwise.practice.enums.SubmissionStatus;

import java.time.LocalDateTime;
import java.util.List;

/** Full submission view: the summary fields plus source code and per-case results. */
public record SubmissionDetail(
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
        Integer memoryKb,
        String sourceCode,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        List<SubmissionCaseView> cases,
        /** First failing hidden case, revealed only on a non-accepted SUBMIT; null otherwise. */
        RevealedCase revealedCase
) {}
