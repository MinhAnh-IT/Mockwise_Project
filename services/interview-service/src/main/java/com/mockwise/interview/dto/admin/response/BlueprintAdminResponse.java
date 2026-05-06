package com.mockwise.interview.dto.admin.response;

import com.mockwise.interview.dto.admin.request.BlueprintTopicDto;
import com.mockwise.interview.entity.BlueprintTopic;
import com.mockwise.interview.entity.InterviewBlueprint;
import com.mockwise.interview.enums.InterviewType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BlueprintAdminResponse(
        UUID id,
        String targetRole,
        String level,
        InterviewType interviewType,
        List<BlueprintTopicDto> topics,
        int questionBudget,
        int timeBudgetMinutes,
        int maxFollowUpsPerTopic,
        int maxFollowUpsPerSession,
        boolean useAiSelector,
        boolean isDefault,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static BlueprintAdminResponse from(InterviewBlueprint b) {
        List<BlueprintTopicDto> topics = b.getTopics() == null ? List.of()
                : b.getTopics().stream().map(BlueprintAdminResponse::topicDto).toList();
        return new BlueprintAdminResponse(
                b.getId(),
                b.getTargetRole(),
                b.getLevel(),
                b.getInterviewType(),
                topics,
                b.getQuestionBudget(),
                b.getTimeBudgetMinutes(),
                b.getMaxFollowUpsPerTopic(),
                b.getMaxFollowUpsPerSession(),
                b.isUseAiSelector(),
                b.isDefault(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }

    private static BlueprintTopicDto topicDto(BlueprintTopic t) {
        return new BlueprintTopicDto(
                t.getKind(),
                t.getTopicValue(),
                t.getImportance(),
                t.getTargetDifficulty(),
                t.getOrderHint());
    }
}
