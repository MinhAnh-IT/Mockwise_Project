package com.mockwise.ttsstt.stt.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "transcript")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Transcript {

    @Id
    @UuidGenerator
    @Column(length = 36)
    String id;

    @Column(name = "stt_job_id", nullable = false, length = 36)
    String sttJobId;

    @Column(name = "storage_object_id", nullable = false, length = 36)
    String storageObjectId;

    @Column(name = "answer_id", nullable = false, length = 36)
    String answerId;

    @Column(name = "session_id", nullable = false, length = 64)
    String sessionId;

    @Column(name = "question_id", nullable = false, length = 64)
    String questionId;

    @Lob
    @Column(name = "text", nullable = false, columnDefinition = "MEDIUMTEXT")
    String text;

    @Column(name = "language_code", nullable = false, length = 10)
    String languageCode;

    @Column(name = "language_confidence")
    Float languageConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "words", nullable = false, columnDefinition = "JSON")
    List<TranscriptWord> words;

    @Column(name = "duration_ms")
    Integer durationMs;

    @Column(name = "model_id", nullable = false, length = 64)
    String modelId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;
}
