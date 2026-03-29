package com.mockwise.userprofile.controller;

import com.core.apiresponse.response.ApiListResponse;
import com.core.apiresponse.response.ApiResponse;
import com.mockwise.userprofile.dto.response.PositionTrackResponse;
import com.mockwise.userprofile.service.PositionTrackService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/position-tracks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PublicPositionTrackController {

    PositionTrackService positionTrackService;

    @GetMapping
    public ResponseEntity<ApiResponse<ApiListResponse<PositionTrackResponse>>> getAllTracks() {
        List<PositionTrackResponse> items = positionTrackService.getAllActive();
        return ResponseEntity.ok(ApiResponse.success(ApiListResponse.of(items)));
    }
}
