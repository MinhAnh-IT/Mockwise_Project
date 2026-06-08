package com.mockwise.iam.service;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.util.StatusCode;
import com.mockwise.iam.dto.response.UserResponse;
import com.mockwise.iam.entity.User;
import com.mockwise.iam.enums.Role;
import com.mockwise.iam.mapper.UserMapper;
import com.mockwise.iam.repository.jpa.UserRepository;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Admin account moderation. Today this is just block / unblock — admins do NOT
 * edit a user's profile content (that stays the user's own right).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserService {

    UserRepository userRepository;
    RefreshTokenService refreshTokenService;
    UserMapper userMapper;

    /**
     * Ban an account. Bumps {@code tokenVersion} (live access tokens go inactive
     * at the next gateway introspect) and revokes every refresh token, so the
     * user is logged out everywhere immediately and cannot sign back in.
     */
    @Transactional
    public UserResponse blockUser(String actingUserId, String targetUserId) {
        if (targetUserId.equals(actingUserId)) {
            throw new BusinessException(StatusCode.CANNOT_BLOCK_SELF);
        }

        User user = findUser(targetUserId);

        if (user.getRole() == Role.Admin) {
            throw new BusinessException(StatusCode.CANNOT_BLOCK_ADMIN);
        }

        if (!user.isBlocked()) {
            user.setBlocked(true);
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
            refreshTokenService.revokeAllByUserId(targetUserId);
            log.info("Admin {} blocked user {}", actingUserId, targetUserId);
        }

        return userMapper.toUserResponse(user);
    }

    /** Lift a ban. The user can sign in again (with fresh tokens). */
    @Transactional
    public UserResponse unblockUser(String actingUserId, String targetUserId) {
        User user = findUser(targetUserId);

        if (user.isBlocked()) {
            user.setBlocked(false);
            userRepository.save(user);
            log.info("Admin {} unblocked user {}", actingUserId, targetUserId);
        }

        return userMapper.toUserResponse(user);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "User not found: " + userId));
    }
}
