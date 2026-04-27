package com.mockwise.ttsstt.stt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mockwise.ttsstt.common.config.ElevenLabsProperties;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import com.mockwise.ttsstt.stt.entity.TranscriptWord;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.PathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ElevenLabsScribeClient {

    RestClient client;
    ElevenLabsProperties props;

    public ElevenLabsScribeClient(@Qualifier("elevenLabsRestClient") RestClient client,
                                  ElevenLabsProperties props) {
        this.client = client;
        this.props = props;
    }

    public ScribeResult transcribe(Path audioFile, String languageCode) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new PathResource(audioFile));
        form.add("model_id", props.sttModel());
        form.add("timestamps_granularity", "word");
        if (languageCode != null && !languageCode.isBlank()) {
            form.add("language_code", languageCode);
        }

        try {
            JsonNode resp = client.post()
                    .uri("/v1/speech-to-text")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        String snippet = new String(res.getBody().readAllBytes());
                        log.warn("Scribe {} : {}", status, snippet);
                        if (status == 402) {
                            throw new BusinessException(StatusCode.ELEVENLABS_PAYMENT_REQUIRED, snippet);
                        }
                        throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, snippet);
                    })
                    .body(JsonNode.class);

            if (resp == null) {
                throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, "empty body");
            }
            return mapResult(resp);
        } catch (BusinessException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.error("Scribe network/timeout", ex);
            throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, "timeout");
        } catch (HttpServerErrorException ex) {
            log.error("Scribe upstream 5xx: {}", ex.getStatusCode(), ex);
            throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, ex.getStatusCode().toString());
        } catch (Exception ex) {
            log.error("Scribe unexpected", ex);
            throw new BusinessException(StatusCode.ELEVENLABS_STT_ERROR, ex.getMessage());
        }
    }

    private ScribeResult mapResult(JsonNode resp) {
        String text = resp.path("text").asText("");
        String langCode = resp.path("language_code").asText(null);
        Float langConf = resp.has("language_probability")
                ? (float) resp.get("language_probability").asDouble() : null;

        List<TranscriptWord> words = new ArrayList<>();
        JsonNode wordsArr = resp.path("words");
        if (wordsArr.isArray()) {
            for (JsonNode w : wordsArr) {
                words.add(TranscriptWord.builder()
                        .text(w.path("text").asText(null))
                        .type(w.path("type").asText(null))
                        .start(w.has("start") ? w.get("start").asDouble() : null)
                        .end(w.has("end") ? w.get("end").asDouble() : null)
                        .speakerId(w.path("speaker_id").asText(null))
                        .confidence(w.has("logprob") ? w.get("logprob").asDouble() : null)
                        .build());
            }
        }
        return ScribeResult.builder()
                .text(text)
                .languageCode(langCode)
                .languageConfidence(langConf)
                .words(words)
                .build();
    }
}
