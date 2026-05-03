package com.mockwise.interview.entity;

import com.mockwise.interview.entity.enums.Difficulty;
import com.mockwise.interview.entity.enums.QuestionSource;
import com.mockwise.interview.entity.enums.QuestionType;
import com.mockwise.interview.entity.enums.TopicKind;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One question pinned to a session, in display order.
 *
 * <p>{@code source} drives which fields are populated:
 * <ul>
 *   <li>{@link QuestionSource#BANK BANK} — {@code questionId} set,
 *       {@code snapshot} carries the frozen question-bank row,
 *       {@code parentSessionQuestionId} null.</li>
 *   <li>{@link QuestionSource#PRE_AUTHORED_FOLLOWUP PRE_AUTHORED_FOLLOWUP} —
 *       {@code questionId} set (refs the follow-up row),
 *       {@code parentQuestionId} set (refs the parent in the bank),
 *       {@code parentSessionQuestionId} set,
 *       {@code isFollowUp = true}.</li>
 *   <li>{@link QuestionSource#AI_GENERATED AI_GENERATED} — {@code questionId}
 *       null, {@code inlineText} + {@code inlineExpectedPoints} carry the
 *       content, {@code parentSessionQuestionId} set,
 *       {@code isFollowUp = true}.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "session_question")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionQuestion {

    @Id
    @UuidGenerator
    UUID id;

    @Column(name = "session_id", nullable = false)
    UUID sessionId;

    @Column(nullable = false)
    int sequence;

    @Column(name = "question_id", length = 36)
    String questionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_kind", length = 20)
    TopicKind topicKind;

    @Column(name = "topic_value", length = 50)
    String topicValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    Difficulty difficulty;

    @Column(name = "parent_question_id", length = 36)
    String parentQuestionId;

    @Column(name = "parent_session_question_id")
    UUID parentSessionQuestionId;

    @Column(name = "is_follow_up", nullable = false)
    @Builder.Default
    boolean isFollowUp = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    QuestionSource source = QuestionSource.BANK;

    @Column(name = "inline_text", columnDefinition = "TEXT")
    String inlineText;

    @Type(JsonBinaryType.class)
    @Column(name = "inline_expected_points", columnDefinition = "jsonb")
    List<String> inlineExpectedPoints;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> snapshot = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;
}
