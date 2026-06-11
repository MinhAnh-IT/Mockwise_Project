package com.mockwise.practice.entity;

import com.mockwise.practice.enums.ProblemStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A user's standing on one problem: ATTEMPTED until a SUBMIT is ACCEPTED, then
 * SOLVED (never regresses). Only SUBMIT touches this; RUN never does. The
 * {@code status} here is constrained to ATTEMPTED / SOLVED — {@code NONE} from
 * {@link ProblemStatus} represents the absent row, not a stored value.
 */
@Entity
@Table(name = "practice_problem_status")
@IdClass(PracticeProblemStatus.Key.class)
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PracticeProblemStatus {

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    String userId;

    @Id
    @Column(name = "question_id", length = 64, nullable = false)
    String questionId;

    /** Denormalized from question-bank so solved-by-difficulty stats stay local. */
    @Column(name = "difficulty", length = 20)
    String difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    ProblemStatus status;

    @Column(name = "attempt_count", nullable = false)
    int attemptCount;

    @Column(name = "best_runtime_ms")
    Integer bestRuntimeMs;

    @Column(name = "first_solved_at")
    LocalDateTime firstSolvedAt;

    @Column(name = "last_attempt_at", nullable = false)
    LocalDateTime lastAttemptAt;

    /** Composite primary key (user_id, question_id). */
    @Getter
    @Setter
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Key implements Serializable {
        String userId;
        String questionId;

        public Key(String userId, String questionId) {
            this.userId = userId;
            this.questionId = questionId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key key)) return false;
            return Objects.equals(userId, key.userId) && Objects.equals(questionId, key.questionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, questionId);
        }
    }
}
