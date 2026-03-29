package com.mockwise.userprofile.dto.response;

public record PositionDetailResponse(
        String positionId,
        TrackResponse track,
        LevelResponse level
) {
    public record TrackResponse(
            String id,
            String name,
            Boolean active
    ) {}
    
    public record LevelResponse(
            String id,
            String positionRole,
            Boolean active
    ) {}
}
