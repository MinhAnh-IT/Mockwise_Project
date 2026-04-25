package com.mockwise.storage.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.storage.dto.request.InternalDownloadUrlRequest;
import com.mockwise.storage.dto.response.PresignedUrlResponse;
import com.mockwise.storage.dto.response.QuestionAudioUploadResponse;
import com.mockwise.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service-to-service endpoints. Authenticated by {@code X-Internal-Auth} —
 * never reachable directly from the public network through api-gateway
 * (the gateway rejects any path containing {@code /internal/}).
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalStorageController {

    StorageService storageService;

    /** tts-stt pushes a generated audio file here after admin creates a question. */
    @PostMapping(value = "/question-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuestionAudioUploadResponse>> uploadQuestionAudio(
            @RequestParam("questionId") String questionId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                storageService.uploadQuestionAudio(questionId, file)));
    }

    /** interview-service requests a short-lived GET URL after verifying session ACL. */
    @PostMapping("/download-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> createDownloadUrl(
            @Valid @RequestBody InternalDownloadUrlRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                storageService.createDownloadUrl(req)));
    }
}
