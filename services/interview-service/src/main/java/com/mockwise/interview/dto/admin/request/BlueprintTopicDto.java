package com.mockwise.interview.dto.admin.request;

import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.Importance;
import com.mockwise.interview.enums.TopicKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * A single blueprint topic.
 *
 * <p>{@code kind} and {@code topicValue} are intentionally NOT hard-required at
 * the bean-validation level: their requirement depends on {@code interviewType}.
 * For BEHAVIORAL/CORE they are mandatory and validated in
 * {@code BlueprintAdminService}; for CODING they are ignored (the slot only
 * carries {@code targetDifficulty}/{@code orderHint}). Validating them here with
 * {@code @NotNull}/{@code @NotBlank} would force CODING callers to send junk
 * placeholders. See {@code BlueprintAdminService#validateTopics}.
 */
public record BlueprintTopicDto(

        TopicKind kind,

        @Size(max = 50, message = "topicValue must be at most 50 characters")
        String topicValue,

        @NotNull(message = "importance is required")
        Importance importance,

        @NotNull(message = "targetDifficulty is required")
        Difficulty targetDifficulty,

        @PositiveOrZero(message = "orderHint must be >= 0")
        int orderHint
) {}
