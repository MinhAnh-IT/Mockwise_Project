package com.interview.judge.entity;

import com.interview.judge.dto.FunctionMeta;
import com.interview.judge.entity.enums.JobStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "judge_jobs")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JudgeJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "CHAR(36)")
    UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "submission_id", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    UUID submissionId;

    /** Producing feature: INTERVIEW (default / null) or PRACTICE. Echoed onto the result event. */
    @Column(name = "origin")
    String origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    JobStatus status = JobStatus.PENDING;

    @Column(name = "total_cases", nullable = false)
    int totalCases;

    @Column(name = "done_cases", nullable = false)
    int doneCases;

    @Column(name = "verdict")
    String verdict;

    @Column(name = "language", nullable = false)
    String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "function_meta", columnDefinition = "json", nullable = false)
    FunctionMeta functionMeta;

    // ── Batch execution (one Judge0 submission per job) ──────────────────────
    // Assembled source + batch stdin are retained so a transient Judge0 internal
    // error can be retried by resubmitting the identical payload.

    @Column(name = "full_source", columnDefinition = "LONGTEXT")
    String fullSource;

    @Column(name = "batch_stdin", columnDefinition = "LONGTEXT")
    String batchStdin;

    @Column(name = "judge0_token", length = 64)
    String judge0Token;

    @Column(name = "retry_count", nullable = false)
    int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "finished_at")
    LocalDateTime finishedAt;
}
