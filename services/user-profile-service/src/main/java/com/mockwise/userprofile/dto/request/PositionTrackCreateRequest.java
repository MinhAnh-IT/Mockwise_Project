package com.mockwise.userprofile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PositionTrackCreateRequest(
        @NotBlank(message = "Track name is required")
        @Size(max = 100, message = "Track name must not exceed 100 characters")
        String name
) { }
