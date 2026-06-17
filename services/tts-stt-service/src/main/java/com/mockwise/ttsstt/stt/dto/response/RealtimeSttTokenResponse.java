package com.mockwise.ttsstt.stt.dto.response;

import com.mockwise.ttsstt.stt.service.RealtimeSttTokenClient.RealtimeToken;

/**
 * Single-use realtime Scribe token handed to interview-service (and onward to
 * the browser). The token connects directly to the ElevenLabs realtime
 * WebSocket; the API key never leaves tts-stt.
 */
public record RealtimeSttTokenResponse(String token, String model, int expiresInSeconds) {

    public static RealtimeSttTokenResponse from(RealtimeToken t) {
        return new RealtimeSttTokenResponse(t.token(), t.model(), t.expiresInSeconds());
    }
}
