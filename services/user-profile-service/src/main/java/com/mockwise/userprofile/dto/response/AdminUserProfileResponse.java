package com.mockwise.userprofile.dto.response;

import com.mockwise.userprofile.entity.Language;

import java.util.List;

public record AdminUserProfileResponse(
        String userId,
        String fullName,
        String email,
        Boolean isVerified,
        Boolean blocked,
        PositionResponse position,
        String city,
        Integer experience,
        List<String> techStack,
        Language preferredLanguage,
        Integer yearsInCurrentRole,
        List<String> industries
) { }
