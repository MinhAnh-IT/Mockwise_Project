package com.mockwise.interview.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * AI service couldn't finish the evaluation (LLM timeout, validation
 * error after retries, etc.). The orchestrator flips the answer to
 * FAILED — operator can replay later via {@code /internal/replay/{aid}}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationFailedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String answerId,
        String sessionId,
        String questionId,
        String error,
        String detail
) {}
