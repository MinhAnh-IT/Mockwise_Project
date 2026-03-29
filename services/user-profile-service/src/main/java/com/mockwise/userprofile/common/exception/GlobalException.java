package com.mockwise.userprofile.common.exception;

import com.core.apiresponse.common.ResponseCode;
import com.core.apiresponse.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalException {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.failure(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().isEmpty()
                ? null
                : ex.getBindingResult().getFieldErrors().get(0);

        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "Validation error";

        ApiResponse<Void> body = ApiResponse.failure(ResponseCode.VALIDATION_ERROR.getCode(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeignException(
            feign.FeignException ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.failure(
                ResponseCode.SERVICE_UNAVAILABLE.getCode(), "Upstream service error");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}
