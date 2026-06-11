package com.mockwise.practice.client.questionbank.config;

import com.mockwise.practice.common.exception.BusinessException;
import com.mockwise.practice.common.exception.StatusCode;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * question-bank's {@code /internal/...} endpoints expect {@code X-Internal-Auth}.
 * We always inject it (harmless on public endpoints) and never retry — a failed
 * catalog read surfaces as a clean 502 rather than a silent hang.
 */
public class QuestionBankFeignConfig {

    @Value("${internal.auth.api-key:}")
    private String internalApiKey;

    @Bean
    public Logger.Level questionBankFeignLogger() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options questionBankOptions() {
        return new Request.Options(
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                true);
    }

    @Bean
    public Retryer questionBankRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public RequestInterceptor questionBankInternalAuthInjector() {
        return template -> template.header("X-Internal-Auth", internalApiKey);
    }

    /**
     * Translate question-bank HTTP errors into domain exceptions: a 404 means
     * the problem is unknown or not ACTIVE (→ user-facing 404); anything else
     * is an upstream fault (→ 502). Without this, Feign's default decoder would
     * raise a generic {@code FeignException} for the not-found case too.
     */
    @Bean
    public ErrorDecoder questionBankErrorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new BusinessException(StatusCode.PROBLEM_NOT_FOUND);
            }
            return new BusinessException(StatusCode.QUESTION_BANK_UNAVAILABLE);
        };
    }
}
