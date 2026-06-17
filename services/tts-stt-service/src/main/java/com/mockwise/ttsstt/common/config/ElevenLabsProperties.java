package com.mockwise.ttsstt.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elevenlabs")
public record ElevenLabsProperties(
        String apiKey,
        String baseUrl,
        String defaultVoiceId,
        String ttsModel,
        String sttModel,
        // Model id for the realtime (WebSocket) Scribe used by the browser on
        // the Lever 2 fast path. Distinct from sttModel (batch scribe_v1).
        String realtimeSttModel,
        String defaultLanguageCode,
        int ttsTimeoutMs,
        int sttTimeoutMs
) {}
