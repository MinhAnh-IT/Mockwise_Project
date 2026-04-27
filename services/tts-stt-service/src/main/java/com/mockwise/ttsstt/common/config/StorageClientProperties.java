package com.mockwise.ttsstt.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage-client")
public record StorageClientProperties(
        String baseUrl,
        String internalApiKey,
        int downloadTtlSeconds,
        int connectTimeoutMs,
        int readTimeoutMs
) {}
