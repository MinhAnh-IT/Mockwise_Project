package com.mockwise.interview.entity;

import com.mockwise.interview.entity.enums.InterviewType;
import com.mockwise.interview.entity.enums.SessionStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One interview attempt by a user. Owns lifecycle telemetry the planner
 * reads on every decision (running_strong_count, global_difficulty_offset,
 * stretch_mode, ...).
 *
 * <p>The blueprint reference is nullable so a session created before a
 * blueprint was assigned still survives blueprint deletion / archival.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "interview_session")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InterviewSession {

    @Id
    @UuidGenerator
    UUID id;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "blueprint_id")
    UUID blueprintId;

    @Column(name = "target_role", length = 20)
    String targetRole;

    @Column(length = 20)
    String level;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_type", length = 20)
    InterviewType interviewType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    SessionStatus status;

    @Column(name = "question_count", nullable = false)
    int questionCount;

    @Column(name = "consecutive_unknown_count", nullable = false)
    @Builder.Default
    int consecutiveUnknownCount = 0;

    @Column(name = "running_strong_count", nullable = false)
    @Builder.Default
    int runningStrongCount = 0;

    @Column(name = "global_difficulty_offset", nullable = false)
    @Builder.Default
    int globalDifficultyOffset = 0;

    @Column(name = "stretch_mode", nullable = false)
    @Builder.Default
    boolean stretchMode = false;

    @Column(name = "total_follow_ups_used", nullable = false)
    @Builder.Default
    int totalFollowUpsUsed = 0;

    @Column(name = "started_at")
    OffsetDateTime startedAt;

    @Column(name = "finished_at")
    OffsetDateTime finishedAt;

    @Column(name = "scored_at")
    OffsetDateTime scoredAt;

    @Column(name = "final_score")
    Float finalScore;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;
}
