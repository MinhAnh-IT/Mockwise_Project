package com.mockwise.interview.client.storage;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.storage.config.StorageFeignConfig;
import com.mockwise.interview.client.storage.dto.InternalDownloadUrlRequest;
import com.mockwise.interview.client.storage.dto.InternalVideoDownloadUrlRequest;
import com.mockwise.interview.client.storage.dto.PresignedUrlResponse;
import com.mockwise.interview.client.storage.dto.StorageObjectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Feign client for storage-service's internal endpoints. Used by the
 * orchestrator to verify a {@code storageObjectId} that came from the
 * frontend before treating it as a real answer pointer.
 */
@FeignClient(
        name = "storage-service",
        url = "${external.services.storage.url}",
        configuration = StorageFeignConfig.class
)
public interface StorageClient {

    /**
     * Look up a storage object by id. Required before
     * {@code POST /interviews/{sid}/questions/{qid}/answers} pins the id
     * to a new answer record — checks ownership, kind, and READY status.
     */
    @GetMapping(
            value = "/internal/objects/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<StorageObjectResponse> getObject(@PathVariable("id") UUID objectId);

    /**
     * Short-lived GET URL for an audio object. Called when assembling
     * a {@link com.mockwise.interview.dto.response.PinnedQuestionView}
     * so the FE can play the question audio without storage credentials.
     * The TTL is server-capped per kind (60s for question audio).
     */
    @PostMapping(
            value = "/internal/download-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<PresignedUrlResponse> createDownloadUrl(@RequestBody InternalDownloadUrlRequest request);

    /**
     * Short-lived presigned GET URL for an interview-video by storage object
     * id. Replaces the legacy "stream through this service" path so the
     * browser can hit MinIO directly through the nginx /minio/ proxy. The
     * orchestrator passes the session owner's user id so storage-service can
     * enforce ownership defence-in-depth.
     */
    @PostMapping(
            value = "/internal/objects/{id}/video-download-url",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<PresignedUrlResponse> createVideoDownloadUrlById(
            @PathVariable("id") UUID objectId,
            @RequestBody InternalVideoDownloadUrlRequest request);
}
