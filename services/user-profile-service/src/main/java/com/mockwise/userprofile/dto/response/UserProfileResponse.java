package com.mockwise.userprofile.dto.response;

public record UserProfileResponse (
        String userId,
        String fullName,
        PositionResponse position,
        String city,
        Integer experience
){ }
