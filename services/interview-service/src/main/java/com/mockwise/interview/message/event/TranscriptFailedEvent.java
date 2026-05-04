package com.mockwise.interview.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Audio extraction / STT failure. The orchestrator flips the answer to
 * FAILED with the upstream error code so the FE can render
 * "couldn't process your video — please re-record".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptFailedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String answerId,
        String storageObjectId,
        String sessionId,
        String questionId,
        String errorCode,
        String errorMessage
) {}
