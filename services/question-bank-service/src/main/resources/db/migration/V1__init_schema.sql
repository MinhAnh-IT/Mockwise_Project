-- Question Bank Service — Initial Schema
-- PostgreSQL

CREATE TABLE IF NOT EXISTS questions (
    id          VARCHAR(36)  PRIMARY KEY,
    type        VARCHAR(20)  NOT NULL,
    difficulty  VARCHAR(10)  NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',
    tags        TEXT[]       NOT NULL DEFAULT '{}',
    created_by  VARCHAR(36)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS behavioral_questions (
    id               VARCHAR(36)  PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
    text             TEXT         NOT NULL,
    competency       VARCHAR(30)  NOT NULL,
    expected_signals TEXT[]       NOT NULL DEFAULT '{}',
    audio_key        VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS core_questions (
    id              VARCHAR(36)  PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
    text            TEXT         NOT NULL,
    target_roles    TEXT[]       NOT NULL DEFAULT '{}',
    domain          VARCHAR(30)  NOT NULL,
    key_concepts    TEXT[]       NOT NULL DEFAULT '{}',
    depth_expected  TEXT         NOT NULL,
    audio_key       VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS coding_questions (
    id                          VARCHAR(36)   PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
    title                       VARCHAR(255)  NOT NULL,
    description                 TEXT          NOT NULL,
    time_limit_minutes          INT           NOT NULL DEFAULT 30,
    optimal_time_complexity     VARCHAR(50)   NOT NULL,
    optimal_space_complexity    VARCHAR(50)   NOT NULL,
    function_meta               JSONB         NOT NULL,
    starter_code                TEXT,
    test_cases                  JSONB         NOT NULL DEFAULT '[]'
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_questions_type        ON questions(type);
CREATE INDEX IF NOT EXISTS idx_questions_difficulty  ON questions(difficulty);
CREATE INDEX IF NOT EXISTS idx_questions_status      ON questions(status);
CREATE INDEX IF NOT EXISTS idx_questions_tags        ON questions USING GIN(tags);
CREATE INDEX IF NOT EXISTS idx_questions_created_at  ON questions(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_behavioral_competency ON behavioral_questions(competency);

CREATE INDEX IF NOT EXISTS idx_core_domain           ON core_questions(domain);
CREATE INDEX IF NOT EXISTS idx_core_target_roles     ON core_questions USING GIN(target_roles);

CREATE INDEX IF NOT EXISTS idx_coding_title          ON coding_questions(title);
CREATE INDEX IF NOT EXISTS idx_coding_function_meta  ON coding_questions USING GIN(function_meta);
