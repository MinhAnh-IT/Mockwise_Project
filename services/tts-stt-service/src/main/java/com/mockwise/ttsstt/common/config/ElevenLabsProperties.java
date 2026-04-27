package com.mockwise.ttsstt.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elevenlabs")
public record ElevenLabsProperties(
        String apiKey,
        String baseUrl,
        String defaultVoiceId,
        String ttsModel,
        String sttModel,
        String defaultLanguageCode,
        int ttsTimeoutMs,
        int sttTimeoutMs
) {}
