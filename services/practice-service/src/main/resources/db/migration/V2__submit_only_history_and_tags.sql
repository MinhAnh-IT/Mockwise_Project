-- Practice history becomes SUBMIT-only: RUN trials are now ephemeral (held in an
-- in-memory cache while judging, never persisted). Drop any legacy RUN rows so
-- history, acceptance and grouped-by-problem views are clean. The per-case rows
-- cascade away via fk_case_submission.
DELETE FROM practice_submission WHERE mode = 'RUN';

-- Denormalize question-bank tags so per-language / per-tag progress can be
-- aggregated locally without fanning out to question-bank. Stored on the
-- submission at dispatch and copied onto problem-status on solve.
ALTER TABLE practice_submission     ADD COLUMN tags JSONB;
ALTER TABLE practice_problem_status ADD COLUMN tags JSONB;
