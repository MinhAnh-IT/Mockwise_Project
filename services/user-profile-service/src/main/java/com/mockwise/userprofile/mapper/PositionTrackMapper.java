package com.mockwise.userprofile.mapper;

import com.mockwise.userprofile.dto.request.PositionTrackCreateRequest;
import com.mockwise.userprofile.dto.request.PositionTrackUpdateRequest;
import com.mockwise.userprofile.dto.response.PositionTrackResponse;
import com.mockwise.userprofile.entity.PositionTrack;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PositionTrackMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    PositionTrack toEntity(PositionTrackCreateRequest request);

    PositionTrackResponse toResponse(PositionTrack entity);

    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget PositionTrack entity, PositionTrackUpdateRequest request);
}
