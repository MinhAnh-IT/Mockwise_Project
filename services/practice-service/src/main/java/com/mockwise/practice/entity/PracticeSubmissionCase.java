package com.mockwise.practice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Per-test-case result of a submission. For hidden cases {@link #hidden} is
 * true and {@link #stdout}/{@link #stderr} are left null — the API exposes only
 * the case status, never the hidden I/O.
 */
@Entity
@Table(name = "practice_submission_case")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PracticeSubmissionCase {

    @Id
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    String id;

    @Column(name = "submission_id", length = 36, nullable = false)
    String submissionId;

    @Column(name = "order_index", nullable = false)
    int orderIndex;

    @Column(name = "test_case_id", length = 64)
    String testCaseId;

    @Column(name = "status", length = 30, nullable = false)
    String status;

    @Column(name = "runtime_ms")
    Integer runtimeMs;

    @Column(name = "memory_kb")
    Integer memoryKb;

    @Column(name = "hidden", nullable = false)
    boolean hidden;

    @Column(name = "stdout", columnDefinition = "TEXT")
    String stdout;

    @Column(name = "stderr", columnDefinition = "TEXT")
    String stderr;
}
