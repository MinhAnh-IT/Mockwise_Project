package com.mockwise.iam.dto.response;

public record UserProfileResponse(
        String userId,
        String fullName,
        PositionResponse position,
        String city,
        Integer experience
) { }

