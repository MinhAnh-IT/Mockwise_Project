package com.mockwise.storage.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.storage.common.security.CustomUserDetails;
import com.mockwise.storage.dto.request.CreateVideoUploadRequest;
import com.mockwise.storage.dto.response.AvatarStream;
import com.mockwise.storage.dto.response.StorageObjectResponse;
import com.mockwise.storage.dto.response.VideoUploadResponse;
import com.mockwise.storage.service.StorageService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
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
     * Streams the authenticated user's latest avatar bytes back to the browser.
     *
     * <p>This avoids handing the browser a presigned MinIO URL — the app runs
     * over HTTPS but MinIO is HTTP-only on this VPS, so a direct {@code <img
     * src="http://...">} would be blocked as mixed content. Streaming through
     * here keeps the load same-origin.
     *
     * <p>{@code Cache-Control: private, max-age=600} matches the avatar TTL we
     * cap presigned URLs at; clients will revalidate after 10 minutes. The
     * frontend appends a {@code ?v=<key-suffix>} cache-buster so a re-upload
     * surfaces the new image immediately.
     */
    @GetMapping("/avatars/me")
    public ResponseEntity<InputStreamResource> getMyAvatar(
            @AuthenticationPrincipal CustomUserDetails user) {
        AvatarStream avatar = storageService.streamLatestAvatar(user.getUserId());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600");
        if (avatar.sizeBytes() > 0) {
            builder.contentLength(avatar.sizeBytes());
        }
        return builder.body(new InputStreamResource(avatar.stream()));
    }
}
