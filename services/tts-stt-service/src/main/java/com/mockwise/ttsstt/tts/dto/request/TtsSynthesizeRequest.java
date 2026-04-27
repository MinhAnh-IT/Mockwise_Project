package com.mockwise.ttsstt.tts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TtsSynthesizeRequest {

    @NotBlank
    String questionId;

    @NotBlank
    @Size(max = 5000)
    String text;

    String voiceId;

    String modelId;

    String languageCode;
}
