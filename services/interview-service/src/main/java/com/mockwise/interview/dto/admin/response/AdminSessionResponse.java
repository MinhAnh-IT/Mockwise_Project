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
 *
 * <p>{@code questionCount} is the planned base count set at /start (the blueprint
 * budget; the fixed coding-plan size for CODING). {@code answeredQuestions} is the
 * real submitted-answer count. {@code totalQuestions} is the display denominator:
 * the blueprint budget while the candidate is at or under it, falling back to the
 * actual pinned-question count once follow-ups push the answered count past the
 * budget — so the admin list shows an accurate "answered / total" for every type.
 */
public record AdminSessionResponse(
        UUID id,
        String userId,
        String targetRole,
        String level,
        InterviewType interviewType,
        SessionStatus status,
        int questionCount,
        int answeredQuestions,
        int totalQuestions,
        Float finalScore,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime scoredAt,
        OffsetDateTime createdAt
) {
    public static AdminSessionResponse from(InterviewSession s, int answeredQuestions, int totalQuestions) {
        return new AdminSessionResponse(
                s.getId(),
                s.getUserId(),
                s.getTargetRole(),
                s.getLevel(),
                s.getInterviewType(),
                s.getStatus(),
                s.getQuestionCount(),
                answeredQuestions,
                totalQuestions,
                s.getFinalScore(),
                s.getStartedAt(),
                s.getFinishedAt(),
                s.getScoredAt(),
                s.getCreatedAt());
    }
}
