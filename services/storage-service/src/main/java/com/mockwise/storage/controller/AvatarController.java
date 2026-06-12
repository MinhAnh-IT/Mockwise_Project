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
 * Read-side avatar endpoints. Lives at {@code /avatars} (separate from the
 * upload flow at {@code /uploads/avatars}) so the URL conveys a download, not
 * an upload.
 *
 * <p>Bytes are streamed server-side from MinIO so the browser, which loads
 * the app over HTTPS, never sees the HTTP-only MinIO host (mixed-content
 * blocking would otherwise stop {@code <img src>} from loading).
 */
@Slf4j
@RestController
@RequestMapping("/avatars")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AvatarController {

    StorageService storageService;

    @GetMapping("/me")
    public ResponseEntity<InputStreamResource> getMyAvatar(
            @AuthenticationPrincipal CustomUserDetails user) {
        return streamAvatar(user.getUserId(), "private, max-age=600");
    }

    /**
     * Another user's latest avatar, by id — backs public displays like the
     * practice leaderboard. Declared after {@code /me} so the literal path wins.
     * Avatars are display info, so any authenticated user may read one; a 404
     * (no avatar uploaded) lets the client fall back to an initials placeholder.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<InputStreamResource> getUserAvatar(@PathVariable String userId) {
        return streamAvatar(userId, "public, max-age=600");
    }

    private ResponseEntity<InputStreamResource> streamAvatar(String userId, String cacheControl) {
        AvatarStream avatar = storageService.streamLatestAvatar(userId);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, cacheControl);
        if (avatar.sizeBytes() > 0) {
            builder.contentLength(avatar.sizeBytes());
        }
        return builder.body(new InputStreamResource(avatar.stream()));
    }
}
