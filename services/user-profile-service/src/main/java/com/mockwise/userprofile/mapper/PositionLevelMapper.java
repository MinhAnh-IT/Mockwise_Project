package com.mockwise.userprofile.mapper;

import com.mockwise.userprofile.dto.request.PositionLevelCreateRequest;
import com.mockwise.userprofile.dto.request.PositionLevelUpdateRequest;
import com.mockwise.userprofile.dto.response.PositionLevelResponse;
import com.mockwise.userprofile.entity.PositionLevel;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface PositionLevelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    PositionLevel toEntity(PositionLevelCreateRequest request);

    PositionLevelResponse toResponse(PositionLevel entity);

    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget PositionLevel entity, PositionLevelUpdateRequest request);
}