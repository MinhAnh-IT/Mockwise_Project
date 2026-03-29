package com.mockwise.iam.dto.response;

import com.mockwise.iam.enums.Role;

public record UserResponse(
        String userId,
        String email,
        Role role,
        boolean isVerified
) {}
