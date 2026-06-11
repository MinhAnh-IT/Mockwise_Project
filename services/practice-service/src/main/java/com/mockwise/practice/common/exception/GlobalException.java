package com.mockwise.practice.common.exception;

import com.core.apiresponse.common.ResponseCode;
import com.core.apiresponse.response.ApiResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalException {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ApiResponse<Void> body = ApiResponse.failure(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().isEmpty()
                ? null
                : ex.getBindingResult().getFieldErrors().get(0);
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "Validation error";
        ApiResponse<Void> body = ApiResponse.failure(ResponseCode.VALIDATION_ERROR.getCode(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Any failure reaching upstream question-bank surfaces as a 502 to the caller. */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstream(FeignException ex) {
        log.warn("Upstream call failed: status={} msg={}", ex.status(), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.failure(
                StatusCode.QUESTION_BANK_UNAVAILABLE.getCode(),
                StatusCode.QUESTION_BANK_UNAVAILABLE.getMessage());
        return ResponseEntity.status(StatusCode.QUESTION_BANK_UNAVAILABLE.getHttpStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiResponse<Void> body = ApiResponse.failure(
                ResponseCode.INTERNAL_ERROR.getCode(), "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
