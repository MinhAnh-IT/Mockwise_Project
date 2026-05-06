package com.mockwise.userprofile.dto.request;

import com.mockwise.userprofile.entity.Language;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserProfileUpdateRequest(
        String fullName,
        String trackId,
        String levelId,
        String city,

        @Min(value = 0, message = "Experience must be >= 0")
        Integer experience,

        /**
         * Storage object key returned by storage-service after a successful avatar
         * upload. Pass an empty string to clear the avatar.
         */
        String avatarObjectKey,

        @Size(max = 20, message = "Tech stack must have at most 20 items")
        List<String> techStack,

        Language preferredLanguage,

        @Min(value = 0, message = "Years in current role must be >= 0")
        Integer yearsInCurrentRole,

        @Size(max = 10, message = "Industries must have at most 10 items")
        List<String> industries
) {}
