package com.interview.judge.judge0;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
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

    static final double CPU_TIME_LIMIT = 5.0;
    static final int MEMORY_LIMIT = 256000;

    final WebClient webClient;

    @Value("${judge0.base-url}")
    String baseUrl;

    @Value("${judge0.auth-token:}")
    String authToken;

    public Judge0Client(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Submits code to Judge0 asynchronously. Judge0 will POST the result
     * to {@code callbackUrl} when execution finishes.
     *
     * @param sourceCode  raw Java source code (not base64)
     * @param stdin       raw stdin string (not base64)
     * @param callbackUrl URL for Judge0 to POST the result back
     * @param language    submission language (only "java" supported)
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
                .cpuTimeLimit(CPU_TIME_LIMIT)
                .memoryLimit(MEMORY_LIMIT)
                .build();

        log.debug("Submitting to Judge0: languageId={}, callbackUrl={}", languageId, callbackUrl);

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
                .block();

        if (response == null || response.getToken() == null) {
            throw new IllegalStateException("Judge0 returned null token for callbackUrl=" + callbackUrl);
        }

        log.info("Judge0 token received: {}", response.getToken());
        return response.getToken();
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
