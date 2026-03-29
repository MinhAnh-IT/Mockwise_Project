package com.mockwise.iam.dto.internal;

import lombok.Builder;

import java.time.Instant;

@Builder
public record RefreshTokenDto (
        String id,
        String sessionId,
        String userId,
        String tokenHash,
        Instant issuedAt,
        Instant expiresAt
){
}
