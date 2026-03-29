package com.mockwise.userprofile.mapper;

import com.mockwise.userprofile.dto.request.UserProfileRequest;
import com.mockwise.userprofile.dto.request.UserProfileUpdateRequest;
import com.mockwise.userprofile.dto.response.AdminUserProfileResponse;
import com.mockwise.userprofile.dto.response.ProfileStatsResponse;
import com.mockwise.userprofile.dto.response.UserProfileResponse;
import com.mockwise.userprofile.entity.UserProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {PositionMapper.class})
public interface UserProfileMapper {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "position", ignore = true)
    UserProfile toEntity(UserProfileRequest request);

    UserProfileResponse toResponse(UserProfile entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "position", ignore = true)
    void updateEntityFromDto(UserProfileUpdateRequest dto, @MappingTarget UserProfile entity);

    @Mapping(source = "profile.userId", target = "userId")
    @Mapping(source = "profile.fullName", target = "fullName")
    @Mapping(source = "profile.position", target = "position")
    @Mapping(source = "profile.city", target = "city")
    @Mapping(source = "profile.experience", target = "experience")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "isVerified", target = "isVerified")
    AdminUserProfileResponse toAdminResponse(UserProfileResponse profile, String email, Boolean isVerified);

    default ProfileStatsResponse toStatsResponse(long totalProfiles) {
        ProfileStatsResponse stats = new ProfileStatsResponse();
        stats.setTotalProfiles(totalProfiles);
        return stats;
    }
}
