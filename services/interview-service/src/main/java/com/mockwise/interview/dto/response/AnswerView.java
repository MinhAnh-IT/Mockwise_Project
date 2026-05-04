package com.mockwise.interview.dto.response;

import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Single-answer projection for the FE poll endpoint
 * {@code GET /interviews/{sid}/answers/{aid}}. Carries enough state for
 * the UI to render "scoring…" / "ready" / "failed" plus the verdict
 * + feedback once SCORED.
 */
public record AnswerView(
        UUID answerId,
        UUID sessionId,
        UUID sessionQuestionId,
        AnswerType type,
        AnswerStatus status,
        Float score,
        Float maxScore,
        String feedback,
        Map<String, Object> verdict,
        Map<String, Object> rubricScores,
        String errorCode,
        String errorMessage,
        OffsetDateTime submittedAt,
        OffsetDateTime scoredAt
) {

    public static AnswerView fromEntity(Answer a) {
        return new AnswerView(
                a.getId(),
                a.getSessionId(),
                a.getSessionQuestionId(),
                a.getType(),
                a.getStatus(),
                a.getScore(),
                a.getMaxScore(),
                a.getFeedback(),
                a.getVerdict(),
                a.getRubricScores(),
                a.getErrorCode(),
                a.getErrorMessage(),
                a.getSubmittedAt(),
                a.getScoredAt());
    }
}
