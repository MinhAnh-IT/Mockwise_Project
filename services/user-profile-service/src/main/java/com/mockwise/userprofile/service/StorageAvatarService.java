package com.mockwise.userprofile.service;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.client.StorageClient;
import com.mockwise.userprofile.client.dto.StorageDownloadUrlRequest;
import com.mockwise.userprofile.client.dto.StorageDownloadUrlResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Mints short-lived presigned URLs for user avatars.
 *
 * <p>Storage failures are intentionally swallowed (logged + return empty)
 * so a transient storage-service hiccup never breaks the whole profile fetch:
 * the client just gets a profile without an avatar URL and falls back to the
 * initials placeholder.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageAvatarService {

    private static final String KIND_USER_AVATAR = "USER_AVATAR";

    StorageClient storageClient;

    // @FieldDefaults(makeFinal=true) would otherwise put this in the
    // @RequiredArgsConstructor — Spring can't autowire a primitive int that way.
    // @NonFinal keeps it non-final → field-injected from @Value.
    @NonFinal
    @Value("${mockwise.storage.avatar-ttl-seconds:600}")
    int avatarTtlSeconds;

    public Optional<StorageDownloadUrlResponse> mintAvatarUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return Optional.empty();
        try {
            ApiResponse<StorageDownloadUrlResponse> resp = storageClient.createDownloadUrl(
                    new StorageDownloadUrlRequest(KIND_USER_AVATAR, objectKey, avatarTtlSeconds));
            return Optional.ofNullable(resp.getData());
        } catch (Exception e) {
            log.warn("Failed to mint avatar URL for objectKey={}: {}", objectKey, e.getMessage());
            return Optional.empty();
        }
    }
}
