package com.mockwise.interview.dto.response;

import com.mockwise.interview.entity.SessionQuestion;
import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.QuestionSource;
import com.mockwise.interview.enums.QuestionType;
import com.mockwise.interview.enums.TopicKind;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the FE needs to render one pinned question. Carries enough metadata
 * for the player UI ({@code text}, {@code audioKey} when present) plus
 * follow-up linkage so the FE can show "Câu này tiếp nối câu trước".
 */
public record PinnedQuestionView(
        UUID sessionQuestionId,
        int sequence,
        String questionId,
        QuestionType questionType,
        TopicKind topicKind,
        String topicValue,
        Difficulty difficulty,
        QuestionSource source,
        boolean isFollowUp,
        UUID parentSessionQuestionId,
        String text,
        String audioKey,
        List<String> expectedPoints
) {

    /** Build from an entity. {@code text}/{@code audioKey} come from snapshot or inline. */
    public static PinnedQuestionView fromEntity(SessionQuestion sq) {
        Map<String, Object> snap = sq.getSnapshot() != null ? sq.getSnapshot() : Map.of();
        String text = sq.getInlineText() != null
                ? sq.getInlineText()
                : (String) snap.get("text");
        String audioKey = (String) snap.get("audioKey");
        List<String> expectedPoints = sq.getInlineExpectedPoints() != null
                ? sq.getInlineExpectedPoints()
                : extractStringList(snap.get("expectedPoints"));

        return new PinnedQuestionView(
                sq.getId(),
                sq.getSequence(),
                sq.getQuestionId(),
                sq.getQuestionType(),
                sq.getTopicKind(),
                sq.getTopicValue(),
                sq.getDifficulty(),
                sq.getSource(),
                sq.isFollowUp(),
                sq.getParentSessionQuestionId(),
                text,
                audioKey,
                expectedPoints
        );
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Object o) {
        return o instanceof List<?> l ? (List<String>) l : null;
    }
}
