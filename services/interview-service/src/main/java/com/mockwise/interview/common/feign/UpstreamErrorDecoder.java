package com.mockwise.interview.common.feign;

import com.core.apiresponse.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.interview.common.exception.BusinessException;
import com.mockwise.interview.common.exception.StatusCode;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Generic Feign error decoder for downstream services that return our
 * {@code ApiResponse} envelope. Mirrors the pattern in
 * {@code iam-service/.../ProfileErrorDecoder}: parse the body, prefer the
 * upstream's {@code code} + {@code message}, fall back to the HTTP status.
 *
 * <p>Each Feign client config wires this with a fallback {@link StatusCode}
 * to use when the body is empty / unparsable — that's how a 502 from an
 * unreachable storage-service still produces a clean
 * {@code STORAGE_UNAVAILABLE} business exception instead of a raw
 * Jackson stack trace.
 */
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UpstreamErrorDecoder implements ErrorDecoder {

    ObjectMapper mapper;
    StatusCode unavailableFallback;

    public UpstreamErrorDecoder(ObjectMapper mapper, StatusCode unavailableFallback) {
        this.mapper = mapper;
        this.unavailableFallback = unavailableFallback;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int httpStatus = response.status();
        String body = readBody(response);

        if (body != null && !body.isBlank()) {
            try {
                ApiResponse<JsonNode> api = mapper.readValue(
                        body, new TypeReference<ApiResponse<JsonNode>>() {});
                int code = api.getCode() != 0 ? api.getCode() : httpStatus;
                String message = (api.getMessage() != null && !api.getMessage().isBlank())
                        ? api.getMessage()
                        : "Upstream error (" + httpStatus + ")";
                return new BusinessException(code, message, httpStatus);
            } catch (IOException e) {
                log.warn("Could not parse upstream ApiResponse from {} ({} bytes): {}",
                        methodKey, body.length(), e.getMessage());
                return new BusinessException(StatusCode.UPSTREAM_RESPONSE_UNPARSABLE);
            }
        }

        // Empty body — most likely a 5xx connection error or an upstream that
        // didn't bother with a payload. Fall back to the per-service "unavailable" code.
        return new BusinessException(unavailableFallback);
    }

    private static String readBody(Response response) {
        if (response.body() == null) return null;
        try {
            return Util.toString(response.body().asReader(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }
}
