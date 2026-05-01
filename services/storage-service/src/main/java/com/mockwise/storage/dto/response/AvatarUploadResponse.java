package com.mockwise.storage.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvatarUploadResponse {
    String objectId;
    String bucket;
    String objectKey;
    String uploadUrl;
    OffsetDateTime expiresAt;
}
