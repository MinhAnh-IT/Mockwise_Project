CREATE TABLE judge_jobs (
    id              CHAR(36)     PRIMARY KEY,
    submission_id   CHAR(36)     NOT NULL UNIQUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_cases     INT          NOT NULL DEFAULT 0,
    done_cases      INT          NOT NULL DEFAULT 0,
    verdict         VARCHAR(20),
    language        VARCHAR(20)  NOT NULL,
    function_meta   JSON         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP    NULL
);

CREATE TABLE judge_task_results (
    id              CHAR(36)     PRIMARY KEY,
    job_id          CHAR(36)     NOT NULL,
    test_case_id    CHAR(36)     NOT NULL,
    order_index     INT          NOT NULL,
    judge0_token    VARCHAR(64),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    stdout          TEXT,
    stderr          TEXT,
    expected_output TEXT,
    runtime_ms      INT,
    memory_kb       INT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP    NULL,
    CONSTRAINT fk_task_job FOREIGN KEY (job_id) REFERENCES judge_jobs(id)
);

CREATE INDEX idx_judge_jobs_submission_id  ON judge_jobs(submission_id);
CREATE INDEX idx_task_results_job_id       ON judge_task_results(job_id);
CREATE INDEX idx_task_results_judge0_token ON judge_task_results(judge0_token);
