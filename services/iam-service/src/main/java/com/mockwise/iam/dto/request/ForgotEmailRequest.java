package com.mockwise.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotEmailRequest (
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email
){}
