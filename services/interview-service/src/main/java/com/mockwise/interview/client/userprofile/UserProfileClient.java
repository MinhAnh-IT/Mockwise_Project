package com.mockwise.interview.client.userprofile;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.userprofile.config.UserProfileFeignConfig;
import com.mockwise.interview.client.userprofile.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign view onto user-profile-service's by-id endpoint. The route is
 * unauthenticated by design (service-to-service traffic), but we still
 * inject service-identity {@code X-User-*} headers so user-profile's
 * filter can log the caller.
 */
@FeignClient(
        name = "user-profile-service",
        url = "${external.services.user-profile.url}",
        configuration = UserProfileFeignConfig.class
)
public interface UserProfileClient {

    @GetMapping(
            value = "/{userId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<UserProfileResponse> getProfile(@PathVariable("userId") String userId);
}
