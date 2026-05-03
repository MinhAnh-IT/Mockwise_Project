package com.mockwise.interview.entity;

import com.mockwise.interview.entity.enums.InterviewType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin-managed catalog row keyed by (target_role, level, interview_type).
 *
 * <p>Sessions copy the topic list into {@code session_topic_state} at
 * {@code /interviews/start} time so admin edits to the blueprint do not
 * mutate sessions already in flight.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "interview_blueprint")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InterviewBlueprint {

    @Id
    @UuidGenerator
    UUID id;

    @Column(name = "target_role", nullable = false, length = 20)
    String targetRole;

    @Column(nullable = false, length = 20)
    String level;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", nullable = false, length = 20)
    InterviewType interviewType;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    List<BlueprintTopic> topics = new ArrayList<>();

    @Column(name = "question_budget", nullable = false)
    @Builder.Default
    int questionBudget = 8;

    @Column(name = "max_follow_ups_per_topic", nullable = false)
    @Builder.Default
    int maxFollowUpsPerTopic = 2;

    @Column(name = "max_follow_ups_per_session", nullable = false)
    @Builder.Default
    int maxFollowUpsPerSession = 4;

    @Column(name = "time_budget_minutes", nullable = false)
    @Builder.Default
    int timeBudgetMinutes = 45;

    @Column(name = "use_ai_selector", nullable = false)
    @Builder.Default
    boolean useAiSelector = false;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;
}
