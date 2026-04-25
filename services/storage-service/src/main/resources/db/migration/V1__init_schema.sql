-- Storage Service — Initial Schema
-- PostgreSQL

CREATE TABLE IF NOT EXISTS storage_object (
    id              VARCHAR(36)  PRIMARY KEY,
    kind            VARCHAR(20)  NOT NULL,
    bucket          VARCHAR(100) NOT NULL,
    object_key      VARCHAR(500) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    size_bytes      BIGINT,
    sha256          VARCHAR(64),
    status          VARCHAR(20)  NOT NULL,
    owner_user_id   VARCHAR(36),
    session_id      VARCHAR(36),
    question_id     VARCHAR(36),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    UNIQUE (bucket, object_key)
);

CREATE INDEX IF NOT EXISTS idx_storage_object_kind          ON storage_object(kind);
CREATE INDEX IF NOT EXISTS idx_storage_object_status        ON storage_object(status);
CREATE INDEX IF NOT EXISTS idx_storage_object_owner_user_id ON storage_object(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_storage_object_session_id    ON storage_object(session_id);
CREATE INDEX IF NOT EXISTS idx_storage_object_question_id   ON storage_object(question_id);
CREATE INDEX IF NOT EXISTS idx_storage_object_created_at    ON storage_object(created_at DESC);
