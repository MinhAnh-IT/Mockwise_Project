package com.mockwise.questionbank.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the server-side proxy call to the AI service's
 * {@code /generate-testcases} endpoint.
 *
 * <p>The AI service is subscription-backed (LLM cost) and is protected by a
 * static {@code X-API-Key}. That key is a <b>server-only secret</b>: it must
 * never be shipped to the browser. The admin UI calls our own admin-gated
 * endpoint with its normal JWT; this service holds {@code apiKey} and adds the
 * header when forwarding to the AI service.
 */
@ConfigurationProperties(prefix = "ai-client")
public record AiClientProperties(
        String baseUrl,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs
) {}
