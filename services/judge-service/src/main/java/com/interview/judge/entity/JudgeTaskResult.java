package com.interview.judge.entity;

import com.interview.judge.entity.enums.TaskStatus;
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
@Table(name = "judge_task_results")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JudgeTaskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "CHAR(36)")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    JudgeJob job;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "test_case_id", nullable = false, columnDefinition = "CHAR(36)")
    UUID testCaseId;

    @Column(name = "order_index", nullable = false)
    int orderIndex;

    @Column(name = "judge0_token", length = 64)
    String judge0Token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    TaskStatus status = TaskStatus.PENDING;

    @Column(name = "stdout", columnDefinition = "TEXT")
    String stdout;

    @Column(name = "stderr", columnDefinition = "TEXT")
    String stderr;

    @Column(name = "expected_output", columnDefinition = "TEXT")
    String expectedOutput;

    @Column(name = "runtime_ms")
    Integer runtimeMs;

    @Column(name = "memory_kb")
    Integer memoryKb;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "finished_at")
    LocalDateTime finishedAt;
}
