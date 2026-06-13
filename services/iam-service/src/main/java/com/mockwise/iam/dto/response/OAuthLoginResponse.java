package com.mockwise.iam.dto.response;

import lombok.Builder;

/**
 * Result of a social-login exchange. {@code accessToken} is our own JWT (the
 * refresh token is set as an HttpOnly cookie, same as password login).
 * {@code profileCompleted=false} tells the SPA to route the user to the
 * "complete your profile" page before entering the app.
 */
@Builder
public record OAuthLoginResponse(
        String accessToken,
        boolean profileCompleted
) {}
