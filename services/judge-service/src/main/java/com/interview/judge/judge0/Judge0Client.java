package com.interview.judge.judge0;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Judge0Client {

    // Judge0 language IDs
    static final int LANG_JAVA       = 62;  // Java (OpenJDK 13.0.1)
    static final int LANG_JAVASCRIPT = 63;  // JavaScript (Node.js 12.14.0)
    static final int LANG_CPP        = 54;  // C++ (GCC 9.2.0)
    static final int LANG_PYTHON     = 71;  // Python (3.8.1)

    static final int MEMORY_LIMIT = 512000;

    final WebClient webClient;

    @Value("${judge0.base-url}")
    String baseUrl;

    @Value("${judge0.auth-token:}")
    String authToken;

    /** Retry attempts for transient Judge0 submit failures (queue full / 503, conn errors). */
    @Value("${judge0.submit-max-retries:6}")
    int submitMaxRetries;

    // One submission now runs ALL of a job's cases in a single process (compile
    // once), so these caps cover the WHOLE batch, not one case. MUST be ≤ the
    // MAX_CPU_TIME_LIMIT / MAX_WALL_TIME_LIMIT in judge0.conf or Judge0 rejects
    // the submission with HTTP 422 — keep them in sync when tuning the VPS.

    /** Per-batch CPU-seconds cap. */
    @Value("${judge0.cpu-time-limit:20}")
    double cpuTimeLimit;

    /** Per-batch wall-clock cap; explicit so we don't fall back to Judge0's 10s default. */
    @Value("${judge0.wall-time-limit:30}")
    double wallTimeLimit;

    public Judge0Client(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Submits code to Judge0 asynchronously. Judge0 will POST the result
     * to {@code callbackUrl} when execution finishes.
     *
     * @param sourceCode  raw source code (not base64) — language matches {@code language}
     * @param stdin       raw stdin string (not base64)
     * @param callbackUrl URL for Judge0 to POST the result back
     * @param language    submission language ({@code "java"} or {@code "python"})
     * @return Judge0 submission token
     */
    public String submitAsync(String sourceCode, String stdin, String callbackUrl, String language) {
        int languageId = resolveLanguageId(language);

        String encodedSource = Base64.getEncoder().encodeToString(sourceCode.getBytes(StandardCharsets.UTF_8));
        String encodedStdin  = Base64.getEncoder().encodeToString(stdin.getBytes(StandardCharsets.UTF_8));

        Judge0SubmissionRequest request = Judge0SubmissionRequest.builder()
                .sourceCode(encodedSource)
                .languageId(languageId)
                .stdin(encodedStdin)
                .callbackUrl(callbackUrl)
                .cpuTimeLimit(cpuTimeLimit)
                .wallTimeLimit(wallTimeLimit)
                .memoryLimit(MEMORY_LIMIT)
                .build();

        log.debug("Submitting to Judge0: languageId={}, callbackUrl={}", languageId, callbackUrl);

        // Judge0 returns 503 "queue is full" (and may drop connections) when its
        // worker queue saturates under a burst of per-test-case submissions. Such
        // failures are transient: retry with exponential backoff + jitter rather
        // than letting the caller mark the case RE. Non-transient errors (e.g. 4xx
        // for a malformed request) are not retried and surface immediately.
        Judge0SubmissionResponse response = webClient.post()
                .uri(baseUrl + "/submissions?base64_encoded=true&wait=false")
                .headers(headers -> {
                    if (authToken != null && !authToken.isBlank()) {
                        headers.set("X-Auth-Token", authToken);
                    }
                })
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Judge0SubmissionResponse.class)
                .retryWhen(Retry.backoff(submitMaxRetries, Duration.ofMillis(250))
                        .maxBackoff(Duration.ofSeconds(4))
                        .jitter(0.5)
                        .filter(Judge0Client::isTransient)
                        .doBeforeRetry(rs -> log.warn(
                                "Judge0 submit transient failure (attempt {}/{}), retrying: {}",
                                rs.totalRetries() + 1, submitMaxRetries,
                                rs.failure().getMessage()))
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .block();

        if (response == null || response.getToken() == null) {
            throw new IllegalStateException("Judge0 returned null token for callbackUrl=" + callbackUrl);
        }

        log.info("Judge0 token received: {}", response.getToken());
        return response.getToken();
    }

    /**
     * Whether a Judge0 submit failure is worth retrying. Covers queue-full /
     * overload responses (429/502/503/504) and transport-level failures
     * (connection refused/reset, premature close) which are inherently
     * transient. A 4xx (other than 429) means the request itself is bad, so we
     * do not retry it.
     */
    private static boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException wcre) {
            int code = wcre.getStatusCode().value();
            return code == 429 || code == 502 || code == 503 || code == 504;
        }
        return t instanceof WebClientRequestException;
    }

    private int resolveLanguageId(String language) {
        return switch (language.toLowerCase()) {
            case "java"       -> LANG_JAVA;
            case "javascript",
                 "js"         -> LANG_JAVASCRIPT;
            case "cpp",
                 "c++"        -> LANG_CPP;
            case "python",
                 "py"         -> LANG_PYTHON;
            default -> throw new IllegalArgumentException("Unsupported language: " + language +
                    ". Supported: java, javascript, cpp, python");
        };
    }
}
