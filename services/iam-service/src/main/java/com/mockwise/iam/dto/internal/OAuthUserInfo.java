package com.mockwise.iam.dto.internal;

import lombok.Builder;

/**
 * Normalized identity returned by a social provider after the token exchange.
 * {@code emailVerified} must be true before we trust the email for account
 * lookup/linking (guards against account takeover via unverified emails).
 */
@Builder
public record OAuthUserInfo(
        String providerId,
        String email,
        boolean emailVerified,
        String name,
        String picture
) {}
