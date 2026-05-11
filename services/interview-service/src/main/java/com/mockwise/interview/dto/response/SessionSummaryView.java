package com.mockwise.interview.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>{@code hireSignal} and {@code grade} are pulled out of the typed
 * {@link OverallReviewView} (deserialised from {@code metadata.overallReview})
 * when the session has reached SCORED; both stay null otherwise. We don't
 * expose the full review here to keep the list response small.
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

    public static SessionSummaryView fromEntity(InterviewSession s, ObjectMapper objectMapper) {
        OverallReviewView review = extractOverallReview(s, objectMapper);
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
                review != null ? review.hireSignal() : null,
                review != null ? review.grade() : null);
    }

    @SuppressWarnings("unchecked")
    static OverallReviewView extractOverallReview(InterviewSession s, ObjectMapper objectMapper) {
        if (s.getStatus() != SessionStatus.SCORED || s.getMetadata() == null || objectMapper == null) {
            return null;
        }
        Object review = s.getMetadata().get("overallReview");
        if (!(review instanceof Map<?, ?> map)) return null;
        try {
            return objectMapper.convertValue((Map<String, Object>) map, OverallReviewView.class);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
