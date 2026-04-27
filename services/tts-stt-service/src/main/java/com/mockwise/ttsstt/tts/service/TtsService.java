package com.mockwise.ttsstt.tts.service;

import com.mockwise.ttsstt.common.config.ElevenLabsProperties;
import com.mockwise.ttsstt.common.config.TtsSttProperties;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import com.mockwise.ttsstt.storage.StorageServiceClient;
import com.mockwise.ttsstt.storage.UploadedQuestionAudio;
import com.mockwise.ttsstt.tts.dto.request.TtsSynthesizeRequest;
import com.mockwise.ttsstt.tts.dto.response.TtsSynthesizeResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TtsService {

    TtsSttProperties props;
    ElevenLabsProperties elevenProps;
    ElevenLabsTtsClient elevenLabs;
    TtsCacheService cache;
    StorageServiceClient storage;

    public TtsSynthesizeResponse synthesize(TtsSynthesizeRequest req) {
        if (req.getText().length() > props.tts().maxTextLength()) {
            throw new BusinessException(StatusCode.TTS_TEXT_TOO_LONG, props.tts().maxTextLength());
        }

        String voiceId = (req.getVoiceId() != null && !req.getVoiceId().isBlank())
                ? req.getVoiceId() : elevenProps.defaultVoiceId();
        String modelId = (req.getModelId() != null && !req.getModelId().isBlank())
                ? req.getModelId() : elevenProps.ttsModel();

        String hash = cache.hash(req.getText(), voiceId, modelId);
        TtsCacheEntry hit = cache.get(hash);
        if (hit != null) {
            log.info("TTS cache hit for question={} hash={}", req.getQuestionId(), hash);
            return TtsSynthesizeResponse.builder()
                    .objectId(hit.getObjectId())
                    .objectKey(hit.getObjectKey())
                    .bucket(hit.getBucket())
                    .sizeBytes(hit.getSizeBytes())
                    .durationMs(hit.getDurationMs())
                    .cached(true)
                    .build();
        }

        log.info("TTS cache miss — calling ElevenLabs for question={} voice={} model={}",
                req.getQuestionId(), voiceId, modelId);

        byte[] audio = elevenLabs.synthesize(req.getText(), voiceId, modelId);

        UploadedQuestionAudio uploaded = storage.uploadQuestionAudio(req.getQuestionId(), audio, "audio/mpeg");

        TtsCacheEntry entry = TtsCacheEntry.builder()
                .objectId(uploaded.getObjectId())
                .objectKey(uploaded.getObjectKey())
                .bucket(uploaded.getBucket())
                .sizeBytes(uploaded.getSizeBytes())
                .build();
        cache.put(hash, entry);

        return TtsSynthesizeResponse.builder()
                .objectId(uploaded.getObjectId())
                .objectKey(uploaded.getObjectKey())
                .bucket(uploaded.getBucket())
                .sizeBytes(uploaded.getSizeBytes())
                .cached(false)
                .build();
    }
}
