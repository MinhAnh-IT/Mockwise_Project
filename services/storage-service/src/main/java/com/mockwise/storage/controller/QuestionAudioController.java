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
 * <p>Object keys are stored with a slash ({@code audio/<uuid>}). Using a
 * normal path-variable regex {@code {key:.+}} doesn't work in Spring 6 with
 * PathPatternParser — that engine matches segment-by-segment, so a single
 * variable never captures past the first slash. The {@code {*objectKey}}
 * "catch-all" syntax does, with the caveat that the captured value starts
 * with a leading {@code /} which we strip.
 */
@Slf4j
@RestController
@RequestMapping("/question-audio")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class QuestionAudioController {

    StorageService storageService;

    @GetMapping("/{*objectKey}")
    public ResponseEntity<InputStreamResource> getAudio(@PathVariable String objectKey) {
        // {*objectKey} captures the remainder including the leading "/".
        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        AvatarStream audio = storageService.streamQuestionAudio(key);

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
