package com.mockwise.interview.dto.response;

import com.mockwise.interview.entity.InterviewSession;
import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.SessionStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Slim projection for the session list (history) page. Carries just enough
 * for a card preview — full topic/question/overallReview detail is fetched
 * via {@code GET /interviews/{sid}} on click.
 *
 * <p>{@code hireSignal} and {@code grade} are pulled out of the
 * {@code metadata.overallReview} JSON when the session has reached SCORED;
 * both stay null otherwise. We don't expose the raw {@code overallReview}
 * map here to keep the list response small.
 */
public record SessionSummaryView(
        UUID sessionId,
        String targetRole,
        String level,
        InterviewType interviewType,
        SessionStatus status,
        int questionCount,
        int timeBudgetMinutes,
        Float finalScore,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime scoredAt,
        OffsetDateTime createdAt,
        String hireSignal,
        String grade
) {

    public static SessionSummaryView fromEntity(InterviewSession s) {
        Map<String, Object> review = extractOverallReview(s);
        return new SessionSummaryView(
                s.getId(),
                s.getTargetRole(),
                s.getLevel(),
                s.getInterviewType(),
                s.getStatus(),
                s.getQuestionCount(),
                s.getTimeBudgetMinutes(),
                s.getFinalScore(),
                s.getStartedAt(),
                s.getFinishedAt(),
                s.getScoredAt(),
                s.getCreatedAt(),
                review != null ? asString(review.get("hireSignal")) : null,
                review != null ? asString(review.get("grade")) : null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractOverallReview(InterviewSession s) {
        if (s.getStatus() != SessionStatus.SCORED || s.getMetadata() == null) {
            return null;
        }
        Object review = s.getMetadata().get("overallReview");
        return review instanceof Map ? (Map<String, Object>) review : null;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
