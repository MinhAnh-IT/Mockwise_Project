package com.interview.judge.admin.dto;

import com.interview.judge.entity.JudgeJob;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** One row in the admin job table. */
public record JudgeJobSummary(
        UUID id,
        UUID submissionId,
        String origin,
        String status,
        String verdict,
        String language,
        int doneCases,
        int totalCases,
        int retryCount,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt,
        Long latencyMs
) {
    public static JudgeJobSummary from(JudgeJob j) {
        Long latency = null;
        if (j.getFinishedAt() != null && j.getCreatedAt() != null) {
            latency = java.time.Duration.between(j.getCreatedAt(), j.getFinishedAt()).toMillis();
        }
        return new JudgeJobSummary(
                j.getId(),
                j.getSubmissionId(),
                j.getOrigin(),
                j.getStatus().name(),
                j.getVerdict(),
                j.getLanguage(),
                j.getDoneCases(),
                j.getTotalCases(),
                j.getRetryCount(),
                j.getCreatedAt() == null ? null : j.getCreatedAt().atOffset(ZoneOffset.UTC),
                j.getFinishedAt() == null ? null : j.getFinishedAt().atOffset(ZoneOffset.UTC),
                latency
        );
    }
}
