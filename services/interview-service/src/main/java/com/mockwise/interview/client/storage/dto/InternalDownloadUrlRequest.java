package com.mockwise.interview.client.storage.dto;

/**
 * Mirror of storage-service's {@code InternalDownloadUrlRequest}. Sent
 * by the orchestrator when it needs a short-lived presigned GET URL —
 * currently only for question audio playback at session start / when
 * pinning a follow-up.
 *
 * <p>{@code kind} must be a name from storage's {@code StorageKind} enum
 * ({@code QUESTION_AUDIO}, {@code INTERVIEW_VIDEO}, {@code USER_AVATAR}).
 * {@code ttlSeconds} is capped server-side per kind, so a wildly large
 * value here just lands at the cap rather than failing the call.
 */
public record InternalDownloadUrlRequest(
        String kind,
        String objectKey,
        Integer ttlSeconds
) {}
