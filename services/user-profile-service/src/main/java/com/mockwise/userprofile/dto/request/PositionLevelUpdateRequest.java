package com.mockwise.userprofile.dto.request;

import jakarta.validation.constraints.Size;

public record PositionLevelUpdateRequest(
        @Size(max = 64, message = "Position role must not exceed 64 characters")
        String positionRole,
        Boolean active
) { }
