package com.mockwise.questionbank.common.exception;

import com.core.apiresponse.common.ResponseCode;
import com.core.apiresponse.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalException {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJsonParsing(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        Throwable cause = ex.getMostSpecificCause();
        String message = "Invalid request body";

        if (cause instanceof InvalidFormatException ife) {
            String path = pathOf(ife);
            String expected = humanType(ife.getTargetType());
            String got = (ife.getValue() == null) ? "null" : humanType(ife.getValue().getClass());
            message = path.isEmpty()
                    ? "Invalid field type: expected " + expected + " but got " + got
                    : path + ": expected " + expected + " but got " + got;
        } else if (cause instanceof MismatchedInputException mie) {
            String path = pathOf(mie);
            Class<?> target = mie.getTargetType();
            String expected = (target != null) ? humanType(target) : "valid value";
            message = path.isEmpty()
                    ? "Invalid field type: expected " + expected
                    : path + ": expected " + expected;
        }

        ApiResponse<Void> body = ApiResponse.failure(ResponseCode.VALIDATION_ERROR.getCode(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        ApiResponse<Void> body = ApiResponse.failure(
                ResponseCode.INTERNAL_ERROR.getCode(), "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String pathOf(JsonMappingException e) {
        return e.getPath().stream()
                .map(ref -> {
                    String f = ref.getFieldName();
                    if (f != null) return f;
                    if (ref.getIndex() >= 0) return "[" + ref.getIndex() + "]";
                    return "?";
                })
                .collect(Collectors.joining("."));
    }

    private String humanType(Class<?> type) {
        if (type == null) return "value";
        if (type.isPrimitive()) {
            if (type == boolean.class) return "boolean";
            if (type == char.class) return "string";
            return "number";
        }
        if (Number.class.isAssignableFrom(type)) return "number";
        if (CharSequence.class.isAssignableFrom(type)) return "string";
        if (Boolean.class.isAssignableFrom(type)) return "boolean";
        if (Collection.class.isAssignableFrom(type)) return "array";
        if (Map.class.isAssignableFrom(type)) return "object";
        if (Enum.class.isAssignableFrom(type)) return "enum";
        return type.getSimpleName();
    }
}
