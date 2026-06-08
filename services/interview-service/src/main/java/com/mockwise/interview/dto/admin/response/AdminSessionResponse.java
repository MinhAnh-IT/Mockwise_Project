package com.mockwise.interview.dto.admin.response;

import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.SessionStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only admin row for an interview session. Deliberately shallow: it carries
 * lifecycle + scoring metadata only, never the answers, transcripts or pinned
 * questions — admins may oversee and report on sessions, not inspect their
 * contents.
 */
public record AdminSessionResponse(
        UUID id,
        String userId,
        String targetRole,
        String level,
        InterviewType interviewType,
        SessionStatus status,
        int questionCount,
        Float finalScore,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime scoredAt,
        OffsetDateTime createdAt
) {
    public static AdminSessionResponse from(InterviewSession s) {
        return new AdminSessionResponse(
                s.getId(),
                s.getUserId(),
                s.getTargetRole(),
                s.getLevel(),
                s.getInterviewType(),
                s.getStatus(),
                s.getQuestionCount(),
                s.getFinalScore(),
                s.getStartedAt(),
                s.getFinishedAt(),
                s.getScoredAt(),
                s.getCreatedAt());
    }
}
