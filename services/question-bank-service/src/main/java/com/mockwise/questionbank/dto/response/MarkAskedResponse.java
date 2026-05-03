package com.mockwise.questionbank.dto.response;

import java.time.OffsetDateTime;

public record MarkAskedResponse(
        String id,
        long askCount,
        OffsetDateTime lastAskedAt
) {}
