package com.mockwise.questionbank.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockwise.questionbank.common.exception.BusinessException;
import com.mockwise.questionbank.common.exception.StatusCode;
import feign.RetryableException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Service-side entry point for AI coding-question generation.
 *
 * <p>Non-2xx upstream responses are already translated to a
 * {@link BusinessException} by {@link AiFeignConfig}'s error decoder. This
 * wrapper only handles transport failures (AI service unreachable / timed
 * out), which Feign raises as {@link RetryableException} instead of routing
 * through the error decoder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CodingGenerationClient {

    AiGenerationFeignClient feignClient;

    /**
     * Forward the (camelCase) generate request to the AI service and return
     * the raw coding-question payload for the admin to review and edit.
     */
    public JsonNode generate(JsonNode body) {
        try {
            JsonNode result = feignClient.generateTestcases(body);
            if (result == null || result.isNull()) {
                throw new BusinessException(StatusCode.AI_GENERATION_FAILED, "empty response");
            }
            return result;
        } catch (RetryableException ex) {
            log.error("AI generate-testcases transport error", ex);
            throw new BusinessException(StatusCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
