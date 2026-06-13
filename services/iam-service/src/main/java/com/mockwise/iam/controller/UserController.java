package com.mockwise.iam.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.iam.dto.request.ProfileDraftRequest;
import com.mockwise.iam.dto.request.RegisterRequest;
import com.mockwise.iam.dto.response.RegisterResponse;
import com.mockwise.iam.dto.response.UserResponse;
import com.mockwise.iam.entity.User;
import com.mockwise.iam.mapper.UserMapper;
import com.mockwise.iam.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;
    UserMapper userMapper;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> createUser(
            @RequestBody @Valid RegisterRequest body) {
        RegisterResponse result = userService.register(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    /**
     * Complete the profile for the currently-authenticated user (social-login
     * first sign-in). Authed route — the gateway injects {@code X-User-Id}.
     */
    @PostMapping("/me/profile")
    public ResponseEntity<ApiResponse<Void>> completeProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody @Valid ProfileDraftRequest body) {
        userService.completeProfile(userId, body);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String userId) {
        User user = userService.findById(userId);
        UserResponse userResponse = userMapper.toUserResponse(user);
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }
}
