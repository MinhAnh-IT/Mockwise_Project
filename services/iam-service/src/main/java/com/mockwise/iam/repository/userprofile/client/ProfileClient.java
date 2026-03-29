package com.mockwise.iam.repository.userprofile.client;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.iam.dto.request.ProfileDraftRequest;
import com.mockwise.iam.dto.response.UserProfileResponse;
import com.mockwise.iam.repository.userprofile.config.ProfileFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "profile-service",
        url = "${external.services.profile-service.url:http://localhost:8082/api/v1/profiles}",
        configuration = ProfileFeignConfig.class
)
public interface ProfileClient {

    @PostMapping(
            value = "/{userId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<UserProfileResponse> createProfile(
            @PathVariable("userId") String userId,
            @RequestBody ProfileDraftRequest request
    );
}
