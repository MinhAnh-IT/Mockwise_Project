package com.mockwise.userprofile.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record IamUserResponse(
        String userId,
        String email,
        Boolean isVerified,
        Boolean blocked,
        Instant lastLoginAt
) {
}

