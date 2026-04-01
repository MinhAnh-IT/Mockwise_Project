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

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "finished_at")
    LocalDateTime finishedAt;
}
