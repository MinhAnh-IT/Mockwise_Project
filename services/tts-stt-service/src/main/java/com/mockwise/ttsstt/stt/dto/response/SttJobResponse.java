package com.mockwise.ttsstt.stt.dto.response;

import com.mockwise.ttsstt.stt.entity.SttJob;
import com.mockwise.ttsstt.stt.entity.SttJobStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SttJobResponse {
    String jobId;
    SttJobStatus status;
    String storageObjectId;
    String answerId;
    String transcriptId;
    String errorCode;
    String errorMessage;
    int attemptCount;
    OffsetDateTime startedAt;
    OffsetDateTime finishedAt;

    public static SttJobResponse from(SttJob j) {
        return SttJobResponse.builder()
                .jobId(j.getId())
                .status(j.getStatus())
                .storageObjectId(j.getStorageObjectId())
                .answerId(j.getAnswerId())
                .transcriptId(j.getTranscriptId())
                .errorCode(j.getErrorCode())
                .errorMessage(j.getErrorMessage())
                .attemptCount(j.getAttemptCount())
                .startedAt(j.getStartedAt())
                .finishedAt(j.getFinishedAt())
                .build();
    }
}
