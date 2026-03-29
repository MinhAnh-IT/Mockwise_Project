package com.mockwise.userprofile.dto.response;

public record PositionResponse(
        String positionId,
        String trackId,
        String trackName,
        String levelId,
        String levelName
) { }
