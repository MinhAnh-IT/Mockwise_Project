package com.mockwise.interview.client.ai;

import com.mockwise.interview.client.ai.dto.AiFollowUpRequest;
import com.mockwise.interview.client.ai.dto.AiFollowUpResponse;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around {@link AiServiceClient}. The error decoder already
 * translates upstream failures into {@link BusinessException}s — this
 * layer exists mainly so callers see a service-shaped class in their
 * dependency graph rather than a Feign interface, and so we have one
 * place to add cross-cutting concerns (caching, circuit breaker) later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiServiceAdapter {

    AiServiceClient client;

    public AiFollowUpResponse generateFollowUp(AiFollowUpRequest request) {
        AiFollowUpResponse response = client.generateFollowUp(request);
        if (response == null || response.questionText() == null || response.questionText().isBlank()) {
            // Defensive: an empty / blank follow-up question is unusable —
            // treat the same as an upstream failure so the planner falls back.
            throw new BusinessException(StatusCode.AI_SERVICE_UNAVAILABLE);
        }
        return response;
    }
}
