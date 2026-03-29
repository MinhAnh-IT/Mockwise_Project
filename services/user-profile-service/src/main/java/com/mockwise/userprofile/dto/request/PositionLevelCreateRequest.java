package com.mockwise.userprofile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PositionLevelCreateRequest(
        @NotBlank(message = "Position role is required")
        @Size(max = 64, message = "Position role must not exceed 64 characters")
        String positionRole
) { }
