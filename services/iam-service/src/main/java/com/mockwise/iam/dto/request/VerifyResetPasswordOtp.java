package com.mockwise.iam.dto.request;

import jakarta.validation.constraints.*;

public record VerifyResetPasswordOtp(
        @Email
        String email,
        @NotBlank(message = "Otp must not be blank")
        @Size(min = 6, max = 6, message = "Otp must be exactly 6 characters")
        String otp,
        @NotBlank(message = "Password must not be blank")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).{8,}$",
                message = "Password must be at least 8 characters long and include uppercase, lowercase, and special characters"
        )
        @Size(max = 72, message = "Password must not exceed 72 characters")
        String newPassword
){ }
