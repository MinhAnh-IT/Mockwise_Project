package com.mockwise.interview.client.storage;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.storage.dto.InternalDownloadUrlRequest;
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

    /** Server caps QUESTION_AUDIO at 60s; ask for that. */
    private static final int QUESTION_AUDIO_TTL_SECONDS = 60;

    StorageClient client;

    public StorageObjectResponse getObject(UUID objectId) {
        return unwrap(client.getObject(objectId));
    }

    /**
     * Soft-failing audio URL signing. The FE flow renders the question
     * even if audio is missing — a transient storage outage shouldn't
     * stop a candidate mid-session. Returns empty when storage is down
     * or the key is unknown.
     */
    public Optional<String> signQuestionAudioUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return Optional.empty();
        }
        try {
            ApiResponse<PresignedUrlResponse> response = client.createDownloadUrl(
                    new InternalDownloadUrlRequest("QUESTION_AUDIO", objectKey, QUESTION_AUDIO_TTL_SECONDS));
            PresignedUrlResponse body = unwrap(response);
            return Optional.ofNullable(body.url());
        } catch (Exception ex) {
            log.warn("Audio URL signing failed for objectKey={}: {}", objectKey, ex.getMessage());
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
