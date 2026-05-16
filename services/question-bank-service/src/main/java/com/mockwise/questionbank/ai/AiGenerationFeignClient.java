package com.mockwise.questionbank.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the AI service's {@code /generate-testcases} endpoint.
 *
 * <p>The base URL and the {@code X-API-Key} secret are resolved server-side
 * (see {@link AiFeignConfig}); nothing here is exposed to the browser. The
 * request/response are passed through as raw JSON so this service stays a thin
 * proxy and doesn't drift from the AI payload schema.
 */
@FeignClient(
        name = "aiGenerationClient",
        url = "${ai-client.base-url}",
        configuration = AiFeignConfig.class)
public interface AiGenerationFeignClient {

    @PostMapping(
            value = "/generate-testcases",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    JsonNode generateTestcases(@RequestBody JsonNode body);
}
