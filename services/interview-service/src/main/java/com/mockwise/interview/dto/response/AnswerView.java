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

    /**
     * Masks score / feedback / verdict / rubricScores when {@code revealResults}
     * is false — used while the session is still in progress so the candidate
     * cannot peek at per-question grading. Status, errorCode, errorMessage and
     * the timestamps stay exposed so the FE can still render
     * "submitted → scoring → done" transitions between questions. Once the
     * session reaches SCORED the controller calls this with
     * {@code revealResults = true}.
     */
    public static AnswerView fromEntity(Answer a, boolean revealResults) {
        return new AnswerView(
                a.getId(),
                a.getSessionId(),
                a.getSessionQuestionId(),
                a.getType(),
                a.getStatus(),
                revealResults ? a.getScore() : null,
                revealResults ? a.getMaxScore() : null,
                revealResults ? a.getFeedback() : null,
                revealResults ? a.getVerdict() : null,
                revealResults ? a.getRubricScores() : null,
                a.getErrorCode(),
                a.getErrorMessage(),
                a.getSubmittedAt(),
                a.getScoredAt());
    }
}
