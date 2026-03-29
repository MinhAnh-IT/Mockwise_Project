package com.mockwise.userprofile.service;

import com.core.apiresponse.common.ResponseCode;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.client.IamClient;
import com.mockwise.userprofile.client.dto.IamUserResponse;
import com.mockwise.userprofile.common.exception.BusinessException;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IamUserService {

    IamClient iamClient;

    public IamUserResponse getUserInfo(String userId) {
        try {
            ApiResponse<IamUserResponse> response = iamClient.getUserById(userId);
            IamUserResponse data = response.getData();

            if (data == null) {
                log.error("No data in IAM response for userId: {}", userId);
                throw new BusinessException(ResponseCode.INTERNAL_ERROR, "No user data returned from IAM service");
            }

            return data;
        } catch (FeignException e) {
            log.error("Failed to get user info from IAM service for userId: {}", userId, e);
            throw new BusinessException(ResponseCode.INTERNAL_ERROR, "Failed to fetch user from IAM service", e);
        }
    }
}
