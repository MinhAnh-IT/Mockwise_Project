package com.mockwise.questionbank.entity;

import com.mockwise.questionbank.enums.Difficulty;
import com.mockwise.questionbank.enums.QuestionStatus;
import com.mockwise.questionbank.enums.QuestionType;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "questions")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Question {

    @Id
    @UuidGenerator
    String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    QuestionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    QuestionStatus status = QuestionStatus.DRAFT;

    @Type(StringArrayType.class)
    @Column(columnDefinition = "text[]", nullable = false)
    @Builder.Default
    String[] tags = new String[0];

    @Column(nullable = false)
    String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    OffsetDateTime updatedAt;

    /**
     * Times this question has been pinned to a session. Used by the selector
     * as a tie-breaker so popular questions are not always picked first —
     * incremented via the internal mark-asked endpoint, not Hibernate writes.
     */
    @Column(name = "ask_count", nullable = false)
    @Builder.Default
    Long askCount = 0L;

    @Column(name = "last_asked_at")
    OffsetDateTime lastAskedAt;
}
