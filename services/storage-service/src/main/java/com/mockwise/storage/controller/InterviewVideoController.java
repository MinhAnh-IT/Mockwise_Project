package com.mockwise.storage.controller;

import com.mockwise.storage.common.security.CustomUserDetails;
import com.mockwise.storage.dto.response.AvatarStream;
import com.mockwise.storage.service.StorageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side endpoint for replaying a candidate's submitted answer video.
 * Mirrors {@link AvatarController} — bytes are streamed server-side from
 * MinIO so the browser, which loads the app over HTTPS, never touches the
 * HTTP-only MinIO host (mixed-content blocking).
 *
 * <p>Lookup is by {@code objectId} (the storage row PK) because that's what
 * interview-service knows when assembling its {@code AnswerView}. ACL is
 * enforced inside the service: the {@code ownerUserId} on the row must
 * match the authenticated user — api-gateway introspect already requires
 * a valid JWT for any non-public path, so the principal is trustworthy.
 */
@Slf4j
@RestController
@RequestMapping("/interview-videos")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InterviewVideoController {

    StorageService storageService;

    @GetMapping("/{objectId}")
    public ResponseEntity<InputStreamResource> getVideo(
            @PathVariable String objectId,
            @AuthenticationPrincipal CustomUserDetails user) {
        AvatarStream video = storageService.streamInterviewVideo(objectId, user.getUserId());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(video.contentType()))
                // Private cache only — different users mustn't share the
                // bytes through a shared proxy. 30 min matches the legacy
                // download-TTL the presigned MinIO path used to issue.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=1800");
        if (video.sizeBytes() > 0) {
            builder.contentLength(video.sizeBytes());
        }
        return builder.body(new InputStreamResource(video.stream()));
    }
}
