package com.mockwise.practice.common.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BusinessException extends RuntimeException {

    int code;
    int httpStatus;

    public BusinessException(StatusCode statusCode) {
        super(statusCode.getMessage());
        this.code = statusCode.getCode();
        this.httpStatus = statusCode.getHttpStatus();
    }

    public BusinessException(StatusCode statusCode, Object... args) {
        super(statusCode.formatMessage(args));
        this.code = statusCode.getCode();
        this.httpStatus = statusCode.getHttpStatus();
    }
}
