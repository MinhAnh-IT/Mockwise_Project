package com.mockwise.interview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.auth")
public record InternalAuthProperties(String apiKey) {}
