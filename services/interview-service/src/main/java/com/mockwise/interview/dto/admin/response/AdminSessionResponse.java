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
 * <p>{@code questionCount} is the planned base count snapshot at /start (kept for
 * compatibility). {@code answeredQuestions} (A) is the real submitted-answer
 * count. {@code totalQuestions} is the display denominator {@code max(A, B)} where
 * B is the live blueprint's question budget: a candidate under budget reads A/B
 * (stopped early), and once follow-ups push A to or past B it reads A/A. For
 * non-adaptive CODING A never exceeds B, so it always reads A/B.
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
