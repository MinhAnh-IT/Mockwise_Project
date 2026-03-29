package com.mockwise.iam.repository.userprofile.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ProfileFeignConfig {

    @Bean
    public ProfileErrorDecoder profileErrorDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new ProfileErrorDecoder(mapper);
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options options() {
        return new Request.Options(
                Duration.ofSeconds(2),   // connect timeout
                Duration.ofSeconds(5),   // read timeout
                true
        );
    }

    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public RequestInterceptor devIamHeaderInjector() {
        return requestTemplate -> {
            requestTemplate.header("X-User-Id", "mockwise-iam-service");
            requestTemplate.header("X-User-Role", "SERVICE");
            requestTemplate.header("X-User-Email", "iam-service@mockwise.dev");
        };
    }
}
