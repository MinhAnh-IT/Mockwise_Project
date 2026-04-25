package com.mockwise.storage.dto.response;

import com.mockwise.storage.entity.StorageObject;
import com.mockwise.storage.enums.StorageKind;
import com.mockwise.storage.enums.StorageStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StorageObjectResponse {
    String objectId;
    StorageKind kind;
    String bucket;
    String objectKey;
    String contentType;
    Long sizeBytes;
    StorageStatus status;
    String sessionId;
    String questionId;
    OffsetDateTime createdAt;
    OffsetDateTime completedAt;

    public static StorageObjectResponse from(StorageObject o) {
        return StorageObjectResponse.builder()
                .objectId(o.getId())
                .kind(o.getKind())
                .bucket(o.getBucket())
                .objectKey(o.getObjectKey())
                .contentType(o.getContentType())
                .sizeBytes(o.getSizeBytes())
                .status(o.getStatus())
                .sessionId(o.getSessionId())
                .questionId(o.getQuestionId())
                .createdAt(o.getCreatedAt())
                .completedAt(o.getCompletedAt())
                .build();
    }
}
