-- Tag each job with the feature that produced it so the verdict can be
-- echoed back with an `origin`, letting practice-service and interview-service
-- fast-filter the shared `submission-judged` topic. NULL = legacy/INTERVIEW.
ALTER TABLE judge_jobs ADD COLUMN origin VARCHAR(20) NULL AFTER submission_id;
