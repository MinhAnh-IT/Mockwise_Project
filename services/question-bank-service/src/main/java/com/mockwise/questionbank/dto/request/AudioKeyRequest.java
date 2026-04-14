package com.mockwise.questionbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AudioKeyRequest {

    @NotBlank(message = "audioKey is required")
    String audioKey;
}
