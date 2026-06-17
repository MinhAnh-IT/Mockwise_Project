package com.mockwise.userprofile.dto.request;


import com.mockwise.userprofile.entity.Language;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserProfileRequest(
        @NotBlank(message = "Full name must not be blank")
        String fullName,

        @NotBlank(message = "Track ID must not be blank")
        String trackId,

        @NotBlank(message = "Level ID must not be blank")
        String levelId,

        @NotNull(message = "Experience is required")
        @Min(value = 0, message = "Experience must be >= 0")
        Integer experience,

        @Size(max = 20, message = "Tech stack must have at most 20 items")
        List<String> techStack,

        Language preferredLanguage,

        @Min(value = 0, message = "Years in current role must be >= 0")
        Integer yearsInCurrentRole,

        @Size(max = 10, message = "Industries must have at most 10 items")
        List<String> industries
) { }
