package com.mockwise.interview.client.userprofile;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileAdapter {

    UserProfileClient client;

    public UserProfileResponse getProfile(String userId) {
        return unwrap(client.getProfile(userId));
    }

    private static <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new BusinessException(StatusCode.USER_PROFILE_UNAVAILABLE);
        }
        if (!response.isSuccess()) {
            throw new BusinessException(
                    response.getCode(),
                    response.getMessage() != null ? response.getMessage() : "user-profile call failed",
                    StatusCode.USER_PROFILE_UNAVAILABLE.getHttpStatus());
        }
        return response.getData();
    }
}
