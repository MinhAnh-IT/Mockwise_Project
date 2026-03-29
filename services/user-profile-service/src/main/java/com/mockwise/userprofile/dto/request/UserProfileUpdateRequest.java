package com.mockwise.userprofile.dto.request;

import jakarta.validation.constraints.Min;

public record UserProfileUpdateRequest(
        String fullName,
        String trackId,
        String levelId,
        String city,

        @Min(value = 0, message = "Experience must be >= 0")
        Integer experience
) {}
