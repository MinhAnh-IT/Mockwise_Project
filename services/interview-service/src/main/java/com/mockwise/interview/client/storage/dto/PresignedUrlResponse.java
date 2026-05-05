package com.mockwise.interview.client.storage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PresignedUrlResponse(
        String url,
        OffsetDateTime expiresAt
) {}
