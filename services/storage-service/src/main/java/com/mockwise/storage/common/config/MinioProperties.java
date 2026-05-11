package com.mockwise.storage.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO connection + bucket layout + presign TTLs + upload limits.
 *
 * <p>{@code endpoint} is the address used by storage-service from inside the cluster.
 * {@code publicEndpoint} is the address SigV4 signs against — the host the upstream
 * MinIO sees when nginx forwards the request. Browsers never hit this directly:
 * presigned URLs are rewritten to start with {@code proxyPublicBaseUrl} so the
 * request goes through HTTPS nginx (which forwards to {@code publicEndpoint},
 * preserving the Host header so SigV4 still validates).
 *
 * <p>When {@code proxyPublicBaseUrl} is null/blank the URL is returned as the
 * SDK produced it — useful in local dev where the browser can hit MinIO directly.
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String proxyPublicBaseUrl,
        String accessKey,
        String secretKey,
        String region,
        Buckets buckets,
        Presign presign,
        UploadLimits uploadLimits
) {
    public record Buckets(
            String interviewVideo,
            String questionAudio,
            String userAvatar
    ) {}

    public record Presign(
            int uploadTtlSeconds,
            int downloadTtlSeconds,
            int audioTtlSeconds,
            int avatarTtlSeconds
    ) {}

    public record UploadLimits(
            long videoMaxBytes,
            long audioMaxBytes,
            long avatarMaxBytes
    ) {}
}
