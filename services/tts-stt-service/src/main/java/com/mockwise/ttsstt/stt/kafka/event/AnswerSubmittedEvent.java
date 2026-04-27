package com.mockwise.ttsstt.stt.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

/**
 * Event published by interview-service when a user submits a video answer.
 * Used as the trigger for STT processing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnswerSubmittedEvent {

    String eventId;
    String eventType;
    OffsetDateTime occurredAt;
    String answerId;
    String sessionId;
    String questionId;
    String ownerUserId;
    String storageObjectId;
    VideoMeta videoMeta;
    String languageHint;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoMeta {
        String bucket;
        String objectKey;
        String contentType;
        Long sizeBytes;
    }
}
