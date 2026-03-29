package com.mockwise.iam.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.iam.dto.request.*;
import com.mockwise.iam.dto.response.LoginResponse;
import com.mockwise.iam.dto.response.TokenIntrospectResponse;
import com.mockwise.iam.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {

    AuthService authService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/sign-in")
    public ResponseEntity<ApiResponse<LoginResponse>> signIn(
            @RequestBody @Valid AccountRequest body,
            HttpServletResponse response,
            HttpServletRequest request) {
        LoginResponse result = authService.login(body, response);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAllDevices(HttpServletRequest request) {
        authService.logoutAllDevices(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/token/renew")
    public ResponseEntity<ApiResponse<LoginResponse>> renewAccessToken(HttpServletRequest request) {
        LoginResponse result = authService.renewAccessToken(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/token/introspect")
    public ResponseEntity<ApiResponse<TokenIntrospectResponse>> introspect(
            @RequestBody IntrospectRequest body,
            HttpServletRequest request) {
        TokenIntrospectResponse result = authService.introspectAccessToken(body);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/verify-account/send")
    public ResponseEntity<ApiResponse<Boolean>> sendAccountVerificationOtp(
            @RequestBody @Valid VerifyEmailRequest body,
            HttpServletRequest request) {
        boolean result = authService.sendVerificationOtp(body);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/verify-account/confirm")
    public ResponseEntity<ApiResponse<Boolean>> verifyAccountOtp(
            @RequestBody @Valid VerifyAccountOtpRequest body,
            HttpServletRequest request) {
        boolean result = authService.verifyAccountOtp(body);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/forgot-password/send")
    public ResponseEntity<ApiResponse<Boolean>> sendForgotPasswordOtp(
            @RequestBody @Valid ForgotEmailRequest body,
            HttpServletRequest request) {
        boolean result = authService.sendResetPasswordOtp(body);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/forgot-password/confirm")
    public ResponseEntity<ApiResponse<Boolean>> verifyResetPasswordOtp(
            @RequestBody @Valid VerifyResetPasswordOtp body,
            HttpServletRequest request) {
        boolean result = authService.verifyResetPasswordOtp(body);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
