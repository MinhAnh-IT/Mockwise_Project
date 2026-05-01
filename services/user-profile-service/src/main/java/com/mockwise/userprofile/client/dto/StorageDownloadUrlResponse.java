package com.mockwise.userprofile.client.dto;

import java.time.OffsetDateTime;

public record StorageDownloadUrlResponse(
        String url,
        OffsetDateTime expiresAt
) {}
