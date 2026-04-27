package com.mockwise.ttsstt.tts.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TtsSynthesizeResponse {
    String objectId;
    String objectKey;
    String bucket;
    Long sizeBytes;
    Integer durationMs;
    boolean cached;
}
