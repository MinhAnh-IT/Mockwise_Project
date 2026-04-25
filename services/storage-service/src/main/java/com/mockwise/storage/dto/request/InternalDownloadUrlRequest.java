package com.mockwise.storage.dto.request;

import com.mockwise.storage.enums.StorageKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Internal: ask for a presigned GET URL.
 *
 * <p>Caller must own ACL — this service trusts the caller (interview-service or similar)
 * after the X-Internal-Auth handshake. {@code ttlSeconds} is capped server-side per kind.
 */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalDownloadUrlRequest {

    @NotNull
    StorageKind kind;

    @NotBlank
    String objectKey;

    @NotNull
    @Positive
    Integer ttlSeconds;
}
