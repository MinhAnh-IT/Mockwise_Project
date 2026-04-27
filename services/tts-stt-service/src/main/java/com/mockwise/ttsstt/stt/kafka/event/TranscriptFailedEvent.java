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
public class TranscriptFailedEvent {
    String eventId;
    String eventType;
    OffsetDateTime occurredAt;
    String sttJobId;
    String answerId;
    String storageObjectId;
    String sessionId;
    String questionId;
    String errorCode;
    String errorMessage;
    int attemptCount;
}
