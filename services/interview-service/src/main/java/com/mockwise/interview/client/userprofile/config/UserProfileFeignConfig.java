package com.mockwise.interview.client.userprofile.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.common.exception.StatusCode;
import com.mockwise.interview.common.feign.UpstreamErrorDecoder;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class UserProfileFeignConfig {

    @Bean
    public UpstreamErrorDecoder userProfileErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new UpstreamErrorDecoder(mapper, StatusCode.USER_PROFILE_UNAVAILABLE);
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
     * user-profile's UserContextFilter expects X-User-* even though it
     * permits anonymous reads — without these the request log is empty
     * and we lose the trace of who triggered the call.
     */
    @Bean
    public RequestInterceptor interviewServiceIdentityInjector() {
        return template -> {
            template.header("X-User-Id", "mockwise-interview-service");
            template.header("X-User-Role", "SERVICE");
            template.header("X-User-Email", "interview-service@mockwise.dev");
        };
    }
}
