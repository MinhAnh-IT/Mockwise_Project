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

import java.util.Map;

/**
 * Google OAuth 2.0: exchanges the code at {@code oauth2.googleapis.com/token},
 * then reads the OpenID userinfo endpoint. Google returns {@code email_verified}
 * directly so no extra email call is needed.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GoogleOAuthClient implements OAuthProviderClient {

    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    OAuthProperties props;
    RestClient rest;

    public GoogleOAuthClient(OAuthProperties props) {
        this.props = props;
        this.rest = RestClient.create();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo fetchUser(String code, String redirectUri) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("code", code);
            form.add("client_id", props.getGoogle().getClientId());
            form.add("client_secret", props.getGoogle().getClientSecret());
            form.add("redirect_uri", redirectUri);
            form.add("grant_type", "authorization_code");

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

            Map<String, Object> info = rest.get()
                    .uri(USERINFO_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);

            if (info == null || info.get("email") == null) {
                throw new BusinessException(StatusCode.OAUTH_EXCHANGE_FAILED);
            }

            return OAuthUserInfo.builder()
                    .providerId((String) info.get("sub"))
                    .email((String) info.get("email"))
                    .emailVerified(Boolean.TRUE.equals(info.get("email_verified")))
                    .name((String) info.get("name"))
                    .picture((String) info.get("picture"))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google OAuth exchange failed: {}", e.getMessage());
            throw new BusinessException(StatusCode.OAUTH_EXCHANGE_FAILED);
        }
    }
}
