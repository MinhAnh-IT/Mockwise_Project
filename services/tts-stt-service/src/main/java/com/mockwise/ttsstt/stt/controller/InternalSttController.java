package com.mockwise.ttsstt.stt.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.ttsstt.common.exception.BusinessException;
import com.mockwise.ttsstt.common.exception.StatusCode;
import com.mockwise.ttsstt.stt.dto.request.CreateTranscriptRequest;
import com.mockwise.ttsstt.stt.dto.response.RealtimeSttTokenResponse;
import com.mockwise.ttsstt.stt.dto.response.SttJobResponse;
import com.mockwise.ttsstt.stt.dto.response.TranscriptResponse;
import com.mockwise.ttsstt.stt.entity.SttJob;
import com.mockwise.ttsstt.stt.kafka.event.AnswerSubmittedEvent;
import com.mockwise.ttsstt.stt.repository.SttJobRepository;
import com.mockwise.ttsstt.stt.repository.TranscriptRepository;
import com.mockwise.ttsstt.stt.service.RealtimeSttTokenClient;
import com.mockwise.ttsstt.stt.service.SttOrchestrator;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal STT endpoints — fallback REST path used by interview-service
 * (admin tooling, replay) when the Kafka path is unavailable. The primary STT
 * trigger remains the {@code answer-submitted} Kafka topic.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalSttController {

    SttOrchestrator orchestrator;
    SttJobRepository jobRepo;
    TranscriptRepository transcriptRepo;
    RealtimeSttTokenClient realtimeTokenClient;

    /**
     * Mints a single-use ElevenLabs realtime Scribe token for the Lever 2 fast
     * path. interview-service relays the result to the authenticated browser,
     * which connects straight to the ElevenLabs WebSocket — our API key stays
     * here. POST (not GET) because each call consumes a fresh token.
     */
    @PostMapping("/realtime-stt-token")
    public ResponseEntity<ApiResponse<RealtimeSttTokenResponse>> mintRealtimeToken() {
        return ResponseEntity.ok(ApiResponse.success(
                RealtimeSttTokenResponse.from(realtimeTokenClient.mint())));
    }

    @PostMapping("/transcripts")
    public ResponseEntity<ApiResponse<SttJobResponse>> createTranscript(
            @Valid @RequestBody CreateTranscriptRequest req) {

        log.info("STT replay request: storageObjectId={} answerId={} force={}",
                req.getStorageObjectId(), req.getAnswerId(), req.isForce());

        AnswerSubmittedEvent event = AnswerSubmittedEvent.builder()
                .answerId(req.getAnswerId())
                .sessionId(req.getSessionId())
                .questionId(req.getQuestionId())
                .ownerUserId(req.getOwnerUserId())
                .storageObjectId(req.getStorageObjectId())
                .languageHint(req.getLanguageCode())
                .videoMeta(AnswerSubmittedEvent.VideoMeta.builder()
                        .objectKey(req.getObjectKey())
                        .contentType(req.getContentType())
                        .sizeBytes(req.getSizeBytes())
                        .build())
                .build();

        SttJob job = orchestrator.trigger(event, req.isForce());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(SttJobResponse.from(job)));
    }

    @GetMapping("/transcripts/{transcriptId}")
    public ResponseEntity<ApiResponse<TranscriptResponse>> getTranscript(
            @PathVariable String transcriptId) {
        return transcriptRepo.findById(transcriptId)
                .map(t -> ResponseEntity.ok(ApiResponse.success(TranscriptResponse.from(t))))
                .orElseThrow(() -> new BusinessException(StatusCode.RESOURCE_NOT_FOUND));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<SttJobResponse>> getJob(@PathVariable String jobId) {
        return jobRepo.findById(jobId)
                .map(j -> ResponseEntity.ok(ApiResponse.success(SttJobResponse.from(j))))
                .orElseThrow(() -> new BusinessException(StatusCode.RESOURCE_NOT_FOUND));
    }
}
