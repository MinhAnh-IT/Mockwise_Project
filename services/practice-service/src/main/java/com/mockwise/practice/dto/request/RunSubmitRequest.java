package com.mockwise.practice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /problems/{id}/run} and {@code /submit}. {@code userId}
 * and {@code problemId} are never in the body — they come from the token and
 * the path. Code size is bounded again in the service against the configured
 * byte limit (this length check is a cheap first guard).
 */
public record RunSubmitRequest(
        @NotBlank(message = "language is required")
        @Size(max = 20, message = "language must be at most 20 characters")
        String language,

        @NotBlank(message = "code is required")
        String code
) {}
