package com.mockwise.iam.service.oauth;

import com.mockwise.iam.dto.internal.OAuthUserInfo;
import com.mockwise.iam.enums.AuthProvider;

/**
 * Exchanges an authorization {@code code} for the user's identity at a social
 * provider. Implementations encapsulate the provider-specific token endpoint
 * and userinfo calls; the rest of the app only sees a normalized
 * {@link OAuthUserInfo}.
 */
public interface OAuthProviderClient {

    /** Which provider this client serves; used to route requests. */
    AuthProvider provider();

    /**
     * Run the full code→token→userinfo handshake.
     *
     * @param code        authorization code from the provider redirect
     * @param redirectUri the redirect URI the SPA used (providers validate it)
     */
    OAuthUserInfo fetchUser(String code, String redirectUri);
}
