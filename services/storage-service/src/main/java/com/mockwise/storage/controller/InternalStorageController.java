package com.mockwise.storage.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.storage.dto.request.InternalDownloadUrlRequest;
import com.mockwise.storage.dto.request.InternalVideoDownloadUrlRequest;
import com.mockwise.storage.dto.response.PresignedUrlResponse;
import com.mockwise.storage.dto.response.QuestionAudioUploadResponse;
import com.mockwise.storage.dto.response.StorageObjectResponse;
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

    /**
     * Short-lived presigned GET URL for an interview-video by storage object
     * id. Lets the browser hit MinIO directly through the nginx /minio/
     * proxy instead of streaming bytes through this service. Ownership is
     * enforced here as well — interview-service has already done a session
     * ACL check, this is defense in depth.
     */
    @PostMapping("/objects/{id}/video-download-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> createVideoDownloadUrlById(
            @PathVariable("id") String id,
            @Valid @RequestBody InternalVideoDownloadUrlRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                storageService.createVideoDownloadUrlById(id, req)));
    }

    /**
     * Object lookup by id — interview-service hits this before pinning a
     * {@code storageObjectId} to an answer to verify ownership / READY
     * state / kind. Returns 404 (via STORAGE_OBJECT_NOT_FOUND in the
     * global error decoder) when the id doesn't exist.
     */
    @GetMapping("/objects/{id}")
    public ResponseEntity<ApiResponse<StorageObjectResponse>> getObject(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(storageService.getObjectById(id)));
    }
}
