package com.mockwise.questionbank.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the synchronous TTS-STT internal call we make right after a
 * behavioral / core question is created or its text changes. {@code baseUrl}
 * points at tts-stt-service inside the docker network; {@code internalApiKey}
 * is the same shared secret all internal-net services use.
 */
@ConfigurationProperties(prefix = "tts-client")
public record TtsClientProperties(
        String baseUrl,
        String internalApiKey,
        int connectTimeoutMs,
        int readTimeoutMs,
        boolean enabled
) {}
