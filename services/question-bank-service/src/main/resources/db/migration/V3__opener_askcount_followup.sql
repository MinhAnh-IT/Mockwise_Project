-- Question Bank — selection metadata & pre-authored follow-ups
--
-- Drives interview-service's first-question algorithm and the follow-up
-- decision tree (see services/interview-service/docs/question-selection-design.md).

-- Per-question selection metadata. ask_count powers the "diversify across
-- users" tie-breaker; last_asked_at lets selectors deprioritize questions
-- that just appeared in another session.
ALTER TABLE questions ADD COLUMN ask_count     BIGINT      NOT NULL DEFAULT 0;
ALTER TABLE questions ADD COLUMN last_asked_at TIMESTAMPTZ;
CREATE INDEX idx_questions_ask_count ON questions(ask_count);

-- Opener tag: BEHAVIORAL/CORE only — coding questions don't open sessions.
ALTER TABLE behavioral_questions ADD COLUMN is_opener BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE core_questions       ADD COLUMN is_opener BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_behavioral_opener ON behavioral_questions(is_opener) WHERE is_opener = TRUE;
CREATE INDEX idx_core_opener       ON core_questions(is_opener)       WHERE is_opener = TRUE;

-- Pre-authored follow-up questions. Each row probes a single weak target
-- (signal / concept / misconception / red_flag) belonging to a parent
-- question, so interview-service can match a follow-up by (kind, value)
-- without an LLM call.
CREATE TABLE question_follow_up (
    id                      VARCHAR(36)  PRIMARY KEY,
    parent_question_id      VARCHAR(36)  NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    probes_target_kind      VARCHAR(20)  NOT NULL,
    probes_target_value     VARCHAR(200) NOT NULL,
    text                    TEXT         NOT NULL,
    expected_points         JSONB,
    audio_key               VARCHAR(500),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (parent_question_id, probes_target_kind, probes_target_value)
);
CREATE INDEX idx_followup_parent ON question_follow_up(parent_question_id);
CREATE INDEX idx_followup_target ON question_follow_up(probes_target_kind, probes_target_value);
