package com.mockwise.interview.client.ttsstt.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.common.feign.UpstreamErrorDecoder;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Per-client Feign config. Same auth scheme as storage / question-bank
 * (X-Internal-Auth) because tts-stt's transcript-fetch endpoint is also
 * under /internal/.
 */
public class TtsSttFeignConfig {

    @Value("${internal.auth.api-key:}")
    private String internalApiKey;

    @Bean
    public UpstreamErrorDecoder ttsSttErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new UpstreamErrorDecoder(mapper, StatusCode.UPSTREAM_RESPONSE_UNPARSABLE);
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

    @Bean
    public RequestInterceptor ttsSttInternalAuthInjector() {
        return template -> template.header("X-Internal-Auth", internalApiKey);
    }
}
