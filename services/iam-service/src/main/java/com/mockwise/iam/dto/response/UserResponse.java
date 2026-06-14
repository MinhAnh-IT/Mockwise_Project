package com.mockwise.iam.dto.response;

import com.mockwise.iam.enums.Role;

import java.time.Instant;

public record UserResponse(
        String userId,
        String email,
        Role role,
        boolean isVerified,
        boolean blocked,
        Instant lastLoginAt
) {}
