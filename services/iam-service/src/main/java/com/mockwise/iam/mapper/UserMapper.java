package com.mockwise.iam.mapper;

import com.mockwise.iam.dto.internal.AuthenticatedUser;
import com.mockwise.iam.dto.request.AccountRequest;
import com.mockwise.iam.dto.response.UserResponse;
import com.mockwise.iam.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "isVerified", ignore = true)
    @Mapping(target = "blocked", ignore = true)
    @Mapping(target = "tokenVersion", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "hashPass", ignore = true)
    User toUser(AccountRequest request);

    // For boolean fields starting with "is", MapStruct exposes the property name as "verified"
    @Mapping(target = "isVerified", source = "verified")
    UserResponse toUserResponse(User user);

    @Mapping(target = "username", source = "user.email")
    AuthenticatedUser toAuthenticatedUser(User user);
}
