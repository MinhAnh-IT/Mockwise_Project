package com.mockwise.interview.entity;

import com.mockwise.interview.enums.Difficulty;
import com.mockwise.interview.enums.Importance;
import com.mockwise.interview.enums.TopicStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * The Topic Matrix row — one per (session, topic) pair. Status is the
 * primary input the planner reads when deciding whether a topic is closed.
 *
 * <p>{@code last_assessment} stores a snapshot of the most recent
 * AssessmentVerdict so the planner can reason about decay (e.g. "topic
 * was STRONG 3 questions ago — still trust it?") without re-scanning
 * the answer table.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "session_topic_state")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionTopicState {

    @EmbeddedId
    SessionTopicStateId id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    TopicStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    Importance importance;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_difficulty", nullable = false, length = 10)
    Difficulty targetDifficulty;

    @Column(name = "questions_asked", nullable = false)
    @Builder.Default
    int questionsAsked = 0;

    @Column(name = "follow_ups_used", nullable = false)
    @Builder.Default
    int followUpsUsed = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_difficulty", length = 10)
    Difficulty lastDifficulty;

    @Column(name = "last_score")
    Float lastScore;

    @Type(JsonBinaryType.class)
    @Column(name = "last_assessment", columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> lastAssessment = new HashMap<>();

    @Column(name = "closed_at")
    OffsetDateTime closedAt;
}
