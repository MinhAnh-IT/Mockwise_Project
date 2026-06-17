package com.mockwise.ttsstt.stt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockwise.ttsstt.common.config.ElevenLabsProperties;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Mints ElevenLabs single-use tokens for the realtime (WebSocket) Scribe so the
 * browser can stream audio directly to ElevenLabs on the Lever 2 fast path
 * without ever seeing our API key. The token is time-bound (~15 min) and
 * consumed on use — see realtime-stt-plan.md §5 (Provider C).
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RealtimeSttTokenClient {

    // 15 min per ElevenLabs docs — surfaced to the caller so the FE can refresh.
    private static final int TOKEN_TTL_SECONDS = 900;

    RestClient client;
    ElevenLabsProperties props;

    public RealtimeSttTokenClient(@Qualifier("elevenLabsRestClient") RestClient client,
                                  ElevenLabsProperties props) {
        this.client = client;
        this.props = props;
    }

    /** @return a fresh single-use token + the realtime model id the FE should use. */
    public RealtimeToken mint() {
        try {
            JsonNode resp = client.post()
                    .uri("/v1/single-use-token/realtime_scribe")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String snippet = new String(res.getBody().readAllBytes());
                        log.warn("ElevenLabs single-use-token {} : {}", res.getStatusCode().value(), snippet);
                        throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, snippet);
                    })
                    .body(JsonNode.class);
            if (resp == null || resp.get("token") == null) {
                throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, "no token in response");
            }
            return new RealtimeToken(
                    resp.get("token").asText(), props.realtimeSttModel(), TOKEN_TTL_SECONDS);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to mint realtime STT token: {}", e.getMessage(), e);
            throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, String.valueOf(e.getMessage()));
        }
    }

    public record RealtimeToken(String token, String model, int expiresInSeconds) {}
}
