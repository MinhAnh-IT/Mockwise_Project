package com.mockwise.interview.client.ai;

import com.mockwise.interview.client.ai.config.AiServiceFeignConfig;
import com.mockwise.interview.client.ai.dto.AiFollowUpRequest;
import com.mockwise.interview.client.ai.dto.AiFollowUpResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the Python AI service. The AI service does NOT use our
 * {@code ApiResponse} envelope — endpoints return their typed payload
 * directly, so the response type is the DTO itself.
 *
 * <p>Only {@code POST /follow-up/generate} is wired here; the
 * {@code POST /evaluate} flow goes through Kafka via
 * {@code evaluation-requested}, not REST.
 */
@FeignClient(
        name = "ai-service",
        url = "${external.services.ai.url}",
        configuration = AiServiceFeignConfig.class
)
public interface AiServiceClient {

    @PostMapping(
            value = "/follow-up/generate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    AiFollowUpResponse generateFollowUp(@RequestBody AiFollowUpRequest request);
}
