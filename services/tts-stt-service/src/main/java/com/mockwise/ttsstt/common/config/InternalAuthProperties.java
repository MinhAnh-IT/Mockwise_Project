package com.mockwise.ttsstt.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.auth")
public record InternalAuthProperties(String apiKey) {}
