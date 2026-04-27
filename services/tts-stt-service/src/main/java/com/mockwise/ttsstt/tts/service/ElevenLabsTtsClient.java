package com.mockwise.ttsstt.tts.service;

import com.mockwise.ttsstt.common.config.ElevenLabsProperties;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ElevenLabsTtsClient {

    RestClient client;
    ElevenLabsProperties props;

    public ElevenLabsTtsClient(@Qualifier("elevenLabsRestClient") RestClient client,
                               ElevenLabsProperties props) {
        this.client = client;
        this.props = props;
    }

    /**
     * Synthesize text → mp3 bytes via ElevenLabs.
     */
    public byte[] synthesize(String text, String voiceId, String modelId) {
        Map<String, Object> body = Map.of(
                "text", text,
                "model_id", modelId,
                "voice_settings", Map.of("stability", 0.5, "similarity_boost", 0.75));

        try {
            byte[] audio = client.post()
                    .uri("/v1/text-to-speech/{voiceId}", voiceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("audio/mpeg"))
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        String snippet = new String(res.getBody().readAllBytes());
                        log.warn("ElevenLabs TTS {} for voice={} : {}", status, voiceId, snippet);
                        if (status == 402) {
                            throw new BusinessException(StatusCode.ELEVENLABS_PAYMENT_REQUIRED, snippet);
                        }
                        if (status == 401 || status == 403) {
                            throw new BusinessException(StatusCode.ELEVENLABS_TTS_ERROR, snippet);
                        }
                        throw new BusinessException(StatusCode.TTS_VOICE_INVALID);
                    })
                    .body(byte[].class);

            if (audio == null || audio.length == 0) {
                throw new BusinessException(StatusCode.ELEVENLABS_TTS_ERROR, "empty body");
            }
            return audio;
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("ElevenLabs TTS timeout/network", ex);
            throw new BusinessException(StatusCode.ELEVENLABS_TTS_TIMEOUT);
        } catch (HttpServerErrorException ex) {
            log.error("ElevenLabs TTS upstream 5xx: status={}", ex.getStatusCode(), ex);
            throw new BusinessException(StatusCode.ELEVENLABS_TTS_ERROR, ex.getStatusCode().toString());
        } catch (Exception ex) {
            log.error("ElevenLabs TTS unexpected", ex);
            throw new BusinessException(StatusCode.ELEVENLABS_TTS_ERROR, ex.getMessage());
        }
    }

    public ElevenLabsProperties props() {
        return props;
    }
}
