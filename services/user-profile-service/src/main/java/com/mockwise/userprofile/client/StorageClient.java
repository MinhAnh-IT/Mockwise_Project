package com.mockwise.userprofile.client;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.client.config.StorageFeignConfig;
import com.mockwise.userprofile.client.dto.StorageDownloadUrlRequest;
import com.mockwise.userprofile.client.dto.StorageDownloadUrlResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal call into storage-service to mint short-lived presigned GET URLs
 * for user-owned objects (currently just avatars).
 *
 * <p>The storage-service `/internal/**` endpoints are gated by an {@code
 * X-Internal-Auth} shared-secret header — the api-gateway strips that header
 * from any public request, so only callers on the internal docker network can
 * reach this. The header is injected by {@link StorageFeignConfig}.
 */
@FeignClient(
        name = "storageClient",
        url = "${mockwise.storage.base-url:http://storage-service:8085/api/v1/storage}",
        configuration = StorageFeignConfig.class
)
public interface StorageClient {

    @PostMapping("/internal/download-url")
    ApiResponse<StorageDownloadUrlResponse> createDownloadUrl(
            @RequestBody StorageDownloadUrlRequest request);
}
