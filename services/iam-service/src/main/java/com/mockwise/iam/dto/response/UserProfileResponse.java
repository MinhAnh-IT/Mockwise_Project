package com.mockwise.iam.dto.response;

import com.mockwise.iam.dto.common.Language;

import java.util.List;

public record UserProfileResponse(
        String userId,
        String fullName,
        PositionResponse position,
        Integer experience,
        List<String> techStack,
        Language preferredLanguage,
        Integer yearsInCurrentRole,
        List<String> industries
) { }
