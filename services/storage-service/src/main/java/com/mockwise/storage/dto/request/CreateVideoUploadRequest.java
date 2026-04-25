package com.mockwise.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateVideoUploadRequest {

    @NotBlank
    String sessionId;

    @NotBlank
    String contentType;

    @NotNull
    @Positive
    Long sizeBytes;
}
