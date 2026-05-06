package com.mockwise.interview.client.storage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of storage-service's {@code StorageObjectResponse}. Field names
 * match the upstream emit shape exactly ({@code objectId}, not {@code id};
 * {@code status} as the enum's name string {@code READY}/{@code PENDING_UPLOAD}/
 * {@code FAILED}; {@code kind} as {@code INTERVIEW_VIDEO}/{@code QUESTION_AUDIO}/
 * {@code USER_AVATAR}).
 *
 * <p>The orchestrator uses this to verify ownership / kind / READY status
 * before pinning a storage object id to an answer (see
 * {@code AnswerService.verifyStorageObject}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StorageObjectResponse(
        String objectId,
        String ownerUserId,
        String kind,
        String status,
        String bucket,
        String objectKey,
        String contentType,
        Long sizeBytes,
        String sessionId,
        String questionId
) {}
