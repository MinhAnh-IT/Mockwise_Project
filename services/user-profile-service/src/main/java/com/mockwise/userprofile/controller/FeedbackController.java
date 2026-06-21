package com.mockwise.userprofile.controller;

import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.common.security.CustomUserDetails;
import com.mockwise.userprofile.dto.request.FeedbackCreateRequest;
import com.mockwise.userprofile.dto.response.FeedbackResponse;
import com.mockwise.userprofile.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public-facing feedback submission. The endpoint is reachable without auth
 * (gateway public route + permitAll) so landing-page visitors can send feedback;
 * when the sender is logged in, {@code userId} is taken from the gateway headers.
 */
@RestController
@RequestMapping("/feedbacks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeedbackController {

    FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<FeedbackResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FeedbackCreateRequest request) {
        String userId = userDetails != null ? userDetails.getUserId() : null;
        FeedbackResponse response = feedbackService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
