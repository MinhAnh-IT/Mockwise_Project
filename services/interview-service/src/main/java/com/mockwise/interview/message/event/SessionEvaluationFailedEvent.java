package com.mockwise.interview.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of the AI service's {@code SessionEvaluationFailedEvent}.
 * The orchestrator records the error in
 * {@code session.metadata.overallReviewError} and leaves the session in
 * COMPLETED so an operator can clear the dedup flag and replay.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionEvaluationFailedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String sessionId,
        String error,
        String detail
) {}
