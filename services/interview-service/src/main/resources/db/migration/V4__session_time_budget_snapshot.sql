-- Snapshot the blueprint's time_budget_minutes onto the session at /start
-- time so admin edits to the blueprint don't change the limit mid-interview,
-- and so the FE can read it back via GET /sessions/{sid} after a reload.
-- Mirrors the existing question_count snapshot on the same row.

ALTER TABLE interview_session
    ADD COLUMN time_budget_minutes INT NOT NULL DEFAULT 45;

-- Backfill in-flight sessions from their blueprint where possible. Sessions
-- whose blueprint is gone keep the 45-minute default — close to historical
-- behavior and only affects sessions that survived a blueprint deletion.
UPDATE interview_session s
SET    time_budget_minutes = b.time_budget_minutes
FROM   interview_blueprint b
WHERE  s.blueprint_id = b.id;
