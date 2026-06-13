package com.mockwise.iam.service.oauth;

import com.mockwise.iam.common.config.OAuthProperties;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.util.StatusCode;
import com.mockwise.iam.dto.internal.OAuthUserInfo;
import com.mockwise.iam.enums.AuthProvider;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * GitHub OAuth: exchanges the code for an opaque access token, then reads
 * {@code /user}. GitHub does not include {@code email_verified} on the profile
 * and the public email may be hidden, so we pull the primary verified address
 * from {@code /user/emails} ({@code user:email} scope required).
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GithubOAuthClient implements OAuthProviderClient {

    static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    static final String USER_URL = "https://api.github.com/user";
    static final String EMAILS_URL = "https://api.github.com/user/emails";

    OAuthProperties props;
    RestClient rest;

    public GithubOAuthClient(OAuthProperties props) {
        this.props = props;
        this.rest = RestClient.create();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GITHUB;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo fetchUser(String code, String redirectUri) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code);
            form.add("client_id", props.getGithub().getClientId());
            form.add("client_secret", props.getGithub().getClientSecret());
            form.add("redirect_uri", redirectUri);

            Map<String, Object> token = rest.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            String accessToken = token == null ? null : (String) token.get("access_token");
            if (accessToken == null) {
                throw new BusinessException(StatusCode.OAUTH_EXCHANGE_FAILED);
            }

            Map<String, Object> user = rest.get()
                    .uri(USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(Map.class);

            if (user == null || user.get("id") == null) {
                throw new BusinessException(StatusCode.OAUTH_EXCHANGE_FAILED);
            }

            VerifiedEmail email = primaryVerifiedEmail(accessToken);

            return OAuthUserInfo.builder()
                    .providerId(String.valueOf(user.get("id")))
                    .email(email == null ? null : email.address())
                    .emailVerified(email != null && email.verified())
                    .name((String) user.get("name"))
                    .picture((String) user.get("avatar_url"))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("GitHub OAuth exchange failed: {}", e.getMessage());
            throw new BusinessException(StatusCode.OAUTH_EXCHANGE_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private VerifiedEmail primaryVerifiedEmail(String accessToken) {
        List<Map<String, Object>> emails = rest.get()
                .uri(EMAILS_URL)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(List.class);

        if (emails == null) return null;

        // Prefer the primary address; fall back to the first verified one.
        VerifiedEmail fallback = null;
        for (Map<String, Object> e : emails) {
            boolean verified = Boolean.TRUE.equals(e.get("verified"));
            boolean primary = Boolean.TRUE.equals(e.get("primary"));
            String address = (String) e.get("email");
            if (!verified || address == null) continue;
            if (primary) return new VerifiedEmail(address, true);
            if (fallback == null) fallback = new VerifiedEmail(address, true);
        }
        return fallback;
    }

    private record VerifiedEmail(String address, boolean verified) {}
}
