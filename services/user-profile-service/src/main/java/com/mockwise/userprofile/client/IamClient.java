package com.mockwise.userprofile.client;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.client.dto.IamUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "iamClient",
        url = "${mockwise.iam.base-url:http://mockwise-iam:8081/api/v1/iam}"
)
public interface IamClient {

    @GetMapping("/users/{userId}")
    ApiResponse<IamUserResponse> getUserById(@PathVariable("userId") String userId);
}
