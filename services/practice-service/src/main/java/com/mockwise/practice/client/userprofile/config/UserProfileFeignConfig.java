package com.mockwise.practice.client.userprofile.config;

import com.mockwise.practice.common.exception.BusinessException;
import com.mockwise.practice.common.exception.StatusCode;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * user-profile's {@code /profiles/**} reads require an authenticated caller. We
 * inject a SERVICE identity (mirroring interview-service) so its filter maps the
 * call to {@code ROLE_SERVICE}. Never retried — a profile lookup is best-effort
 * for the leaderboard and the caller degrades to placeholder names on failure.
 */
public class UserProfileFeignConfig {

    @Bean
    public Logger.Level userProfileFeignLogger() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options userProfileOptions() {
        return new Request.Options(
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                true);
    }

    @Bean
    public Retryer userProfileRetryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public RequestInterceptor userProfileIdentityInjector() {
        return template -> {
            template.header("X-User-Id", "mockwise-practice-service");
            template.header("X-User-Role", "SERVICE");
            template.header("X-User-Email", "practice-service@mockwise.dev");
        };
    }

    @Bean
    public ErrorDecoder userProfileErrorDecoder() {
        return (methodKey, response) -> new BusinessException(StatusCode.USER_PROFILE_UNAVAILABLE);
    }
}
