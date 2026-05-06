package com.mockwise.questionbank.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pre-authored follow-up question linked to a parent question. Each row
 * probes one specific weak target (signal / concept / misconception /
 * red_flag) so interview-service can pick a follow-up by lookup rather
 * than asking the AI to generate one.
 *
 * <p>The (parent, target_kind, target_value) tuple is unique — admins
 * curate at most one follow-up per probe target per parent.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "question_follow_up")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuestionFollowUp {

    @Id
    @UuidGenerator
    String id;

    @Column(name = "parent_question_id", nullable = false, length = 36)
    String parentQuestionId;

    @Column(name = "probes_target_kind", nullable = false, length = 20)
    String probesTargetKind;

    @Column(name = "probes_target_value", nullable = false, length = 200)
    String probesTargetValue;

    @Column(nullable = false, columnDefinition = "TEXT")
    String text;

    @Type(JsonBinaryType.class)
    @Column(name = "expected_points", columnDefinition = "jsonb")
    List<String> expectedPoints;

    @Column(name = "audio_key", length = 500)
    String audioKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;
}
