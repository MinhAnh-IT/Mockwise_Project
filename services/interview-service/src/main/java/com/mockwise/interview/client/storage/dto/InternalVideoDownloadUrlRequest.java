package com.mockwise.interview.client.storage.dto;

/**
 * Mirror of storage-service's {@code InternalVideoDownloadUrlRequest}.
 * The orchestrator passes the user id it has already authenticated for
 * the session; storage-service verifies it matches the row's
 * {@code ownerUserId}.
 *
 * <p>{@code ttlSeconds} is capped server-side, so an out-of-range value
 * is silently clamped instead of failing the call.
 */
public record InternalVideoDownloadUrlRequest(
        String ownerUserId,
        Integer ttlSeconds
) {}
