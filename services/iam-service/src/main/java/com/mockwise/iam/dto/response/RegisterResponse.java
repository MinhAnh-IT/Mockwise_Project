package com.mockwise.iam.dto.response;

public record RegisterResponse(
        UserResponse user,
        UserProfileResponse profile
) { }
