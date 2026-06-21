package com.mockwise.userprofile.dto.request;

import com.mockwise.userprofile.entity.FeedbackCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackCreateRequest(
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        Integer rating,

        @NotNull(message = "Category is required")
        FeedbackCategory category,

        @NotBlank(message = "Content is required")
        @Size(max = 2000, message = "Content must not exceed 2000 characters")
        String content,

        @Email(message = "Contact email is invalid")
        @Size(max = 320, message = "Contact email must not exceed 320 characters")
        String contactEmail
) { }
