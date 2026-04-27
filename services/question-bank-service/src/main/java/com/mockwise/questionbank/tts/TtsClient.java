package com.mockwise.questionbank.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockwise.questionbank.common.config.TtsClientProperties;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Synchronous client for tts-stt-service. Used right after a behavioral / core
 * question is created or its text changes — generates the audio, persists it on
 * storage-service, and returns the {@code objectKey} the question row should
 * reference.
 *
 * <p>Calls are <b>fail-soft</b>: a TTS outage must not block question creation.
 * Callers receive {@link TtsResult#failed(String)} and persist a null audioKey;
 * the admin can retry by editing the question (which re-triggers TTS).
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TtsClient {

    RestClient client;
    TtsClientProperties props;

    public TtsClient(@Qualifier("ttsRestClient") RestClient client, TtsClientProperties props) {
        this.client = client;
        this.props = props;
    }

    public TtsResult synthesize(String questionId, String text) {
        if (!props.enabled()) {
            log.info("TTS disabled by config — skipping synthesis for question={}", questionId);
            return TtsResult.skipped();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("questionId", questionId);
        body.put("text", text);
        String lang = props.defaultLanguageCode();
        if (lang != null && !lang.isBlank()) {
            body.put("languageCode", lang);
        }

        try {
            JsonNode resp = client.post()
                    .uri("/internal/tts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (resp == null || !resp.has("data")) {
                log.warn("TTS response missing data field for question={}", questionId);
                return TtsResult.failed("empty response");
            }
            JsonNode data = resp.get("data");
            String objectKey = data.path("objectKey").asText(null);
            if (objectKey == null || objectKey.isBlank()) {
                log.warn("TTS returned empty objectKey for question={}", questionId);
                return TtsResult.failed("missing objectKey");
            }

            log.info("TTS generated for question={} objectKey={} cached={}",
                    questionId, objectKey, data.path("cached").asBoolean(false));
            return TtsResult.ok(objectKey);
        } catch (Exception ex) {
            log.warn("TTS call failed for question={} — saving without audio", questionId, ex);
            return TtsResult.failed(ex.getMessage());
        }
    }

    public record TtsResult(String objectKey, boolean success, String reason) {
        public static TtsResult ok(String objectKey) {
            return new TtsResult(objectKey, true, null);
        }
        public static TtsResult failed(String reason) {
            return new TtsResult(null, false, reason);
        }
        public static TtsResult skipped() {
            return new TtsResult(null, false, "disabled");
        }
    }
}
