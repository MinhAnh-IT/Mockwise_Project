package com.mockwise.interview.dto.admin.request;

import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.Importance;
import com.mockwise.interview.enums.TopicKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BlueprintTopicDto(

        @NotNull(message = "kind is required")
        TopicKind kind,

        @NotBlank(message = "topicValue is required")
        @Size(max = 50, message = "topicValue must be at most 50 characters")
        String topicValue,

        @NotNull(message = "importance is required")
        Importance importance,

        @NotNull(message = "targetDifficulty is required")
        Difficulty targetDifficulty,

        @PositiveOrZero(message = "orderHint must be >= 0")
        int orderHint
) {}
