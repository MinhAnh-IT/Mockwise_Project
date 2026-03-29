package com.mockwise.iam.mapper;

import com.mockwise.iam.dto.response.RegisterResponse;
import com.mockwise.iam.dto.response.UserProfileResponse;
import com.mockwise.iam.dto.response.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RegisterMapper {
    RegisterResponse toRegisterResponse(UserResponse user, UserProfileResponse profile);
}
