package com.mockwise.iam.service;

import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.util.StatusCode;
import com.mockwise.iam.dto.internal.OAuthUserInfo;
import com.mockwise.iam.dto.request.OAuthExchangeRequest;
import com.mockwise.iam.dto.response.OAuthLoginResponse;
import com.mockwise.iam.entity.User;
import com.mockwise.iam.enums.AuthProvider;
import com.mockwise.iam.enums.Role;
import com.mockwise.iam.repository.jpa.UserRepository;
import com.mockwise.iam.service.oauth.OAuthProviderClient;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Social-login (Google/GitHub) sign-in. Reuses the existing JWT/refresh-cookie
 * machinery via {@link AuthService#issueSession}; only the user resolution +
 * account-linking policy is new.
 *
 * <p>Linking policy (the provider email must be verified):
 * <ul>
 *   <li><b>No account</b> → create a passwordless account, verified, profile incomplete.</li>
 *   <li><b>Verified account exists</b> (TH1) → link the provider; the existing password
 *       (if any) is kept so the user can sign in either way.</li>
 *   <li><b>Unverified account exists</b> (TH2) → take it over for the real email owner:
 *       mark verified, drop the password and bump tokenVersion (defeats pre-account
 *       hijacking — a password set by a squatter is invalidated).</li>
 * </ul>
 */
@Slf4j
@Service
public class OAuthService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final Map<AuthProvider, OAuthProviderClient> clients;

    public OAuthService(UserRepository userRepository,
                        AuthService authService,
                        List<OAuthProviderClient> providerClients) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.clients = new EnumMap<>(AuthProvider.class);
        for (OAuthProviderClient client : providerClients) {
            this.clients.put(client.provider(), client);
        }
    }

    @Transactional
    public OAuthLoginResponse exchange(String providerName, OAuthExchangeRequest request,
                                       HttpServletResponse response) {
        AuthProvider provider = parseProvider(providerName);
        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(StatusCode.UNSUPPORTED_OAUTH_PROVIDER, providerName);
        }

        OAuthUserInfo info = client.fetchUser(request.code(), request.redirectUri());
        if (info.email() == null || !info.emailVerified()) {
            throw new BusinessException(StatusCode.OAUTH_EMAIL_NOT_VERIFIED, provider.name());
        }

        String email = info.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .map(existing -> linkExisting(existing, provider, info))
                .orElseGet(() -> createOAuthUser(email, provider, info));

        if (user.isBlocked()) {
            throw new BusinessException(StatusCode.ACCOUNT_BLOCKED);
        }

        String accessToken = authService.issueSession(user, response);
        return new OAuthLoginResponse(accessToken, user.isProfileCompleted());
    }

    private User createOAuthUser(String email, AuthProvider provider, OAuthUserInfo info) {
        User user = User.builder()
                .email(email)
                .hashPass(null)
                .authProvider(provider)
                .providerId(info.providerId())
                .role(Role.User)
                .isVerified(true)
                .profileCompleted(false)
                .build();
        log.info("Creating new {} account for {}", provider, email);
        return userRepository.save(user);
    }

    private User linkExisting(User user, AuthProvider provider, OAuthUserInfo info) {
        if (user.isVerified()) {
            // TH1 — same verified human. Link provider, keep any existing password.
            if (user.getProviderId() == null) {
                user.setAuthProvider(provider);
                user.setProviderId(info.providerId());
                log.info("Linking {} to existing verified account {}", provider, user.getEmail());
            }
        } else {
            // TH2 — take over the unverified (possibly squatted) account.
            user.setVerified(true);
            user.setHashPass(null);
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setAuthProvider(provider);
            user.setProviderId(info.providerId());
            log.warn("Taking over unverified account {} via {} (password invalidated)",
                    user.getEmail(), provider);
        }
        return userRepository.save(user);
    }

    private AuthProvider parseProvider(String name) {
        try {
            return AuthProvider.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(StatusCode.UNSUPPORTED_OAUTH_PROVIDER, name);
        }
    }
}
