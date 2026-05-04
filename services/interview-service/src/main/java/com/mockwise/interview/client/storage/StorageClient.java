package com.mockwise.interview.client.storage;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.storage.config.StorageFeignConfig;
import com.mockwise.interview.client.storage.dto.StorageObjectResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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
}
