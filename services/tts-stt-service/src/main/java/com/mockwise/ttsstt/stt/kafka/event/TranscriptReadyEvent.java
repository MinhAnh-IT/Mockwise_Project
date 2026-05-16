package com.mockwise.ttsstt.stt.kafka.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TranscriptReadyEvent {
    String eventId;
    String eventType;
    OffsetDateTime occurredAt;
    String transcriptId;
    String sttJobId;
    String answerId;
    String storageObjectId;
    String sessionId;
    String questionId;
    String ownerUserId;
    String languageCode;
    Integer durationMs;
    Integer wordCount;
    // Full transcript text carried inline so the interview-service
    // consumer doesn't have to round-trip back here via
    // GET /internal/transcripts/{id}. Typical size 200-2000 chars
    // (~1-4KB), well under Kafka's 1MB default max.message.bytes.
    String transcriptText;
}
