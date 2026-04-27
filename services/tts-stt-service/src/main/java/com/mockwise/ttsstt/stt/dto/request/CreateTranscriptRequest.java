package com.mockwise.ttsstt.stt.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Fallback / replay endpoint payload — used when interview-service wants to
 * trigger STT outside the Kafka path (admin tooling, backfill).
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateTranscriptRequest {

    @NotBlank
    String storageObjectId;

    @NotBlank
    String answerId;

    @NotBlank
    String sessionId;

    @NotBlank
    String questionId;

    @NotBlank
    String ownerUserId;

    String objectKey;

    String contentType;

    Long sizeBytes;

    String languageCode;

    boolean force;
}
