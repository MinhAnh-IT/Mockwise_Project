package com.mockwise.storage.dto.response;

import io.minio.GetObjectResponse;

/**
 * Container the controller pipes straight to the HTTP response. Holding the
 * MinIO {@link GetObjectResponse} (an InputStream) keeps memory flat — bytes
 * stream from MinIO → storage-service → client without buffering.
 */
public record AvatarStream(
        GetObjectResponse stream,
        String contentType,
        long sizeBytes
) {}
