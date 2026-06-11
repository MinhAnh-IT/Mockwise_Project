-- Practice-service schema (PostgreSQL). Owns only per-user history & status;
-- problems + test cases live in question-bank, code execution in judge-service.

CREATE TABLE practice_submission (
    id              VARCHAR(36)  NOT NULL,          -- = submissionId sent to judge
    user_id         VARCHAR(36)  NOT NULL,
    question_id     VARCHAR(64)  NOT NULL,          -- problem id in question-bank
    problem_title   VARCHAR(255),                   -- denormalized for self-contained history
    difficulty      VARCHAR(20),                     -- denormalized so the verdict consumer can set problem-status difficulty
    language        VARCHAR(20)  NOT NULL,
    source_code     TEXT         NOT NULL,
    mode            VARCHAR(10)  NOT NULL,          -- RUN | SUBMIT
    status          VARCHAR(10)  NOT NULL,          -- PENDING | JUDGING | DONE | FAILED
    verdict         VARCHAR(30),                    -- ACCEPTED | WRONG_ANSWER | ... (null until DONE)
    passed_cases    INT          NOT NULL DEFAULT 0,
    total_cases     INT          NOT NULL DEFAULT 0,
    runtime_ms      INT,
    memory_kb       INT,
    -- Test-case ids sent as hidden for this run; the consumer uses this to
    -- suppress stdout/stderr of hidden cases when persisting per-case rows.
    hidden_case_ids JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_practice_submission_user_created   ON practice_submission (user_id, created_at DESC);
CREATE INDEX idx_practice_submission_user_question  ON practice_submission (user_id, question_id);

CREATE TABLE practice_submission_case (
    id              VARCHAR(36)  NOT NULL,
    submission_id   VARCHAR(36)  NOT NULL,
    order_index     INT          NOT NULL,
    test_case_id    VARCHAR(64),
    status          VARCHAR(30)  NOT NULL,
    runtime_ms      INT,
    memory_kb       INT,
    hidden          BOOLEAN      NOT NULL DEFAULT FALSE,
    stdout          TEXT,                            -- null for hidden cases
    stderr          TEXT,                            -- null for hidden cases
    PRIMARY KEY (id),
    CONSTRAINT fk_case_submission FOREIGN KEY (submission_id)
        REFERENCES practice_submission (id) ON DELETE CASCADE
);
CREATE INDEX idx_practice_case_submission ON practice_submission_case (submission_id);

CREATE TABLE practice_problem_status (
    user_id         VARCHAR(36)  NOT NULL,
    question_id     VARCHAR(64)  NOT NULL,
    difficulty      VARCHAR(20),                     -- denormalized from question-bank for stats
    status          VARCHAR(10)  NOT NULL,           -- ATTEMPTED | SOLVED
    attempt_count   INT          NOT NULL DEFAULT 0,
    best_runtime_ms INT,
    first_solved_at TIMESTAMP,
    last_attempt_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, question_id)
);
