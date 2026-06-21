package com.mockwise.userprofile.dto.request;

import com.mockwise.userprofile.entity.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackStatusUpdateRequest(
        @NotNull(message = "Status is required")
        FeedbackStatus status,

        @Size(max = 1000, message = "Admin note must not exceed 1000 characters")
        String adminNote
) { }
