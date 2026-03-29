package com.mockwise.iam.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountRequest(
        @Email
        String email,
        @NotBlank(message = "Password must not be blank")
        String password
) {}
