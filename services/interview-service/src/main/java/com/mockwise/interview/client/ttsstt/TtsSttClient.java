package com.mockwise.interview.client.ttsstt;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.interview.client.ttsstt.config.TtsSttFeignConfig;
import com.mockwise.interview.client.ttsstt.dto.RealtimeSttTokenResponse;
import com.mockwise.interview.client.ttsstt.dto.TranscriptResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Feign view onto tts-stt-service's internal transcript endpoint. Called
 * by the {@code transcript-ready} consumer to fetch the actual transcript
 * text — the event payload only carries pointers (transcriptId, durationMs,
 * wordCount), not the text itself.
 *
 * <p>Wire-shape note: tts-stt's controller path is
 * {@code /internal/transcripts/{id}} (no service-level prefix) so the
 * URL config must end at {@code /api/v1/...} stripped — see
 * {@code application.yml}. The original survey confirmed only
 * {@code /internal/transcripts/{id}} on tts-stt's side.
 */
@FeignClient(
        name = "tts-stt-service",
        url = "${external.services.tts-stt.url}",
        configuration = TtsSttFeignConfig.class
)
public interface TtsSttClient {

    @GetMapping(
            value = "/internal/transcripts/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<TranscriptResponse> getTranscript(@PathVariable("id") String transcriptId);

    /**
     * Mints a single-use ElevenLabs realtime Scribe token (Lever 2 fast path).
     * Relayed to the browser by interview-service so the API key stays in
     * tts-stt. POST — each token is consumed on use.
     */
    @PostMapping(
            value = "/internal/realtime-stt-token",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ApiResponse<RealtimeSttTokenResponse> mintRealtimeToken();
}
