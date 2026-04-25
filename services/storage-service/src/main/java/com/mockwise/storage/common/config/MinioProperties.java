package com.mockwise.storage.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO connection + bucket layout + presign TTLs + upload limits.
 *
 * <p>{@code endpoint} is the address used by storage-service from inside the cluster.
 * {@code publicEndpoint} is the address embedded in presigned URLs sent to the browser —
 * may differ when MinIO is exposed via nginx/CDN with a public hostname.
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String region,
        Buckets buckets,
        Presign presign,
        UploadLimits uploadLimits
) {
    public record Buckets(
            String interviewVideo,
            String questionAudio
    ) {}

    public record Presign(
            int uploadTtlSeconds,
            int downloadTtlSeconds,
            int audioTtlSeconds
    ) {}

    public record UploadLimits(
            long videoMaxBytes,
            long audioMaxBytes
    ) {}
}
