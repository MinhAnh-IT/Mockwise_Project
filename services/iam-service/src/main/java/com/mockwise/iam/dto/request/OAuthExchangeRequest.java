package com.mockwise.iam.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Sent by the SPA after the provider redirects back with an authorization
 * {@code code}. {@code redirectUri} must match the one used to obtain the code
 * (providers validate it during the token exchange).
 */
public record OAuthExchangeRequest(
        @NotBlank(message = "Authorization code must not be blank")
        String code,

        @NotBlank(message = "Redirect URI must not be blank")
        String redirectUri
) {}
