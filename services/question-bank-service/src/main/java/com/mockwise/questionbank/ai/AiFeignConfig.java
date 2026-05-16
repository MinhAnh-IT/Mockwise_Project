package com.mockwise.questionbank.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.questionbank.common.config.AiClientProperties;
import com.mockwise.questionbank.common.exception.BusinessException;
import com.mockwise.questionbank.common.exception.StatusCode;
import feign.Request;
import feign.RequestInterceptor;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Feign configuration for {@link AiGenerationFeignClient}.
 *
 * <p>Intentionally not annotated {@code @Configuration} so these beans stay
 * scoped to this single Feign client rather than becoming application-wide
 * Feign defaults. It attaches the server-only API key, applies timeouts, and
 * translates the AI service's FastAPI {@code {"detail": ...}} errors into a
 * {@link BusinessException} carrying the appropriate {@link StatusCode}.
 */
@Slf4j
public class AiFeignConfig {

    @Bean
    public RequestInterceptor aiApiKeyInterceptor(AiClientProperties props) {
        return template -> {
            template.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            if (props.apiKey() != null && !props.apiKey().isBlank()) {
                template.header("X-API-Key", props.apiKey());
            }
        };
    }

    @Bean
    public Request.Options aiRequestOptions(AiClientProperties props) {
        return new Request.Options(
                props.connectTimeoutMs(), TimeUnit.MILLISECONDS,
                props.readTimeoutMs(), TimeUnit.MILLISECONDS,
                true);
    }

    @Bean
    public ErrorDecoder aiErrorDecoder(ObjectMapper objectMapper) {
        return new AiErrorDecoder(objectMapper);
    }

    /** Maps a non-2xx AI response to a {@link BusinessException}. */
    static final class AiErrorDecoder implements ErrorDecoder {

        private final ObjectMapper objectMapper;

        AiErrorDecoder(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public Exception decode(String methodKey, Response response) {
            int status = response.status();
            String detail = readDetail(response);
            log.warn("AI generate-testcases failed: status={} detail={}", status, detail);

            String hint = detail == null ? "" : detail.toLowerCase(Locale.ROOT);

            if (status == 401) {
                return new BusinessException(StatusCode.AI_KEY_MISCONFIGURED);
            }
            if (status == 403 || hint.contains("premium")) {
                return new BusinessException(StatusCode.AI_LEETCODE_PREMIUM);
            }
            if (status == 404 || hint.contains("not_found") || hint.contains("not found")) {
                return new BusinessException(StatusCode.AI_LEETCODE_NOT_FOUND);
            }
            if (status == 400 || status == 422) {
                return new BusinessException(StatusCode.AI_INVALID_INPUT,
                        detail == null ? "validation error" : detail);
            }
            return new BusinessException(StatusCode.AI_GENERATION_FAILED,
                    detail == null ? "upstream error " + status : detail);
        }

        /** FastAPI errors are {@code {"detail": "msg"}} or {@code {"detail": {..}}}. */
        private String readDetail(Response response) {
            if (response.body() == null) {
                return null;
            }
            try {
                String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
                if (body == null || body.isBlank()) {
                    return null;
                }
                JsonNode detail = objectMapper.readTree(body).path("detail");
                if (detail.isMissingNode() || detail.isNull()) {
                    return null;
                }
                if (detail.isTextual()) {
                    return detail.asText();
                }
                String message = detail.path("message").asText(null);
                if (message != null) {
                    return message;
                }
                String error = detail.path("error").asText(null);
                return error != null ? error : detail.toString();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
