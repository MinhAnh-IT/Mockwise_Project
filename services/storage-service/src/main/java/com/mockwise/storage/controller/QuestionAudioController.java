package com.mockwise.storage.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-side question-audio endpoint. Lives at {@code /question-audio} so the
 * URL conveys a download.
 *
 * <p>Bytes are streamed server-side from MinIO so the browser, which loads
 * the app over HTTPS, never sees the HTTP-only MinIO host (mixed-content
 * blocking would otherwise stop {@code <audio>} from loading the TTS clip
 * for an interview question).
 *
 * <p>{@code objectKey} segments contain a slash ({@code audio/<uuid>}) so
 * the path variable uses {@code :.+} to capture the rest of the URL — Spring
 * otherwise stops at the first slash.
 */
@Slf4j
@RestController
@RequestMapping("/question-audio")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionAudioController {

    StorageService storageService;

    @GetMapping("/{objectKey:.+}")
    public ResponseEntity<InputStreamResource> getAudio(@PathVariable String objectKey) {
        AvatarStream audio = storageService.streamQuestionAudio(objectKey);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(audio.contentType()))
                // 1 hour cache — TTS clips are immutable per question; the
                // object key changes when the admin re-records.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
        if (audio.sizeBytes() > 0) {
            builder.contentLength(audio.sizeBytes());
        }
        return builder.body(new InputStreamResource(audio.stream()));
    }
}
