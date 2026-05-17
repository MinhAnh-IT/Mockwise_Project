-- Coding questions: add an optional LeetCode-style Constraints block, and
-- drop the per-question time limit.
--
-- Rationale: interviews are now bounded by the session-level
-- time_budget_minutes (interview_session / interview_blueprint), not by a
-- per-question countdown. The old time_limit_minutes column drove a
-- per-question auto-submit that no longer exists.

ALTER TABLE coding_questions ADD COLUMN constraints TEXT;
ALTER TABLE coding_questions DROP COLUMN time_limit_minutes;
