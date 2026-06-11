package com.mockwise.practice.entity;

import com.mockwise.practice.enums.SubmissionMode;
import com.mockwise.practice.enums.SubmissionStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One Run or Submit attempt by a user for a problem. {@code id} is the
 * {@code submissionId} sent to judge-service, so the verdict event maps back
 * to this row directly.
 */
@Entity
@Table(name = "practice_submission")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PracticeSubmission {

    @Id
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    String id;

    @Column(name = "user_id", length = 36, nullable = false)
    String userId;

    @Column(name = "question_id", length = 64, nullable = false)
    String questionId;

    @Column(name = "problem_title", length = 255)
    String problemTitle;

    @Column(name = "difficulty", length = 20)
    String difficulty;

    @Column(name = "language", length = 20, nullable = false)
    String language;

    @Column(name = "source_code", columnDefinition = "TEXT", nullable = false)
    String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 10, nullable = false)
    SubmissionMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    SubmissionStatus status;

    @Column(name = "verdict", length = 30)
    String verdict;

    @Column(name = "passed_cases", nullable = false)
    int passedCases;

    @Column(name = "total_cases", nullable = false)
    int totalCases;

    @Column(name = "runtime_ms")
    Integer runtimeMs;

    @Column(name = "memory_kb")
    Integer memoryKb;

    /** Test-case ids dispatched as hidden — drives stdout/stderr suppression on the verdict. */
    @Type(JsonBinaryType.class)
    @Column(name = "hidden_case_ids", columnDefinition = "jsonb")
    List<String> hiddenCaseIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "finished_at")
    LocalDateTime finishedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
