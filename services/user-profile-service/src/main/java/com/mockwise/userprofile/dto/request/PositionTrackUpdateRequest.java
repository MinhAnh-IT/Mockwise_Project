package com.mockwise.userprofile.dto.request;

import jakarta.validation.constraints.Size;

public record PositionTrackUpdateRequest(
        @Size(max = 100, message = "Track name must not exceed 100 characters")
        String name,
        Boolean active
) { }
