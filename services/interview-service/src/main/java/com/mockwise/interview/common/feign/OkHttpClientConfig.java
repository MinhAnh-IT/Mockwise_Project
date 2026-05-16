package com.mockwise.interview.common.feign;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Shared OkHttp client backing every Feign client (enabled via
 * {@code spring.cloud.openfeign.okhttp.enabled=true}).
 *
 * <p>The default Feign transport ({@code HttpURLConnection}) opens a
 * fresh TCP connection per call. With ~7-10 internal REST hops per
 * answer (storage, user-profile, tts-stt, question-bank ×N) that's
 * 5-20ms of avoidable setup each. A keep-alive connection pool reuses
 * sockets across calls so warm hops cost ~0ms of transport.
 *
 * <p>Per-client connect/read timeouts stay authoritative: Spring Cloud
 * OpenFeign rebuilds a per-request client from this base applying each
 * client's {@code Request.Options} (e.g. {@code QuestionBankFeignConfig}),
 * while the {@link ConnectionPool} below is shared across all of them.
 */
@Configuration
public class OkHttpClientConfig {

    @Bean
    public OkHttpClient feignOkHttpClient() {
        return new OkHttpClient.Builder()
                // 50 idle sockets, evicted after 5min idle — comfortably
                // covers the handful of internal services we fan out to
                // even under concurrent load.
                .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
                // Safety-net timeouts; individual Feign clients override
                // these via their Request.Options bean.
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
    }
}
