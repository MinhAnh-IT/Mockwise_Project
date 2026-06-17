package com.mockwise.userprofile.mapper;

import com.mockwise.userprofile.dto.request.UserProfileRequest;
import com.mockwise.userprofile.dto.request.UserProfileUpdateRequest;
import com.mockwise.userprofile.dto.response.AdminUserProfileResponse;
import com.mockwise.userprofile.dto.response.ProfileStatsResponse;
import com.mockwise.userprofile.dto.response.UserProfileResponse;
import com.mockwise.userprofile.entity.UserProfile;
import org.mapstruct.*;

import java.time.Instant;

@Mapper(componentModel = "spring", uses = {PositionMapper.class})
public interface UserProfileMapper {

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "position", ignore = true)
    UserProfile toEntity(UserProfileRequest request);

    // avatarUrl + avatarUrlExpiresAt are populated by the service layer after
    // calling storage-service for a presigned GET URL — leave them null here.
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "avatarUrlExpiresAt", ignore = true)
    UserProfileResponse toResponse(UserProfile entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "position", ignore = true)
    void updateEntityFromDto(UserProfileUpdateRequest dto, @MappingTarget UserProfile entity);

    @Mapping(source = "profile.userId", target = "userId")
    @Mapping(source = "profile.fullName", target = "fullName")
    @Mapping(source = "profile.position", target = "position")
    @Mapping(source = "profile.experience", target = "experience")
    @Mapping(source = "profile.createdAt", target = "createdAt")
    @Mapping(source = "profile.techStack", target = "techStack")
    @Mapping(source = "profile.preferredLanguage", target = "preferredLanguage")
    @Mapping(source = "profile.yearsInCurrentRole", target = "yearsInCurrentRole")
    @Mapping(source = "profile.industries", target = "industries")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "isVerified", target = "isVerified")
    @Mapping(source = "blocked", target = "blocked")
    @Mapping(source = "lastLoginAt", target = "lastLoginAt")
    AdminUserProfileResponse toAdminResponse(
            UserProfileResponse profile, String email, Boolean isVerified, Boolean blocked, Instant lastLoginAt);

    default ProfileStatsResponse toStatsResponse(long totalProfiles) {
        ProfileStatsResponse stats = new ProfileStatsResponse();
        stats.setTotalProfiles(totalProfiles);
        return stats;
    }
}
