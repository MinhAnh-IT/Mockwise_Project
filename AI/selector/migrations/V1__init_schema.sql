-- AI Question Selector — vector index for behavioral + core_conceptual questions.
-- Database is provisioned separately (e.g. `ai_question_selector` on the shared
-- Postgres host). The pgvector extension must be available — install once:
--   CREATE EXTENSION IF NOT EXISTS vector;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS question_index (
    id              VARCHAR(36)  PRIMARY KEY,
    type            VARCHAR(20)  NOT NULL,
    text            TEXT         NOT NULL,
    difficulty      VARCHAR(10),
    tags            TEXT[]       NOT NULL DEFAULT '{}',

    -- Behavioral
    competency        VARCHAR(40),
    expected_signals  TEXT[]      NOT NULL DEFAULT '{}',

    -- Core conceptual
    domain          VARCHAR(40),
    target_roles    TEXT[]       NOT NULL DEFAULT '{}',
    key_concepts    TEXT[]       NOT NULL DEFAULT '{}',
    depth_expected  TEXT,

    embedding       vector(768)  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT true,
    indexed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_event_at   TIMESTAMPTZ
);

-- HNSW gives sub-linear cosine search at the cost of a small recall trade-off.
-- For the small pool we expect (low thousands of questions) this is more than
-- enough; for larger banks consider tuning ef_construction / m.
CREATE INDEX IF NOT EXISTS idx_question_index_embedding
    ON question_index USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_question_index_type_active
    ON question_index (type, active);

CREATE INDEX IF NOT EXISTS idx_question_index_competency
    ON question_index (competency) WHERE competency IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_question_index_domain
    ON question_index (domain) WHERE domain IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_question_index_target_roles
    ON question_index USING gin (target_roles);

CREATE INDEX IF NOT EXISTS idx_question_index_key_concepts
    ON question_index USING gin (key_concepts);


-- Idempotency log for Kafka consumer. We dedup on event_id so replay does not
-- cause re-embed thrash. Old rows can be vacuumed on a schedule (out of scope
-- here) — keeping every event_id forever is fine for a question-bank-sized stream.
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     VARCHAR(64)  PRIMARY KEY,
    event_type   VARCHAR(40)  NOT NULL,
    question_id  VARCHAR(36)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_question_id
    ON processed_events (question_id);
