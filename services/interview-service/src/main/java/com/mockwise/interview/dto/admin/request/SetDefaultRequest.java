package com.mockwise.interview.dto.admin.request;

import jakarta.validation.constraints.NotNull;

public record SetDefaultRequest(
        @NotNull(message = "isDefault is required")
        Boolean isDefault
) {}
