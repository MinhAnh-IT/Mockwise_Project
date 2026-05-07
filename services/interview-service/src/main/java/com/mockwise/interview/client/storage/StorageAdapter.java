package com.mockwise.interview.client.storage;

import com.core.apiresponse.response.ApiResponse;
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
