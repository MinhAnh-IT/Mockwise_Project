package com.mockwise.userprofile.dto.response;

import com.mockwise.userprofile.entity.Language;

import java.time.Instant;
import java.util.List;

public record AdminUserProfileResponse(
        String userId,
        String fullName,
        String email,
        Boolean isVerified,
        Boolean blocked,
        Instant lastLoginAt,
        PositionResponse position,
        Integer experience,
        Instant createdAt,
        List<String> techStack,
        Language preferredLanguage,
        Integer yearsInCurrentRole,
        List<String> industries
) { }
