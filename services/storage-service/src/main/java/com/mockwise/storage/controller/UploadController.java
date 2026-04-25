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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
}
