package com.mockwise.storage.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Finalises a streaming video upload: the browser has PUT {@code partCount}
 * ordered parts (1..N), and the service composes them into the final object.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteStreamingUploadRequest {

    @NotNull
    @Min(1)
    Integer partCount;
}
