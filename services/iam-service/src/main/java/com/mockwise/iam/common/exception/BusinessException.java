package com.mockwise.iam.common.exception;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.iam.common.util.StatusCode;
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

    public BusinessException(ResponseCode responseCode) {
        super(responseCode.getDefaultMessage());
        this.code = responseCode.getCode();
        this.httpStatus = responseCode.getHttpStatus();
    }

    public BusinessException(ResponseCode responseCode, String message) {
        super(message);
        this.code = responseCode.getCode();
        this.httpStatus = responseCode.getHttpStatus();
    }

    public BusinessException(int code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = 500;
    }
}
