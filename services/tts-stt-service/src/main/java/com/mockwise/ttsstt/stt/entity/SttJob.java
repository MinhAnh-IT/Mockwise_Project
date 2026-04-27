package com.mockwise.ttsstt.stt.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stt_job")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SttJob {

    @Id
    @UuidGenerator
    @Column(length = 36)
    String id;

    @Column(name = "storage_object_id", nullable = false, length = 36)
    String storageObjectId;

    @Column(name = "answer_id", nullable = false, length = 36)
    String answerId;

    @Column(name = "session_id", nullable = false, length = 64)
    String sessionId;

    @Column(name = "question_id", nullable = false, length = 64)
    String questionId;

    @Column(name = "owner_user_id", nullable = false, length = 64)
    String ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    SttJobStatus status;

    @Column(name = "error_code", length = 40)
    String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    String errorMessage;

    @Column(name = "transcript_id", length = 36)
    String transcriptId;

    @Column(name = "attempt_count", nullable = false)
    int attemptCount;

    @Column(name = "started_at")
    OffsetDateTime startedAt;

    @Column(name = "finished_at")
    OffsetDateTime finishedAt;

    @Column(name = "published_event_at")
    OffsetDateTime publishedEventAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    OffsetDateTime updatedAt;
}
