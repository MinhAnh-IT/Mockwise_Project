package com.mockwise.userprofile.dto.response;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        String userId,
        String fullName,
        PositionResponse position,
        String city,
        Integer experience,
        String avatarObjectKey,
        String avatarUrl,
        OffsetDateTime avatarUrlExpiresAt
) {}
