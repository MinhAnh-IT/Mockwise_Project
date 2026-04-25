package com.mockwise.storage.common.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioClientConfig {

    public static final String INTERNAL_CLIENT = "minioClient";
    public static final String PUBLIC_CLIENT = "minioPublicClient";

    private final MinioProperties properties;

    /** Internal client — uses the cluster-internal endpoint for read/write operations. */
    @Bean(INTERNAL_CLIENT)
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    /**
     * Public-endpoint client — used only to generate presigned URLs that browsers can resolve.
     * MinIO bakes the host into the signed URL, so the host must match what the client will hit.
     */
    @Bean(PUBLIC_CLIENT)
    public MinioClient minioPublicClient() {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    /**
     * Idempotent bucket bootstrap. Runs after all singletons are initialized so the
     * injected {@link MinioClient} is the same instance other beans use.
     *
     * <p>Failures here are logged but do not abort startup — that lets the service boot
     * even when MinIO is briefly unavailable, and any later request that needs the
     * bucket will surface the error to the caller.
     */
    @Bean
    SmartInitializingSingleton bucketInitializer(@Qualifier(INTERNAL_CLIENT) MinioClient client) {
        return () -> {
            ensureBucket(client, properties.buckets().interviewVideo());
            ensureBucket(client, properties.buckets().questionAudio());
        };
    }

    private void ensureBucket(MinioClient client, String name) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(name).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(name).build());
                log.info("Created MinIO bucket: {}", name);
            }
        } catch (MinioException | java.io.IOException
                 | java.security.NoSuchAlgorithmException
                 | java.security.InvalidKeyException e) {
            log.error("Failed to ensure bucket {}: {}", name, e.getMessage());
        }
    }
}
