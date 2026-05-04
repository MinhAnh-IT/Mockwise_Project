package com.mockwise.interview.client.ai.config;

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
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * The AI service authenticates with {@code X-API-Key} (its own scheme,
 * separate from the {@code X-Internal-Auth} we use between Java services).
 * Read timeout is much higher than the other clients because
 * {@code POST /follow-up/generate} blocks on a Gemini call.
 */
@Configuration
public class AiServiceFeignConfig {

    @Value("${external.services.ai.api-key:}")
    private String aiApiKey;

    @Bean
    public UpstreamErrorDecoder aiServiceErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new UpstreamErrorDecoder(mapper, StatusCode.AI_SERVICE_UNAVAILABLE);
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options options() {
        // Generous read timeout: a follow-up generation typically takes
        // 1–4s, but tail-end Gemini calls can hit 15–20s under load. We'd
        // rather pay the wall clock than hand the user a generic error.
        return new Request.Options(
                Duration.ofSeconds(3),
                Duration.ofSeconds(30),
                true);
    }

    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public RequestInterceptor aiApiKeyInjector() {
        return template -> {
            if (aiApiKey != null && !aiApiKey.isBlank()) {
                template.header("X-API-Key", aiApiKey);
            }
        };
    }
}
