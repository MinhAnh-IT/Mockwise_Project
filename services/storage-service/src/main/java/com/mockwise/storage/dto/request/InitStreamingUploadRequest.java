package com.mockwise.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Opens a streaming (upload-while-recording) video upload. Unlike
 * {@link CreateVideoUploadRequest} there is no {@code sizeBytes} — the
 * total size is unknown until the recording stops, so it's derived from
 * the composed object at complete-time instead.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InitStreamingUploadRequest {

    @NotBlank
    String sessionId;

    @NotBlank
    String contentType;
}
