package com.mockwise.iam.service;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.iam.common.config.JwtProperties;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.util.StatusCode;
import com.mockwise.iam.dto.internal.AuthenticatedUser;
import com.mockwise.iam.entity.User;
import com.mockwise.iam.enums.Role;
import com.mockwise.iam.repository.jpa.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TokenService {

    JwtProperties jwtProps;
    UserRepository userRepository;

    // ====== GENERATE ======

    public String generateAccessToken(AuthenticatedUser user) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getUserId())
                    .audience(jwtProps.getAudience())
                    .issuer(jwtProps.getIssuer())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(jwtProps.getAccessTtlSeconds())))
                    .notBeforeTime(Date.from(now.minusSeconds(60)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("username", user.getUsername())
                    .claim("role", user.getRole().name())
                    .claim("ver", user.getTokenVersion())
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(createSigner());
            return jwt.serialize();
        } catch (Exception e) {
            log.error("Failed to generate access token", e);
            throw new BusinessException(StatusCode.GEN_TOKEN_FAILED);
        }
    }

    public String generateRefreshToken(String userId, String sessionId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                            "User not found: " + userId));

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .audience(jwtProps.getAudience())
                    .issuer(jwtProps.getIssuer())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(jwtProps.getRefreshTtlSeconds())))
                    .notBeforeTime(Date.from(now.minusSeconds(60)))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("sid", sessionId)
                    .claim("typ", "refresh")
                    .claim("ver", user.getTokenVersion())
                    .build();

            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(createSigner());
            return jwt.serialize();
        } catch (Exception e) {
            log.error("Failed to generate refresh token", e);
            throw new BusinessException(StatusCode.GEN_TOKEN_FAILED);
        }
    }

    // ====== VERIFY ======

    public JWTClaimsSet verifyAccessToken(String token) {
        return verifyToken(token, false);
    }

    private JWTClaimsSet verifyRefreshToken(String token) {
        JWTClaimsSet claims = verifyToken(token, true);
        String typ = (String) claims.getClaim("typ");
        if (!"refresh".equals(typ)) {
            throw new BusinessException(StatusCode.INVALID_TOKEN);
        }
        return claims;
    }

    private JWTClaimsSet verifyToken(String token, boolean isRefresh) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new BusinessException(StatusCode.INVALID_TOKEN);
            }

            if (!jwt.verify(createVerifier())) {
                throw new BusinessException(StatusCode.INVALID_TOKEN);
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            validateClaims(claims);

            String userId = claims.getSubject();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                            "User not found: " + userId));

            Object claimVer = claims.getClaim("ver");
            if (!(claimVer instanceof Number ver) || ver.intValue() < user.getTokenVersion()) {
                throw new BusinessException(ResponseCode.UNAUTHORIZED);
            }

            return claims;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token verification failed: {}", e.getMessage());
            throw new BusinessException(StatusCode.INVALID_TOKEN);
        }
    }

    // ====== PARSE ======

    public AuthenticatedUser extractUser(String accessToken) {
        JWTClaimsSet claims = verifyAccessToken(accessToken);
        return AuthenticatedUser.builder()
                .userId(claims.getSubject())
                .username((String) claims.getClaim("username"))
                .role(Role.valueOf((String) claims.getClaim("role")))
                .tokenVersion(((Number) claims.getClaim("ver")).intValue())
                .build();
    }

    public String extractSessionId(String refreshToken) {
        JWTClaimsSet claims = verifyRefreshToken(refreshToken);
        return (String) claims.getClaim("sid");
    }

    public String extractUserId(String token) {
        JWTClaimsSet claims = verifyToken(token, false);
        return claims.getSubject();
    }

    public String peekTokenId(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.getJWTClaimsSet().getJWTID();
        } catch (Exception e) {
            log.error("Failed to peek token ID: {}", e.getMessage());
            throw new BusinessException(StatusCode.INVALID_TOKEN);
        }
    }

    public Instant extractIssuedAt(String token) {
        JWTClaimsSet claims = verifyToken(token, false);
        return claims.getIssueTime().toInstant();
    }

    public Instant extractExpiresAt(String token) {
        JWTClaimsSet claims = verifyToken(token, false);
        return claims.getExpirationTime().toInstant();
    }

    public int extractTokenVersion(String token) {
        JWTClaimsSet claimsSet = verifyToken(token, false);
        return ((Number) claimsSet.getClaim("ver")).intValue();
    }

    public String extractTokenId(String token) {
        log.info("Extracting token ID from token: {}", token);
        JWTClaimsSet claims = verifyToken(token, false);
        return claims.getJWTID();
    }

    // ====== INTERNAL ======

    private JWSSigner createSigner() throws KeyLengthException {
        return new MACSigner(jwtProps.getJwtHmacSecret().getBytes(StandardCharsets.UTF_8));
    }

    private JWSVerifier createVerifier() throws JOSEException {
        return new MACVerifier(jwtProps.getJwtHmacSecret().getBytes(StandardCharsets.UTF_8));
    }

    private void validateClaims(JWTClaimsSet claims) {
        Instant now = Instant.now();

        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(Date.from(now))) {
            throw new BusinessException(StatusCode.INVALID_TOKEN);
        }

        if (claims.getNotBeforeTime() != null && claims.getNotBeforeTime().after(Date.from(now))) {
            throw new BusinessException(StatusCode.INVALID_TOKEN);
        }

        if (!jwtProps.getIssuer().equals(claims.getIssuer())) {
            throw new BusinessException(StatusCode.INVALID_TOKEN);
        }

        if (claims.getAudience() == null || !claims.getAudience().contains(jwtProps.getAudience())) {
            throw new BusinessException(StatusCode.INVALID_TOKEN);
        }
    }
}
