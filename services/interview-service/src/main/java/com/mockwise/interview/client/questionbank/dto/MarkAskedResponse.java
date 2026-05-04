package com.mockwise.interview.client.questionbank.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarkAskedResponse(
        String id,
        long askCount,
        OffsetDateTime lastAskedAt
) {}
