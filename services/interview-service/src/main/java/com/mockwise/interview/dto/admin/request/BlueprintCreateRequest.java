package com.mockwise.interview.dto.admin.request;

import com.mockwise.interview.enums.InterviewType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BlueprintCreateRequest(

        @NotBlank(message = "targetRole is required")
        @Size(max = 20, message = "targetRole must be at most 20 characters")
        String targetRole,

        @NotBlank(message = "level is required")
        @Size(max = 20, message = "level must be at most 20 characters")
        String level,

        @NotNull(message = "interviewType is required")
        InterviewType interviewType,

        @NotEmpty(message = "topics must not be empty")
        @Valid
        List<BlueprintTopicDto> topics,

        @Min(value = 1, message = "questionBudget must be >= 1")
        Integer questionBudget,

        @Min(value = 1, message = "timeBudgetMinutes must be >= 1")
        Integer timeBudgetMinutes,

        @Min(value = 0, message = "maxFollowUpsPerTopic must be >= 0")
        Integer maxFollowUpsPerTopic,

        @Min(value = 0, message = "maxFollowUpsPerSession must be >= 0")
        Integer maxFollowUpsPerSession,

        Boolean useAiSelector,

        Boolean isDefault
) {}
