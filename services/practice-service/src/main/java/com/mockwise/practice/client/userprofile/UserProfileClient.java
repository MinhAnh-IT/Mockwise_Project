package com.mockwise.practice.client.userprofile;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.practice.client.userprofile.config.UserProfileFeignConfig;
import com.mockwise.practice.client.userprofile.dto.ProfileBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign view onto user-profile-service's batch display-name endpoint, used to
 * enrich a page of leaderboard userIds with names in one round trip. The route
 * is service-to-service; {@link UserProfileFeignConfig} injects a SERVICE
 * identity so user-profile authorizes the read.
 */
@FeignClient(
        name = "practice-user-profile",
        url = "${external.services.user-profile.url}",
        configuration = UserProfileFeignConfig.class
)
public interface UserProfileClient {

    @GetMapping(value = "/batch", produces = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<List<ProfileBrief>> getBriefs(@RequestParam("ids") List<String> ids);
}
