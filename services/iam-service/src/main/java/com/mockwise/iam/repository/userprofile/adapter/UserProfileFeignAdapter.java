package com.mockwise.iam.repository.userprofile.adapter;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.iam.common.exception.BusinessException;
import com.mockwise.iam.dto.request.ProfileDraftRequest;
import com.mockwise.iam.dto.response.UserProfileResponse;
import com.mockwise.iam.repository.userprofile.client.ProfileClient;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileFeignAdapter {

    ProfileClient profileClient;

    public UserProfileResponse createProfile(String userId, ProfileDraftRequest request) {
        ApiResponse<UserProfileResponse> response = profileClient.createProfile(userId, request);
        log.info("User profile created via ProfileClient: userId={}, success={}", userId, response.isSuccess());

        if (response.isSuccess()) {
            return response.getData();
        }

        log.error("Profile service returned failure: code={}, message={}", response.getCode(), response.getMessage());
        throw new BusinessException(response.getCode(), response.getMessage());
    }
}
