-- Mark "throwaway" runs (e.g. the admin "Kiểm tra đề" / validation flow) so the
-- rows can be purged shortly after the result is read. Ephemeral jobs are NOT
-- published to the verdict topic and are excluded from admin monitoring. NULL is
-- impossible (NOT NULL DEFAULT FALSE) — existing/legacy jobs are non-ephemeral.
ALTER TABLE judge_jobs ADD COLUMN ephemeral BOOLEAN NOT NULL DEFAULT FALSE AFTER origin;

-- Purge sweep filters on (ephemeral, created_at); index keeps it cheap.
CREATE INDEX idx_judge_jobs_ephemeral_created ON judge_jobs(ephemeral, created_at);
