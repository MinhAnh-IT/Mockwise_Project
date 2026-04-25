package com.mockwise.storage.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.auth")
public record InternalAuthProperties(String apiKey) {}
