package com.mockwise.iam.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @Valid
        @NotNull(message = "Account must not be null")
        AccountRequest account,

        @Valid
        @NotNull(message = "Profile must not be null")
        ProfileDraftRequest profile
) {
}
