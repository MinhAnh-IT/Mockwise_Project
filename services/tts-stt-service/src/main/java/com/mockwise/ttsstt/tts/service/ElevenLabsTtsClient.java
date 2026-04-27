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
    public byte[] synthesize(String text, String voiceId, String modelId, String languageCode) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("text", text);
        body.put("model_id", modelId);
        body.put("voice_settings", Map.of("stability", 0.5, "similarity_boost", 0.75));
        // language_code is supported by turbo_v2_5 / flash_v2_5 / eleven_v3; multilingual_v2
        // rejects it with HTTP 400 ("Model X does not support language_code Y"). Skip the
        // field for any model that does not opt in, so callers can keep sending a global
        // languageCode default without us having to mirror ElevenLabs' compatibility matrix.
        if (languageCode != null && !languageCode.isBlank() && supportsLanguageCode(modelId)) {
            body.put("language_code", languageCode);
        }

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

    private static boolean supportsLanguageCode(String modelId) {
        if (modelId == null) return false;
        return modelId.startsWith("eleven_turbo_v2_5")
                || modelId.startsWith("eleven_flash_v2_5")
                || modelId.startsWith("eleven_v3");
    }
}
