package com.mockwise.userprofile.client.dto;

public record StorageDownloadUrlRequest(
        String kind,
        String objectKey,
        int ttlSeconds
) {}
