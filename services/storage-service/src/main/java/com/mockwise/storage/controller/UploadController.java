package com.mockwise.storage.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.storage.common.security.CustomUserDetails;
import com.mockwise.storage.dto.request.CreateVideoUploadRequest;
import com.mockwise.storage.dto.response.StorageObjectResponse;
import com.mockwise.storage.dto.response.VideoUploadResponse;
import com.mockwise.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UploadController {

    StorageService storageService;

    /** Step 1: client asks for a presigned PUT URL to upload video bytes. */
    @PostMapping("/videos")
    public ResponseEntity<ApiResponse<VideoUploadResponse>> createVideoUpload(
            @Valid @RequestBody CreateVideoUploadRequest req,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                storageService.createVideoUpload(req, user.getUserId())));
    }

    /** Step 2: client signals upload finished — service verifies and flips status to READY. */
    @PostMapping("/videos/{objectId}/complete")
    public ResponseEntity<ApiResponse<StorageObjectResponse>> completeVideoUpload(
            @PathVariable String objectId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                storageService.completeVideoUpload(objectId, user.getUserId())));
    }

    /**
     * Multipart avatar upload. The browser POSTs the file to this endpoint and
     * we stream it through to MinIO server-side, sidestepping the mixed-content
     * + CORS issues that came with the previous browser-direct presigned PUT
     * (HTTPS app → HTTP MinIO host). Avatars are capped at 5 MB so the extra
     * server hop is cheap.
     */
    @PostMapping(value = "/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StorageObjectResponse>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                storageService.uploadAvatar(file, user.getUserId())));
    }

    /**
     * Single-step interview-video upload. Replaces the 3-step presigned-PUT
     * dance ({@code /videos} → MinIO PUT → {@code /complete}) for browser
     * clients, which the dance broke for: the MinIO host is HTTP-only and the
     * app is served over HTTPS, so the browser blocks the cross-origin PUT
     * with a Mixed Content error.
     *
     * <p>Returns the stored object with {@code status = READY} so the FE can
     * forward {@code objectId} straight to interview-service. Server-to-server
     * callers (e.g. tts-stt warm-up) keep using the presigned-PUT path; this
     * endpoint is purely for the user-facing recorder.
     */
    @PostMapping(value = "/videos/multipart", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StorageObjectResponse>> uploadVideoMultipart(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sessionId") String sessionId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                storageService.uploadVideoMultipart(file, sessionId, user.getUserId())));
    }
}
