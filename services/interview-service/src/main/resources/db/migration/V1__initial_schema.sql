-- Interview Service — Initial Schema
-- PostgreSQL
--
-- Consolidates two design documents:
--   docs/interview-service-design.md          (session lifecycle, answer
--                                              state machine, outbox)
--   docs/question-selection-design.md         (topic matrix, blueprint,
--                                              follow-up linkage)
--
-- One migration ships the full picture so V1 does not immediately need
-- an ALTER for the selection layer.

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- for gen_random_uuid()

-- ─── Catalog: blueprints (admin-managed) ─────────────────────────────────────
-- A blueprint defines, for a given (role, level, type), which topics to test
-- and at what difficulty. Sessions copy the blueprint's topic list into
-- session_topic_state at /interviews/start so blueprint edits don't mutate
-- already-running sessions.

CREATE TABLE interview_blueprint (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_role                 VARCHAR(20) NOT NULL,        -- BACKEND | FRONTEND | ...
    level                       VARCHAR(20) NOT NULL,        -- junior | mid | senior
    interview_type              VARCHAR(20) NOT NULL,        -- BEHAVIORAL | CORE | MIXED
    topics                      JSONB       NOT NULL,        -- [{kind, value, importance, target_difficulty, order_hint}]
    question_budget             INT         NOT NULL DEFAULT 8,
    max_follow_ups_per_topic    INT         NOT NULL DEFAULT 2,
    max_follow_ups_per_session  INT         NOT NULL DEFAULT 4,
    time_budget_minutes         INT         NOT NULL DEFAULT 45,
    use_ai_selector             BOOLEAN     NOT NULL DEFAULT FALSE,
    is_default                  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Only ONE default blueprint per (role, level, type) at a time. Loaders
-- look up the default; admins can stage alternatives by leaving is_default=false.
CREATE UNIQUE INDEX idx_blueprint_default
    ON interview_blueprint(target_role, level, interview_type)
    WHERE is_default = TRUE;
CREATE INDEX idx_blueprint_lookup
    ON interview_blueprint(target_role, level, interview_type);


-- ─── Sessions ────────────────────────────────────────────────────────────────
-- Lifecycle: CREATED → IN_PROGRESS → COMPLETED | CANCELLED → SCORED
-- Selection-aware fields (running_strong_count, global_difficulty_offset, ...)
-- are owned by NextQuestionPlanner.

CREATE TABLE interview_session (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     VARCHAR(36) NOT NULL,
    blueprint_id                UUID        REFERENCES interview_blueprint(id),
    target_role                 VARCHAR(20),
    level                       VARCHAR(20),
    interview_type              VARCHAR(20),

    status                      VARCHAR(20) NOT NULL,        -- CREATED|IN_PROGRESS|COMPLETED|CANCELLED|SCORED
    question_count              INT         NOT NULL,        -- planned budget snapshot

    -- Selection telemetry maintained by the planner. See decision-tree §5.
    consecutive_unknown_count   INT         NOT NULL DEFAULT 0,
    running_strong_count        INT         NOT NULL DEFAULT 0,
    global_difficulty_offset    INT         NOT NULL DEFAULT 0,
    stretch_mode                BOOLEAN     NOT NULL DEFAULT FALSE,
    total_follow_ups_used       INT         NOT NULL DEFAULT 0,

    started_at                  TIMESTAMPTZ,
    finished_at                 TIMESTAMPTZ,
    scored_at                   TIMESTAMPTZ,
    final_score                 REAL,
    metadata                    JSONB,

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_session_user   ON interview_session(user_id);
CREATE INDEX idx_session_status ON interview_session(status);


-- ─── Per-session topic state (Topic Matrix, §1 of selection doc) ─────────────
-- Status drives the decision tree's "is this topic done?" question.
-- Composite PK on (session, kind, value) lets the planner upsert in one round
-- trip without a surrogate id.

CREATE TABLE session_topic_state (
    session_id              UUID        NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    topic_kind              VARCHAR(20) NOT NULL,            -- COMPETENCY | DOMAIN
    topic_value             VARCHAR(50) NOT NULL,            -- e.g. "CONFLICT_RESOLUTION", "DATABASE"
    status                  VARCHAR(20) NOT NULL,            -- NOT_TESTED|PROBING|STRONG|ADEQUATE|PARTIAL|WEAK|UNKNOWN
    importance              VARCHAR(10) NOT NULL,            -- HIGH|MED|LOW (snapshotted from blueprint)
    target_difficulty       VARCHAR(10) NOT NULL,            -- EASY|MEDIUM|HARD
    questions_asked         INT         NOT NULL DEFAULT 0,
    follow_ups_used         INT         NOT NULL DEFAULT 0,
    last_difficulty         VARCHAR(10),
    last_score              REAL,
    last_assessment         JSONB,                            -- snapshot of last AssessmentVerdict
    closed_at               TIMESTAMPTZ,
    PRIMARY KEY (session_id, topic_kind, topic_value)
);
CREATE INDEX idx_topic_state_session ON session_topic_state(session_id);


-- ─── Per-session questions ──────────────────────────────────────────────────
-- One row per question pinned to a session, in display order. Carries enough
-- metadata that a session is replayable even if question-bank later edits or
-- deactivates the underlying question.
--
-- source values:
--   BANK                  — question_id refs question-bank.questions
--   PRE_AUTHORED_FOLLOWUP — pulled from question-bank.question_follow_up,
--                           parent_question_id refs the bank question it
--                           probes
--   AI_GENERATED          — generated on-the-fly by ai-service POST
--                           /follow-up/generate; question_id is NULL,
--                           inline_text + inline_expected_points carry the
--                           content (NOT written back to question-bank)

CREATE TABLE session_question (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id                  UUID        NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    sequence                    INT         NOT NULL,
    question_id                 VARCHAR(36),                  -- NULL when source = AI_GENERATED
    question_type               VARCHAR(20) NOT NULL,         -- BEHAVIORAL | CORE_CONCEPTUAL | LIVE_CODING
    topic_kind                  VARCHAR(20),
    topic_value                 VARCHAR(50),
    difficulty                  VARCHAR(10),

    -- Follow-up linkage. parent_question_id refs the question-bank question
    -- the follow-up probes; parent_session_question_id refs the previous
    -- session_question row in the same session so the planner can walk the
    -- chain without joining back to question-bank.
    parent_question_id          VARCHAR(36),
    parent_session_question_id  UUID        REFERENCES session_question(id),
    is_follow_up                BOOLEAN     NOT NULL DEFAULT FALSE,
    source                      VARCHAR(30) NOT NULL DEFAULT 'BANK',

    inline_text                 TEXT,                         -- only for AI_GENERATED
    inline_expected_points      JSONB,                        -- only for AI_GENERATED
    snapshot                    JSONB,                        -- frozen copy of bank question (text, audio_key, rubric); NULL for AI_GENERATED

    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (session_id, sequence)
);
CREATE INDEX idx_session_question_session  ON session_question(session_id);
CREATE INDEX idx_session_question_question ON session_question(question_id);


-- ─── Answers ────────────────────────────────────────────────────────────────
-- One row per submitted answer. Pinned to a session_question (not the bank
-- question id) so a follow-up's answer is unambiguously distinguishable from
-- the parent question's answer.
--
-- Lifecycle: SUBMITTED → PROCESSING → READY → EVALUATING → SCORED | FAILED
--
-- The old (session_id, question_id) UNIQUE from interview-service-design.md
-- is intentionally NOT created here — follow-ups share parent_question_id and
-- the same session, so a unique key on those would block legitimate inserts.

CREATE TABLE answer (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID        NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    session_question_id UUID        NOT NULL REFERENCES session_question(id) ON DELETE CASCADE,

    type                VARCHAR(10) NOT NULL,                 -- VIDEO | CODE
    status              VARCHAR(20) NOT NULL,                 -- SUBMITTED|PROCESSING|READY|EVALUATING|SCORED|FAILED

    storage_object_id   UUID,                                  -- only for VIDEO
    transcript_id       UUID,                                  -- only for VIDEO, set when READY
    code                TEXT,                                  -- only for CODE
    language            VARCHAR(20),                           -- only for CODE

    score               REAL,
    max_score           REAL,
    rubric_scores       JSONB,
    feedback            TEXT,

    -- Derived from raw_evaluation by AssessmentVerdictMapper. The decision
    -- tree reads verdict, never raw_evaluation directly.
    verdict             JSONB,
    raw_evaluation      JSONB,

    error_code          VARCHAR(40),
    error_message       TEXT,

    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scored_at           TIMESTAMPTZ
);
CREATE INDEX idx_answer_session          ON answer(session_id);
CREATE INDEX idx_answer_session_question ON answer(session_question_id);
CREATE INDEX idx_answer_status           ON answer(status);
CREATE INDEX idx_answer_storage          ON answer(storage_object_id);


-- ─── Audit log: answer state transitions ────────────────────────────────────
-- Append-only. Every flip in answer.status writes one row. Used for debugging
-- "why is this stuck" + after-the-fact accuracy analysis.

CREATE TABLE answer_event_log (
    id          BIGSERIAL    PRIMARY KEY,
    answer_id   UUID         NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    reason      VARCHAR(100),
    metadata    JSONB,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_answer_event_answer ON answer_event_log(answer_id);


-- ─── Outbox: transactional Kafka publish ────────────────────────────────────
-- Writes to this table are committed in the same transaction as the business
-- write that triggered the event. A background poller reads
-- (published=false) rows, publishes, then sets published=true.
-- Partial index keeps the unread list tiny even when the table grows large.

CREATE TABLE outbox_event (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    topic           VARCHAR(100) NOT NULL,
    aggregate_id   UUID,
    event_type      VARCHAR(50)  NOT NULL,
    payload         JSONB        NOT NULL,
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished
    ON outbox_event(published, created_at)
    WHERE published = FALSE;


-- ─── Inbound idempotency: processed-event log ───────────────────────────────
-- Settles the §10 design open question (Redis vs in-memory) by picking the
-- DB-backed option: dedupe is then transactional with the consumer-side
-- business write, so a crash between dedupe and write cannot leak a duplicate.

CREATE TABLE processed_event (
    event_id     VARCHAR(64)  PRIMARY KEY,
    event_type   VARCHAR(50)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
