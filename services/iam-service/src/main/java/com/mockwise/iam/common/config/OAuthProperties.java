package com.mockwise.iam.common.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Client credentials for social-login providers. Secrets come from the
 * environment (see application.yml {@code app.oauth.*}); never commit them.
 */
@Getter
@Setter
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    Provider google = new Provider();
    Provider github = new Provider();

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Provider {
        String clientId;
        String clientSecret;
    }
}
