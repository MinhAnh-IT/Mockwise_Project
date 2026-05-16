package com.mockwise.interview.message.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of tts-stt-service's {@code TranscriptReadyEvent}. Field order
 * + names must match the producer side byte-for-byte; using a Java
 * record + {@code @JsonIgnoreProperties} keeps the contract loose
 * enough that the upstream can keep adding fields without forcing a
 * coordinated deploy here.
 *
 * <p>{@code occurredAt} comes through as a string (ISO-8601) because
 * the producer disables {@code WRITE_DATES_AS_TIMESTAMPS}; we keep it
 * as String here and let downstream code parse if needed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscriptReadyEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String transcriptId,
        String sttJobId,
        String answerId,
        String storageObjectId,
        String sessionId,
        String questionId,
        String ownerUserId,
        String languageCode,
        Integer durationMs,
        Integer wordCount,
        // Inline transcript text (added on the producer side) so we can
        // skip the GET /internal/transcripts/{id} round-trip. Nullable:
        // events replayed from before this field existed won't carry it,
        // in which case AnswerService falls back to the REST fetch.
        String transcriptText
) {}
