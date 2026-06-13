-- Batch execution: one Judge0 submission runs all cases of a job in a single
-- process (compile once). The job now owns the assembled source + batch stdin so
-- a transient Judge0 internal error (status 13/14) can be retried by resubmitting
-- the same payload, and tracks the resubmission count.
ALTER TABLE judge_jobs
    ADD COLUMN full_source  LONGTEXT    NULL,
    ADD COLUMN batch_stdin  LONGTEXT    NULL,
    ADD COLUMN judge0_token VARCHAR(64) NULL,
    ADD COLUMN retry_count  INT         NOT NULL DEFAULT 0;

CREATE INDEX idx_judge_jobs_judge0_token ON judge_jobs(judge0_token);
