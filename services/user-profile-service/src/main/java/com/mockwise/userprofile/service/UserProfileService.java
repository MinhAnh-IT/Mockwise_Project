package com.mockwise.userprofile.service;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.userprofile.client.dto.IamUserResponse;
import com.mockwise.userprofile.common.exception.BusinessException;
import com.mockwise.userprofile.dto.request.UserProfileRequest;
import com.mockwise.userprofile.dto.request.UserProfileUpdateRequest;
import com.mockwise.userprofile.dto.response.AdminUserProfileResponse;
import com.mockwise.userprofile.dto.response.ProfileStatsResponse;
import com.mockwise.userprofile.dto.response.UserProfileResponse;
import com.mockwise.userprofile.entity.Position;
import com.mockwise.userprofile.entity.UserProfile;
import com.mockwise.userprofile.mapper.UserProfileMapper;
import com.mockwise.userprofile.repository.UserProfileRepository;
import jakarta.persistence.criteria.Join;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {

    UserProfileRepository userProfileRepository;
    UserProfileMapper userProfileMapper;
    PositionService positionService;
    IamUserService iamUserService;

    @Transactional
    public UserProfileResponse createProfile(String userId, UserProfileRequest request) {
        if (userProfileRepository.existsById(userId)) {
            throw new BusinessException(ResponseCode.CONFLICT,
                    "Profile already exists for user: " + userId);
        }

        Position position = positionService.getOrCreatePosition(request.trackId(), request.levelId());

        UserProfile profile = userProfileMapper.toEntity(request);
        profile.setUserId(userId);
        profile.setPosition(position);

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Created user profile for userId={}", userId);

        return userProfileMapper.toResponse(saved);
    }

    public UserProfileResponse getProfileById(String userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Profile not found for user: " + userId));
        return userProfileMapper.toResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(String currentUser, String userId, UserProfileUpdateRequest request) {
        if (!currentUser.equals(userId)) {
            throw new BusinessException(ResponseCode.FORBIDDEN);
        }

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Profile not found for user: " + userId));

        userProfileMapper.updateEntityFromDto(request, profile);
        applyPositionUpdate(profile, request);

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Updated user profile for userId={}", userId);

        return userProfileMapper.toResponse(saved);
    }

    /**
     * Position is stored as a Position entity keyed by (track, level), so
     * updating it requires both ids together. Reject partial input loudly
     * instead of silently keeping the old position — that mismatch was
     * previously hard for clients to spot (200 OK with stale data).
     */
    private void applyPositionUpdate(UserProfile profile, UserProfileUpdateRequest request) {
        boolean trackProvided = request.trackId() != null && !request.trackId().isBlank();
        boolean levelProvided = request.levelId() != null && !request.levelId().isBlank();

        if (trackProvided ^ levelProvided) {
            throw new BusinessException(ResponseCode.BAD_REQUEST,
                    "trackId and levelId must be provided together");
        }
        if (trackProvided && levelProvided) {
            Position newPosition = positionService.getOrCreatePosition(request.trackId(), request.levelId());
            profile.setPosition(newPosition);
        }
    }

    public Page<UserProfileResponse> searchProfiles(
            String trackId, String levelId, String keyword, Pageable pageable) {
        Specification<UserProfile> spec = Specification.where(null);

        if (trackId != null && !trackId.isBlank()) {
            spec = spec.and((root, query, cb) -> {
                Join<UserProfile, Position> position = root.join("position");
                return cb.equal(position.get("track").get("id"), trackId);
            });
        }
        if (levelId != null && !levelId.isBlank()) {
            spec = spec.and((root, query, cb) -> {
                Join<UserProfile, Position> position = root.join("position");
                return cb.equal(position.get("level").get("id"), levelId);
            });
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase().trim() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("fullName")), like));
        }

        return userProfileRepository.findAll(spec, pageable)
                .map(userProfileMapper::toResponse);
    }

    public Page<AdminUserProfileResponse> searchProfilesForAdmin(
            String trackId, String levelId, String keyword, Pageable pageable) {
        Page<UserProfileResponse> profiles = searchProfiles(trackId, levelId, keyword, pageable);

        List<AdminUserProfileResponse> enrichedProfiles = new ArrayList<>();
        for (UserProfileResponse profile : profiles.getContent()) {
            IamUserResponse userInfo = iamUserService.getUserInfo(profile.userId());
            String email = userInfo.email() != null ? userInfo.email() : "";
            boolean isVerified = Boolean.TRUE.equals(userInfo.isVerified());
            enrichedProfiles.add(userProfileMapper.toAdminResponse(profile, email, isVerified));
        }

        return new PageImpl<>(enrichedProfiles, pageable, profiles.getTotalElements());
    }

    @Transactional
    public UserProfileResponse updateProfileAsAdmin(String userId, UserProfileUpdateRequest request) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Profile not found for user: " + userId));

        userProfileMapper.updateEntityFromDto(request, profile);
        applyPositionUpdate(profile, request);

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Admin updated user profile for userId={}", userId);
        return userProfileMapper.toResponse(saved);
    }

    public ProfileStatsResponse getProfileStats() {
        return userProfileMapper.toStatsResponse(userProfileRepository.count());
    }
}
