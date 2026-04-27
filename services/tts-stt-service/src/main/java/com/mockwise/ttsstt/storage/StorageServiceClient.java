package com.mockwise.ttsstt.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin wrapper around storage-service's internal endpoints.
 *
 * <p>Used for two flows:
 * <ul>
 *   <li>Upload TTS-generated audio ({@code POST /internal/question-audio}).</li>
 *   <li>Request a presigned GET URL for an interview video ({@code POST /internal/download-url}).</li>
 * </ul>
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StorageServiceClient {

    RestClient client;

    public StorageServiceClient(@Qualifier("storageRestClient") RestClient client) {
        this.client = client;
    }

    public UploadedQuestionAudio uploadQuestionAudio(String questionId, byte[] audioBytes, String contentType) {
        ByteArrayResource fileResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "question-audio.mp3";
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("questionId", questionId);
        form.add("file", fileResource);

        try {
            JsonNode resp = client.post()
                    .uri("/internal/question-audio")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (resp == null || !resp.has("data")) {
                throw new BusinessException(StatusCode.STORAGE_UNREACHABLE);
            }
            JsonNode data = resp.get("data");
            return UploadedQuestionAudio.builder()
                    .objectId(data.path("objectId").asText())
                    .bucket(data.path("bucket").asText())
                    .objectKey(data.path("objectKey").asText())
                    .sizeBytes(data.path("sizeBytes").asLong())
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("storage-service unreachable for upload", ex);
            throw new BusinessException(StatusCode.STORAGE_UNREACHABLE);
        } catch (Exception ex) {
            log.error("storage-service upload failed", ex);
            throw new BusinessException(StatusCode.STORAGE_UNREACHABLE);
        }
    }

    public PresignedDownload requestDownloadUrl(String kind, String objectKey, int ttlSeconds) {
        Map<String, Object> body = Map.of(
                "kind", kind,
                "objectKey", objectKey,
                "ttlSeconds", ttlSeconds);
        try {
            JsonNode resp = client.post()
                    .uri("/internal/download-url")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (resp == null || !resp.has("data")) {
                throw new BusinessException(StatusCode.STORAGE_UNREACHABLE);
            }
            JsonNode data = resp.get("data");
            return PresignedDownload.builder()
                    .url(data.path("url").asText())
                    .expiresAt(data.path("expiresAt").asText())
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("storage-service unreachable for download URL", ex);
            throw new BusinessException(StatusCode.STORAGE_UNREACHABLE);
        } catch (Exception ex) {
            log.error("storage-service download-url failed", ex);
            throw new BusinessException(StatusCode.STORAGE_UNREACHABLE);
        }
    }
}
