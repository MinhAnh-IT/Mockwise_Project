package com.mockwise.iam.repository.userprofile.config;

import com.core.apiresponse.response.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockwise.iam.common.exception.BusinessException;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileErrorDecoder implements ErrorDecoder {

    ObjectMapper mapper;

    public ProfileErrorDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            String body = response.body() != null
                    ? Util.toString(response.body().asReader(StandardCharsets.UTF_8))
                    : null;

            int httpStatus = response.status();

            if (body != null && !body.isBlank()) {
                ApiResponse<JsonNode> api = mapper.readValue(
                        body, new TypeReference<ApiResponse<JsonNode>>() {});
                int code = api.getCode() != 0 ? api.getCode() : httpStatus;
                String message = (api.getMessage() != null && !api.getMessage().isBlank())
                        ? api.getMessage()
                        : "Upstream error (" + httpStatus + ")";
                return new BusinessException(code, message, httpStatus);
            }

            return new BusinessException(httpStatus, "Upstream error (" + httpStatus + ")", httpStatus);
        } catch (IOException e) {
            return new BusinessException(5999, "Unparsable upstream response");
        }
    }
}
