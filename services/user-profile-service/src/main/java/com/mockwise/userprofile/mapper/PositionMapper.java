package com.mockwise.userprofile.mapper;

import com.mockwise.userprofile.dto.response.PositionDetailResponse;
import com.mockwise.userprofile.dto.response.PositionResponse;
import com.mockwise.userprofile.entity.Position;
import com.mockwise.userprofile.entity.PositionLevel;
import com.mockwise.userprofile.entity.PositionTrack;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    @Mapping(target = "trackId", source = "track.id")
    @Mapping(target = "trackName", source = "track.name")
    @Mapping(target = "levelId", source = "level.id")
    @Mapping(target = "levelName", source = "level.positionRole")
    PositionResponse toResponse(Position entity);

    @Mapping(target = "positionId", ignore = true)
    Position toEntity(PositionTrack track, PositionLevel level);
}
