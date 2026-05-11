package com.mockwise.storage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Internal: ask for a presigned GET URL for an interview-video by its
 * storage object id, with an ownership check enforced on the storage side
 * (the caller passes the user id it has already authenticated; we still
 * verify it matches the row's {@code ownerUserId} as defense in depth).
 *
 * <p>{@code ttlSeconds} is capped server-side by
 * {@code minio.presign.download-ttl-seconds}.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalVideoDownloadUrlRequest {

    @NotBlank
    String ownerUserId;

    @Positive
    Integer ttlSeconds;
}
