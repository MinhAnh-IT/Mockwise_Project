package com.mockwise.userprofile.dto.response;

public record AdminUserProfileResponse(
        String userId,
        String fullName,
        String email,
        Boolean isVerified,
        PositionResponse position,
        String city,
        Integer experience
) { }
