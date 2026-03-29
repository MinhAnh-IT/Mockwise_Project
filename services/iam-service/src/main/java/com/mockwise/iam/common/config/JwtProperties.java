package com.mockwise.iam.common.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    String issuer;
    String audience;
    long accessTtlSeconds;
    long refreshTtlSeconds;
    String activeKid;
    String jwtHmacSecret;
    int maxRefreshSessions;
    boolean cookieSecure = true; // Default to true for production, set to false for local dev
}
