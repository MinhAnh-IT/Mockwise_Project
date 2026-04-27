package com.mockwise.ttsstt.tts.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.ttsstt.tts.dto.request.TtsSynthesizeRequest;
import com.mockwise.ttsstt.tts.dto.response.TtsSynthesizeResponse;
import com.mockwise.ttsstt.tts.service.TtsService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal: question-bank-service hits this synchronously when an admin creates
 * or updates a behavioral / core question. Returns the storage objectKey of the
 * generated mp3 so the caller can persist {@code audio_key} on the question row.
 */
@Slf4j
@RestController
@RequestMapping("/internal/tts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalTtsController {

    TtsService ttsService;

    @PostMapping
    public ResponseEntity<ApiResponse<TtsSynthesizeResponse>> synthesize(
            @Valid @RequestBody TtsSynthesizeRequest req) {
        log.info("TTS request: questionId={} textLen={} voiceId={}",
                req.getQuestionId(), req.getText().length(), req.getVoiceId());
        return ResponseEntity.ok(ApiResponse.success(ttsService.synthesize(req)));
    }
}
