package com.mockwise.ttsstt.controller;

import com.core.apiresponse.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> health() {
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
