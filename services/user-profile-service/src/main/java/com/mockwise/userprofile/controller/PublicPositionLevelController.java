package com.mockwise.userprofile.controller;

import com.core.apiresponse.response.ApiListResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.dto.response.PositionLevelResponse;
import com.mockwise.userprofile.service.PositionLevelService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/position-levels")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PublicPositionLevelController {

    PositionLevelService positionLevelService;

    @GetMapping
    public ResponseEntity<ApiResponse<ApiListResponse<PositionLevelResponse>>> getAllLevels() {
        List<PositionLevelResponse> items = positionLevelService.getAllActive();
        return ResponseEntity.ok(ApiResponse.success(ApiListResponse.of(items)));
    }
}
