package com.mockwise.iam.service;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.util.HashUtil;
import com.mockwise.iam.dto.internal.RefreshTokenDto;
import com.mockwise.iam.entity.RefreshToken;
import com.mockwise.iam.entity.User;
import com.mockwise.iam.repository.jpa.RefreshTokenRepository;
import com.mockwise.iam.repository.jpa.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshTokenService {

    RefreshTokenRepository tokenRepo;
    TokenService tokenService;
    UserRepository userRepo;

    @Transactional
    public void save(String token) {
        RefreshTokenDto dto = buildDto(token);
        String userId = tokenService.extractUserId(token);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "User not found: " + userId));

        Optional<RefreshToken> existing = tokenRepo.findById(dto.id());
        if (existing.isPresent()) {
            RefreshToken managed = existing.get();
            managed.setSessionId(dto.sessionId());
            managed.setTokenHash(dto.tokenHash());
            managed.setIssuedAt(dto.issuedAt());
            managed.setExpiresAt(dto.expiresAt());
            managed.setRevoked(false);
            managed.setUser(user);
        } else {
            tokenRepo.insertToken(dto.id(), dto.sessionId(), dto.tokenHash(),
                    dto.issuedAt(), dto.expiresAt(), false, userId);
        }
    }

    public Optional<RefreshToken> findValidById(String id) {
        return tokenRepo.findByIdAndRevokedFalse(id);
    }

    public boolean isRevoked(String tokenId) {
        return tokenRepo.findById(tokenId)
                .map(RefreshToken::isRevoked)
                .orElse(true);
    }

    @Transactional
    public void revokeByToken(String token) {
        String tokenId = tokenService.extractTokenId(token);
        revoke(tokenId);
    }

    public void revoke(String tokenId) {
        tokenRepo.findById(tokenId).ifPresent(token -> {
            token.setRevoked(true);
            tokenRepo.save(token);
        });
    }

    public void revokeAllByUserId(String userId) {
        tokenRepo.deleteByUserUserId(userId);
    }

    @Transactional
    public void saveWithLimit(String token, int maxSessions) {
        String userId = tokenService.extractUserId(token);

        List<RefreshToken> existingTokens = tokenRepo.findAllByUserUserIdOrderByIssuedAtDesc(userId);

        if (existingTokens.size() >= maxSessions) {
            List<RefreshToken> tokensToDelete = existingTokens.subList(maxSessions - 1, existingTokens.size());
            tokenRepo.deleteAllInBatch(tokensToDelete);
        }

        save(token);
    }

    private RefreshTokenDto buildDto(String refreshToken) {
        return RefreshTokenDto.builder()
                .id(tokenService.extractTokenId(refreshToken))
                .sessionId(tokenService.extractSessionId(refreshToken))
                .tokenHash(HashUtil.sha256(refreshToken))
                .issuedAt(tokenService.extractIssuedAt(refreshToken))
                .expiresAt(tokenService.extractExpiresAt(refreshToken))
                .build();
    }
}
