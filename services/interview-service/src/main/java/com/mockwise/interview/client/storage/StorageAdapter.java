package com.mockwise.interview.client.storage;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.storage.dto.InternalVideoDownloadUrlRequest;
import com.mockwise.interview.client.storage.dto.PresignedUrlResponse;
import com.mockwise.interview.client.storage.dto.StorageObjectResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageAdapter {

    /**
     * Same-origin path the FE hits to stream a question-audio clip through
     * storage-service. Replaces the previous MinIO presigned URL — that one
     * pointed at HTTP-only MinIO, which the browser blocks as Mixed Content
     * when the app is served over HTTPS. The path lives in api-gateway's
     * PUBLIC_ROUTES so {@code <audio src>} works without a JWT round-trip.
     */
    private static final String QUESTION_AUDIO_PATH_PREFIX = "/api/v1/storage/question-audio/";

    StorageClient client;

    public StorageObjectResponse getObject(UUID objectId) {
        return unwrap(client.getObject(objectId));
    }

    /**
     * Builds the URL the FE should hit to play a question's TTS audio.
     * Returns empty when {@code objectKey} is missing — the player UI
     * silently degrades to text-only.
     *
     * <p>The previous implementation asked storage-service to presign a
     * MinIO URL for direct browser fetch. That broke under HTTPS apps
     * because MinIO is HTTP-only (Mixed Content blocking). The new URL
     * is same-origin and resolves through nginx → api-gateway →
     * storage-service, which streams the bytes from MinIO server-side.
     */
    public Optional<String> signQuestionAudioUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(QUESTION_AUDIO_PATH_PREFIX + objectKey);
    }

    /**
     * Short-lived presigned MinIO URL for replaying the candidate's submitted
     * answer video. Storage-service signs the URL through nginx's /minio/
     * proxy so the browser streams the bytes straight from MinIO instead of
     * round-tripping through storage-service.
     *
     * <p>Soft-fails (returns empty) if the call to storage-service breaks —
     * the report still renders, just without the playback link, and the FE
     * surfaces a retry. Ownership is enforced server-side; the caller passes
     * the user id it has already authenticated for the session.
     */
    public Optional<String> signInterviewVideoUrl(UUID storageObjectId, String requesterUserId) {
        if (storageObjectId == null || requesterUserId == null || requesterUserId.isBlank()) {
            return Optional.empty();
        }
        try {
            PresignedUrlResponse resp = unwrap(client.createVideoDownloadUrlById(
                    storageObjectId,
                    new InternalVideoDownloadUrlRequest(requesterUserId, /* ttlSeconds */ null)));
            return resp == null || resp.url() == null || resp.url().isBlank()
                    ? Optional.empty()
                    : Optional.of(resp.url());
        } catch (RuntimeException e) {
            // 404 / 5xx — fall back to no URL rather than failing the whole
            // session view. Logged at INFO because a missing video on the
            // report is annoying but not actionable in real time.
            log.info("Could not sign video URL for object {}: {}", storageObjectId, e.getMessage());
            return Optional.empty();
        }
    }

    private static <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new BusinessException(StatusCode.STORAGE_UNAVAILABLE);
        }
        if (!response.isSuccess()) {
            throw new BusinessException(
                    response.getCode(),
                    response.getMessage() != null ? response.getMessage() : "storage call failed",
                    StatusCode.STORAGE_UNAVAILABLE.getHttpStatus());
        }
        return response.getData();
    }
}
