package com.mockwise.iam.service;

import com.core.apiresponse.common.ResponseCode;
import com.core.apiresponse.exception.ApiException;
import com.core.apiresponse.exception.ResourceNotFoundException;
import com.mockwise.iam.common.config.JwtProperties;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.security.Bcrypt;
import com.mockwise.iam.common.util.StatusCode;
import com.mockwise.iam.dto.internal.AuthenticatedUser;
import com.mockwise.iam.dto.request.*;
import com.mockwise.iam.dto.response.LoginResponse;
import com.mockwise.iam.dto.response.TokenIntrospectResponse;
import com.mockwise.iam.entity.User;
import com.mockwise.iam.mapper.UserMapper;
import com.mockwise.iam.message.enums.EmailType;
import com.mockwise.iam.message.event.EmailEvent;
import com.mockwise.iam.message.publisher.EmailEventPublisher;
import com.mockwise.iam.repository.jpa.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {

    UserRepository userRepository;
    Bcrypt bcrypt;
    UserMapper userMapper;
    TokenService tokenService;
    RefreshTokenService refreshTokenService;
    JwtProperties jwtProperties;
    TokenBlacklistService tokenBlacklistService;
    EmailEventPublisher emailEventPublisher;
    OtpService otpService;

    public LoginResponse login(AccountRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Account not found: " + request.email()));

        // Social-only accounts (or accounts whose password was invalidated during
        // an OAuth takeover) have no password hash — steer them to social login.
        if (user.getHashPass() == null) {
            throw new BusinessException(StatusCode.PASSWORD_LOGIN_UNAVAILABLE);
        }

        if (!bcrypt.matches(request.password(), user.getHashPass())) {
            throw new BusinessException(StatusCode.INVALID_PASSWORD_OR_EMAIL);
        }

        if (!user.isVerified()) {
            throw new BusinessException(StatusCode.ACCOUNT_NOT_VERIFIED);
        }

        if (user.isBlocked()) {
            throw new BusinessException(StatusCode.ACCOUNT_BLOCKED);
        }

        return new LoginResponse(issueSession(user, response));
    }

    /**
     * Mint an access token + refresh token for an already-authenticated user and
     * set the refresh cookie. Shared by password login and social login — the
     * user must already be persisted (the refresh token generation reloads it).
     */
    public String issueSession(User user, HttpServletResponse response) {
        AuthenticatedUser authenticatedUser = userMapper.toAuthenticatedUser(user);
        String accessToken = tokenService.generateAccessToken(authenticatedUser);
        String refreshToken = tokenService.generateRefreshToken(user.getUserId(), UUID.randomUUID().toString());

        refreshTokenService.saveWithLimit(refreshToken, jwtProperties.getMaxRefreshSessions());
        setRefreshTokenToCookie(refreshToken, response);

        return accessToken;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String bearer = request.getHeader("Authorization");
            log.info("Logout with bearer: {}", bearer);
            if (bearer != null && bearer.startsWith("Bearer ")) {
                String accessToken = extractRawToken(bearer);
                String jti = tokenService.extractTokenId(accessToken);
                Instant exp = tokenService.extractExpiresAt(accessToken);

                long seconds = exp.getEpochSecond() - Instant.now().getEpochSecond();
                if (seconds > 0) {
                    tokenBlacklistService.blacklist(jti, seconds);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist access token during logout: {}", e.getMessage());
        }

        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken != null) {
            refreshTokenService.revokeByToken(refreshToken);
        }

        clearRefreshTokenCookie(response);
    }

    public LoginResponse renewAccessToken(HttpServletRequest request) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        String tokenId = tokenService.extractTokenId(refreshToken);
        String userId = tokenService.extractUserId(refreshToken);
        int tokenVersion = tokenService.extractTokenVersion(refreshToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND, "User not found: " + userId));

        if (refreshTokenService.isRevoked(tokenId) || tokenVersion < user.getTokenVersion() || user.isBlocked()) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        AuthenticatedUser authenticatedUser = userMapper.toAuthenticatedUser(user);
        String accessToken = tokenService.generateAccessToken(authenticatedUser);
        return new LoginResponse(accessToken);
    }

    public boolean verifyAccountOtp(VerifyAccountOtpRequest request) {
        String email = request.email().trim().toLowerCase();
        String otp = request.otp().trim();

        String key = otpService.getVerifyAccountKey(email);
        boolean isValid = otpService.verifyOtp(key, otp);
        if (!isValid) {
            throw new BusinessException(StatusCode.INVALID_OTP);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Account not found: " + email));

        user.setVerified(true);
        userRepository.save(user);
        otpService.deleteOtp(key);
        return true;
    }

    public boolean sendResetPasswordOtp(ForgotEmailRequest request) {
        String email = request.email().trim().toLowerCase();

        if (otpService.isOtpStillValid(email)) {
            throw new BusinessException(StatusCode.TOO_MANY_REQUESTS);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Account", email));

        String otp = otpService.generateOtpForForgotPassword(user.getEmail());
        EmailEvent event = EmailEvent.builder()
                .to(email)
                .content(otp)
                .type(EmailType.RESET_PASSWORD)
                .build();

        emailEventPublisher.publish(event);
        return true;
    }

    public boolean verifyResetPasswordOtp(VerifyResetPasswordOtp request) {
        String email = request.email().trim().toLowerCase();
        String otp = request.otp().trim();

        String key = otpService.getForgotPasswordKey(email);
        boolean isValid = otpService.verifyOtp(key, otp);
        if (!isValid) {
            throw new BusinessException(StatusCode.INVALID_OTP);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Account not found: " + email));

        user.setHashPass(bcrypt.encode(request.newPassword()));
        userRepository.save(user);
        otpService.deleteOtp(key);
        return true;
    }

    public TokenIntrospectResponse introspectAccessToken(IntrospectRequest bearerToken) {
        if (bearerToken == null || bearerToken.bearerToken() == null || bearerToken.bearerToken().isBlank()) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }

        try {
            String token = extractRawToken(bearerToken.bearerToken());
            log.info("Introspecting token: {}", token);

            String jti = tokenService.peekTokenId(token);
            log.info("Token ID (jti): {}", jti);
            if (tokenBlacklistService.isBlacklisted(jti)) {
                log.info("Token is blacklisted: {}", jti);
                return new TokenIntrospectResponse(false, null, null, null, 0L);
            }

            JWTClaimsSet claims = tokenService.verifyAccessToken(token);

            String userId = claims.getSubject();
            String username = (String) claims.getClaim("username");
            String role = (String) claims.getClaim("role");
            long exp = claims.getExpirationTime().toInstant().getEpochSecond();

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                            "User not found: " + userId));

            int tokenVer = Optional.ofNullable(claims.getClaim("ver"))
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .map(Number::intValue)
                    .orElseThrow(() -> new BusinessException(StatusCode.INVALID_TOKEN));

            boolean active = tokenVer >= user.getTokenVersion() && !user.isBlocked();
            return new TokenIntrospectResponse(active, userId, username, role, exp);
        } catch (Exception e) {
            return new TokenIntrospectResponse(false, null, null, null, 0L);
        }
    }

    @Transactional
    public void logoutAllDevices(HttpServletRequest request) {
        String token = extractRawToken(request.getHeader("Authorization"));
        String userId = tokenService.extractUserId(token);

        // Blacklist the current access token so it cannot be reused after this call
        try {
            String jti = tokenService.extractTokenId(token);
            Instant exp = tokenService.extractExpiresAt(token);
            long seconds = exp.getEpochSecond() - Instant.now().getEpochSecond();
            if (seconds > 0) {
                tokenBlacklistService.blacklist(jti, seconds);
            }
        } catch (Exception e) {
            log.warn("Failed to blacklist access token during logout-all: {}", e.getMessage());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "User not found: " + userId));

        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllByUserId(userId);
    }

    public boolean sendVerificationOtp(VerifyEmailRequest request) {
        String email = request.email().trim().toLowerCase();

        if (otpService.isOtpStillValid(email)) {
            throw new BusinessException(StatusCode.TOO_MANY_REQUESTS);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Account not found: " + email));

        String otp = otpService.generateOtpForVerifyAccount(email);
        EmailEvent event = EmailEvent.builder()
                .to(email)
                .content(otp)
                .type(EmailType.OTP_VERIFICATION)
                .build();
        emailEventPublisher.publish(event);
        return true;
    }

    private static String extractRawToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new BusinessException(ResponseCode.UNAUTHORIZED);
        }
        return bearerToken.substring(7);
    }

    private static String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("refreshToken".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshTokenToCookie(String refreshToken, HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(jwtProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge((int) jwtProperties.getRefreshTtlSeconds());
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(jwtProperties.isCookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
