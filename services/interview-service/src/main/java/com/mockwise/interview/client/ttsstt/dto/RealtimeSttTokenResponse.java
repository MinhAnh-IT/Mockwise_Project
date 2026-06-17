package com.mockwise.interview.client.ttsstt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirror of tts-stt-service's realtime-token response. The {@code token} is a
 * single-use ElevenLabs realtime Scribe token the browser uses to connect to
 * the ElevenLabs WebSocket directly (Lever 2 fast path); {@code model} is the
 * realtime model id to put on the WS query.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RealtimeSttTokenResponse(String token, String model, Integer expiresInSeconds) {}
