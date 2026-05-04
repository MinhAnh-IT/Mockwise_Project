package com.mockwise.interview.client.storage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of storage-service's {@code StorageObjectResponse}. The
 * orchestrator uses this to verify ownership / kind / status before
 * pinning a storage object id to an answer.
 *
 * <p>Note: the matching internal endpoint
 * ({@code GET /internal/objects/{id}}) is not yet implemented in
 * storage-service — see the gap list. This DTO is the contract we will
 * call once the endpoint lands.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StorageObjectResponse(
        String id,
        String ownerUserId,
        String kind,            // INTERVIEW_VIDEO | QUESTION_AUDIO | USER_AVATAR
        String status,          // PENDING | READY | FAILED
        String bucket,
        String objectKey,
        String contentType,
        Long sizeBytes
) {}
