package com.mockwise.iam.service;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.common.security.Bcrypt;
import com.mockwise.iam.common.util.StatusCode;
import org.springframework.dao.DataIntegrityViolationException;
import com.mockwise.iam.dto.request.ProfileDraftRequest;
import com.mockwise.iam.dto.request.RegisterRequest;
import com.mockwise.iam.dto.response.RegisterResponse;
import com.mockwise.iam.dto.response.UserProfileResponse;
import com.mockwise.iam.dto.response.UserResponse;
import com.mockwise.iam.entity.User;
import com.mockwise.iam.mapper.RegisterMapper;
import com.mockwise.iam.mapper.UserMapper;
import com.mockwise.iam.repository.jpa.UserRepository;
import com.mockwise.iam.repository.userprofile.adapter.UserProfileFeignAdapter;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    Bcrypt bcrypt;
    UserMapper userMapper;
    UserProfileFeignAdapter profileService;
    RegisterMapper registerMapper;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.account().email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(StatusCode.USER_ALREADY_EXISTS, email);
        }

        User user = userMapper.toUser(request.account());
        user.setHashPass(bcrypt.encode(request.account().password()));

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration with the same email
            throw new BusinessException(StatusCode.USER_ALREADY_EXISTS, email);
        }

        UserResponse userResponse = userMapper.toUserResponse(saved);
        UserProfileResponse profileResponse = profileService.createProfile(userResponse.userId(), request.profile());

        return registerMapper.toRegisterResponse(userResponse, profileResponse);
    }

    /**
     * Create the user-profile for an account that doesn't have one yet — used by
     * social-login users after their first sign-in (Google/GitHub can't supply
     * track/level, so the SPA collects them and posts here). Idempotent
     * guard: a one-shot completion, rejected if already done.
     */
    @Transactional
    public void completeProfile(String userId, ProfileDraftRequest draft) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND, "User not found: " + userId));

        if (user.isProfileCompleted()) {
            throw new BusinessException(StatusCode.PROFILE_ALREADY_COMPLETED);
        }

        profileService.createProfile(userId, draft);
        user.setProfileCompleted(true);
        userRepository.save(user);
    }

    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "User not found: " + userId));
    }
}
