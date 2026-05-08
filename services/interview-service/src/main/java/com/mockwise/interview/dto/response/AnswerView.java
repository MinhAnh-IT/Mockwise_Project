package com.mockwise.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mockwise.interview.entity.Answer;
import com.mockwise.interview.enums.AnswerStatus;
import com.mockwise.interview.enums.AnswerType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Single-answer projection for the FE poll endpoint
 * {@code GET /interviews/{sid}/answers/{aid}}. Carries enough state for
 * the UI to render "scoring…" / "ready" / "failed" plus the verdict
 * + feedback once SCORED.
 *
 * <p>{@code mediaUrl} is a same-origin path to the candidate's submitted
 * video, populated only when results are revealed (session SCORED) and
 * the answer carries a storage object id. The path is JWT-protected
 * end-to-end — see storage-service's {@code InterviewVideoController}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        OffsetDateTime scoredAt,
        UUID storageObjectId,
        String mediaUrl
) {

    /**
     * Masks score / feedback / verdict / rubricScores when {@code revealResults}
     * is false — used while the session is still in progress so the candidate
     * cannot peek at per-question grading. Status, errorCode, errorMessage and
     * the timestamps stay exposed so the FE can still render
     * "submitted → scoring → done" transitions between questions. Once the
     * session reaches SCORED the controller calls this with
     * {@code revealResults = true}.
     *
     * <p>{@code mediaUrl} stays null here — the controller calls
     * {@link #withSignedMedia} after the fact so {@code AnswerView} stays
     * decoupled from the storage adapter.
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
                a.getScoredAt(),
                revealResults ? a.getStorageObjectId() : null,
                /* mediaUrl */ null);
    }

    /**
     * Returns a copy with {@code mediaUrl} filled in. {@code signer} takes
     * a {@code storageObjectId} and yields a same-origin URL the FE can drop
     * into a {@code <video src>}. Caller is expected to only invoke this when
     * results are revealed; we still soft-fail if the answer has no storage
     * object (CODE answers, or VIDEO answers that never reached READY).
     */
    public AnswerView withSignedMedia(Function<UUID, String> signer) {
        if (storageObjectId == null || signer == null) {
            return this;
        }
        String url = signer.apply(storageObjectId);
        if (url == null || url.isBlank()) {
            return this;
        }
        return new AnswerView(
                answerId, sessionId, sessionQuestionId, type, status,
                score, maxScore, feedback, verdict, rubricScores,
                errorCode, errorMessage, submittedAt, scoredAt,
                storageObjectId, url);
    }
}
