package com.mockwise.interview.client.questionbank.config;

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
 * Question-bank's {@code /internal/...} endpoints expect
 * {@code X-Internal-Auth}. The same header on public endpoints is harmless,
 * so we always inject it — keeps the wire-up boring and avoids an
 * accidentally-anonymous call slipping through.
 */
@Configuration
public class QuestionBankFeignConfig {

    @Value("${internal.auth.api-key:}")
    private String internalApiKey;

    @Bean
    public UpstreamErrorDecoder questionBankErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new UpstreamErrorDecoder(mapper, StatusCode.QUESTION_BANK_UNAVAILABLE);
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
    public RequestInterceptor questionBankInternalAuthInjector() {
        return template -> template.header("X-Internal-Auth", internalApiKey);
    }
}
