package com.mockwise.practice.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared secret for guarding {@code /internal/**} and signing outbound internal calls. */
@ConfigurationProperties(prefix = "internal.auth")
public record InternalAuthProperties(String apiKey) {}
