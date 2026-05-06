package com.mockwise.interview.dto.response;

import com.mockwise.interview.enums.InterviewType;
import com.mockwise.interview.enums.SessionStatus;
import com.mockwise.interview.enums.TopicStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SessionView(
        UUID sessionId,
        String userId,
        String targetRole,
        String level,
        InterviewType interviewType,
        SessionStatus status,
        int questionCount,
        Float finalScore,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        OffsetDateTime scoredAt,
        List<TopicProgress> topicProgress,
        List<PinnedQuestionView> questions,
        // Populated only when status == SCORED (Task C). Carries the AI's
        // overall_reviewer output: overallScore / grade / hireSignal /
        // summary / strengths / weaknesses / perTopicSummary / recommendations.
        Map<String, Object> overallReview
) {

    public record TopicProgress(
            String topicKind,
            String topicValue,
            TopicStatus status,
            int questionsAsked,
            int followUpsUsed,
            Float lastScore
    ) {}
}
