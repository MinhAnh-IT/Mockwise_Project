package com.mockwise.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mockwise.interview.enums.AnswerStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Result of submitting one answer.
 *
 * <p>{@code nextQuestion} is populated only for CODING (non-adaptive): the next
 * problem is pinned synchronously at submit time, so we return it (redacted) and
 * the FE advances immediately instead of polling. For adaptive (VIDEO) answers
 * the next question is gated on async AI scoring, so it stays null and the FE
 * keeps polling {@code GET /sessions/{id}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmitAnswerOutput(
        UUID answerId,
        AnswerStatus status,
        OffsetDateTime submittedAt,
        PinnedQuestionView nextQuestion
) {}
