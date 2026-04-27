package com.mockwise.ttsstt.common.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean(name = "elevenLabsRestClient")
    public RestClient elevenLabsRestClient(ElevenLabsProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofMillis(Math.max(props.ttsTimeoutMs(), props.sttTimeoutMs())));
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("xi-api-key", props.apiKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Bean(name = "storageRestClient")
    public RestClient storageRestClient(StorageClientProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        return RestClient.builder()
                .baseUrl(props.baseUrl() + "/api/v1/storage")
                .defaultHeader("X-Internal-Auth", props.internalApiKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Bean(name = "minioDownloadRestClient")
    public RestClient minioDownloadRestClient() {
        // Untimed client for streaming binary content from MinIO presigned URLs.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofMinutes(10));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
