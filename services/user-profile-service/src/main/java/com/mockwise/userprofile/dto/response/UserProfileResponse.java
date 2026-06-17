package com.mockwise.userprofile.dto.response;

import com.mockwise.userprofile.entity.Language;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public record UserProfileResponse(
        String userId,
        String fullName,
        PositionResponse position,
        Integer experience,
        Instant createdAt,
        String avatarUrl,
        OffsetDateTime avatarUrlExpiresAt,
        List<String> techStack,
        Language preferredLanguage,
        Integer yearsInCurrentRole,
        List<String> industries
) {}
