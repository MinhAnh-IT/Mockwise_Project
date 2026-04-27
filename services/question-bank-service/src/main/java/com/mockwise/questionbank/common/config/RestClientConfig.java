package com.mockwise.questionbank.common.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean(name = "ttsRestClient")
    public RestClient ttsRestClient(TtsClientProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        return RestClient.builder()
                .baseUrl(props.baseUrl() + "/api/v1/tts-stt")
                .defaultHeader("X-Internal-Auth", props.internalApiKey())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
