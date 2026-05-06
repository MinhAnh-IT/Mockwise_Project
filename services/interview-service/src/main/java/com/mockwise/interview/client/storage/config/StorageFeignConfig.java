package com.mockwise.interview.client.storage.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.common.feign.UpstreamErrorDecoder;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;


import java.time.Duration;


public class StorageFeignConfig {

    @Value("${internal.auth.api-key:}")
    private String internalApiKey;

    @Bean
    public UpstreamErrorDecoder storageErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new UpstreamErrorDecoder(mapper, StatusCode.STORAGE_UNAVAILABLE);
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options options() {
        return new Request.Options(
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                true);
    }

    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    /**
     * Storage's {@code /internal/...} routes are guarded by InternalAuthFilter
     * — without this header the call is dropped with a 401.
     */
    @Bean
    public RequestInterceptor storageInternalAuthInjector() {
        return template -> template.header("X-Internal-Auth", internalApiKey);
    }
}
