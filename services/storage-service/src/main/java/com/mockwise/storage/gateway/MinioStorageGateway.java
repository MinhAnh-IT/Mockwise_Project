package com.mockwise.storage.gateway;

import com.mockwise.storage.common.config.MinioClientConfig;
import com.mockwise.storage.common.config.MinioProperties;
import com.mockwise.storage.common.exception.BusinessException;
import com.mockwise.storage.common.exception.StatusCode;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around MinIO SDK.
 *
 * <p>Hides MinIO checked exceptions behind {@link BusinessException} so service code stays clean,
 * and centralizes the rule that presigned URLs are signed using {@code minioPublicClient}
 * (so the browser-facing host is correct), while reads/writes use the internal client.
 */
@Slf4j
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MinioStorageGateway {

    MinioClient minioClient;
    MinioClient minioPublicClient;
    MinioProperties properties;

    public MinioStorageGateway(
            @Qualifier(MinioClientConfig.INTERNAL_CLIENT) MinioClient minioClient,
            @Qualifier(MinioClientConfig.PUBLIC_CLIENT) MinioClient minioPublicClient,
            MinioProperties properties) {
        this.minioClient = minioClient;
        this.minioPublicClient = minioPublicClient;
        this.properties = properties;
    }

    /** Sign a PUT URL the browser will use to upload bytes directly to MinIO. */
    public String presignPutUrl(String bucket, String objectKey, int ttlSeconds) {
        try {
            String signed = minioPublicClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS)
                            .build());
            return rewriteProxyUrl(signed);
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            log.error("Failed to presign PUT for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }
    }

    /** Sign a GET URL the browser/player will use to download bytes directly from MinIO. */
    public String presignGetUrl(String bucket, String objectKey, int ttlSeconds) {
        try {
            String signed = minioPublicClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS)
                            .build());
            return rewriteProxyUrl(signed);
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            log.error("Failed to presign GET for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }
    }

    /**
     * Swap the SDK-signed URL's host prefix (the upstream MinIO endpoint) for
     * the public-facing nginx proxy URL. The SigV4 signature is over the
     * <em>host header MinIO sees</em>, so we don't re-sign — nginx must
     * forward the request with {@code Host: <publicEndpoint host>} so the
     * upstream sees the same canonical request the SDK signed.
     *
     * <p>Returns the URL unchanged when the proxy base URL is not configured
     * (local dev where the browser hits MinIO directly).
     */
    private String rewriteProxyUrl(String signedUrl) {
        String proxyBase = properties.proxyPublicBaseUrl();
        if (proxyBase == null || proxyBase.isBlank()) {
            return signedUrl;
        }
        String prefix = stripTrailingSlash(properties.publicEndpoint());
        if (prefix == null || prefix.isBlank() || !signedUrl.startsWith(prefix)) {
            log.warn("Signed URL does not start with public endpoint prefix {}; returning unmodified",
                    prefix);
            return signedUrl;
        }
        return stripTrailingSlash(proxyBase) + signedUrl.substring(prefix.length());
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return null;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Look up an object's metadata. Empty when not found; throws on transport errors. */
    public Optional<StatObjectResponse> stat(String bucket, String objectKey) {
        try {
            return Optional.of(minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()));
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return Optional.empty();
            }
            log.error("Stat failed for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            log.error("Stat failed for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }
    }

    /**
     * Open an InputStream on a stored object for server-side proxying back to
     * the client. Used by the avatar-streaming endpoint so the browser never
     * sees the MinIO host (avoids HTTPS-app + HTTP-MinIO mixed-content blocks).
     */
    public GetObjectResponse getObject(String bucket, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            log.error("Get failed for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }
    }

    /** Server-side put used by service-to-service uploads (e.g. tts-stt). */
    public void putObject(String bucket, String objectKey, InputStream stream, long sizeBytes, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(stream, sizeBytes, -1)
                            .contentType(contentType)
                            .build());
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            log.error("Put failed for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new BusinessException(StatusCode.STORAGE_BACKEND_ERROR);
        }
    }

    /** Best-effort delete; logs but does not throw if the object is already gone. */
    public void removeObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            log.warn("Remove failed for {}/{}: {}", bucket, objectKey, e.getMessage());
        }
    }
}
