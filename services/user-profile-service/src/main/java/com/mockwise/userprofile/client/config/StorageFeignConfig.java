package com.mockwise.userprofile.client.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageFeignConfig {

    @Value("${mockwise.storage.internal-api-key:}")
    private String internalApiKey;

    /**
     * Storage-service `/internal/**` endpoints require an X-Internal-Auth
     * shared secret. Inject it on every outgoing call so the calling service
     * never has to remember to set it.
     */
    @Bean
    public RequestInterceptor storageInternalAuth() {
        return template -> {
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                template.header("X-Internal-Auth", internalApiKey);
            }
        };
    }
}
