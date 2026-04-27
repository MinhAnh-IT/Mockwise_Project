package com.mockwise.ttsstt.tts.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TtsCacheEntry {
    String objectId;
    String objectKey;
    String bucket;
    Long sizeBytes;
    Integer durationMs;
}
