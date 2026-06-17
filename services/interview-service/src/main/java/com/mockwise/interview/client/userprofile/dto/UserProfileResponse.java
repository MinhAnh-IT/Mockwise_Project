package com.mockwise.interview.client.userprofile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mirror of user-profile-service's {@code UserProfileResponse}. Includes
 * the four selection-driving fields added in the recent extension
 * ({@code techStack}, {@code preferredLanguage}, {@code yearsInCurrentRole},
 * {@code industries}) — the orchestrator reads these to seed
 * {@code session.target_role}, the difficulty calibration, and the
 * scoring bias hints in {@code POST /questions/filter}.
 *
 * <p>{@code preferredLanguage} is left as a String (not the enum) on this
 * boundary so an unknown future value doesn't fail the whole response
 * deserialization — the orchestrator coerces / defaults locally.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfileResponse(
        String userId,
        String fullName,
        PositionResponse position,
        Integer experience,
        String avatarUrl,
        OffsetDateTime avatarUrlExpiresAt,
        List<String> techStack,
        String preferredLanguage,
        Integer yearsInCurrentRole,
        List<String> industries
) {}
