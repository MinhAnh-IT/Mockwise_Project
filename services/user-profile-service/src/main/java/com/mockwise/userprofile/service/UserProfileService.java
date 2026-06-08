package com.mockwise.userprofile.service;

import com.core.apiresponse.common.ResponseCode;
import com.mockwise.userprofile.client.dto.IamUserResponse;
import com.mockwise.userprofile.common.exception.BusinessException;
import com.mockwise.userprofile.dto.request.UserProfileRequest;
import com.mockwise.userprofile.dto.request.UserProfileUpdateRequest;
import com.mockwise.userprofile.dto.response.AdminUserProfileResponse;
import com.mockwise.userprofile.dto.response.ProfileStatsResponse;
import com.mockwise.userprofile.dto.response.UserProfileResponse;
import com.mockwise.userprofile.entity.Language;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

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
        normalizeAndValidate(profile);

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Created user profile for userId={}", userId);

        return userProfileMapper.toResponse(saved);
    }

    /**
     * Cleans free-form list inputs (lowercase, trim, dedupe, drop blanks) and
     * enforces cross-field rules. Tech stack and industries are user-typed
     * tokens; without normalization "Java", " java ", "JAVA" would all hit
     * question-bank's tag matcher as different keys.
     */
    private void normalizeAndValidate(UserProfile profile) {
        profile.setTechStack(normalizeTokens(profile.getTechStack()));
        profile.setIndustries(normalizeTokens(profile.getIndustries()));

        if (profile.getPreferredLanguage() == null) {
            profile.setPreferredLanguage(Language.VI);
        }

        Integer years = profile.getYearsInCurrentRole();
        if (years != null && profile.getExperience() != null && years > profile.getExperience()) {
            throw new BusinessException(ResponseCode.BAD_REQUEST,
                    "yearsInCurrentRole cannot exceed total experience");
        }
    }

    private static List<String> normalizeTokens(List<String> input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : input) {
            if (raw == null) continue;
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) {
                seen.add(token);
            }
        }
        return new ArrayList<>(seen);
    }

    public UserProfileResponse getProfileById(String userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResponseCode.NOT_FOUND,
                        "Profile not found for user: " + userId));
        return toEnrichedResponse(profile);
    }

    /**
     * Stamps a same-origin avatar URL onto the response. The bytes are streamed
     * by storage-service at {@code GET /api/v1/storage/avatars/me} so the
     * browser never sees a MinIO host (HTTPS app + HTTP MinIO would otherwise
     * trip mixed-content blocking).
     *
     * <p>The {@code ?v=<key-suffix>} param is a cache-buster: when the user
     * uploads a new avatar the object key changes, the URL changes, and the
     * browser refetches instead of reusing the previous response.
     */
    private UserProfileResponse toEnrichedResponse(UserProfile profile) {
        UserProfileResponse base = userProfileMapper.toResponse(profile);
        String key = profile.getAvatarObjectKey();
        if (key == null || key.isBlank()) {
            return base;
        }
        String url = "/api/v1/storage/avatars/me?v=" + cacheBusterFor(key);
        return new UserProfileResponse(
                base.userId(),
                base.fullName(),
                base.position(),
                base.city(),
                base.experience(),
                url,
                null,
                base.techStack(),
                base.preferredLanguage(),
                base.yearsInCurrentRole(),
                base.industries());
    }

    private static String cacheBusterFor(String objectKey) {
        int slash = objectKey.lastIndexOf('/');
        return slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
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
        normalizeAndValidate(profile);

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Updated user profile for userId={}", userId);

        return toEnrichedResponse(saved);
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
            boolean blocked = Boolean.TRUE.equals(userInfo.blocked());
            enrichedProfiles.add(userProfileMapper.toAdminResponse(profile, email, isVerified, blocked));
        }

        return new PageImpl<>(enrichedProfiles, pageable, profiles.getTotalElements());
    }

    public ProfileStatsResponse getProfileStats() {
        return userProfileMapper.toStatsResponse(userProfileRepository.count());
    }
}
