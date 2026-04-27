-- TTS-STT service schema (MySQL 8).
--
-- TTS cache lives in Redis (key: tts:cache:v1:<sha256>) and is intentionally
-- NOT persisted here. Cache loss = pay ElevenLabs once, no data corruption.
--
-- This migration provisions the durable parts: STT job tracking + transcripts.

CREATE TABLE stt_job (
    id                    CHAR(36)     NOT NULL,
    storage_object_id     CHAR(36)     NOT NULL,
    answer_id             CHAR(36)     NOT NULL,
    session_id            VARCHAR(64)  NOT NULL,
    question_id           VARCHAR(64)  NOT NULL,
    owner_user_id         VARCHAR(64)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,                       -- PENDING|PROCESSING|READY|FAILED
    error_code            VARCHAR(40)  NULL,
    error_message         TEXT         NULL,
    transcript_id         CHAR(36)     NULL,
    attempt_count         INT          NOT NULL DEFAULT 0,
    started_at            DATETIME(6)  NULL,
    finished_at           DATETIME(6)  NULL,
    published_event_at    DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_stt_job PRIMARY KEY (id),
    CONSTRAINT uq_stt_job_storage UNIQUE (storage_object_id),
    INDEX idx_stt_job_answer  (answer_id),
    INDEX idx_stt_job_session (session_id),
    INDEX idx_stt_job_status  (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transcript (
    id                    CHAR(36)     NOT NULL,
    stt_job_id            CHAR(36)     NOT NULL,
    storage_object_id     CHAR(36)     NOT NULL,
    answer_id             CHAR(36)     NOT NULL,
    session_id            VARCHAR(64)  NOT NULL,
    question_id           VARCHAR(64)  NOT NULL,
    text                  MEDIUMTEXT   NOT NULL,
    language_code         VARCHAR(10)  NOT NULL,
    language_confidence   FLOAT        NULL,
    words                 JSON         NOT NULL,
    duration_ms           INT          NULL,
    model_id              VARCHAR(64)  NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_transcript PRIMARY KEY (id),
    CONSTRAINT fk_transcript_job FOREIGN KEY (stt_job_id) REFERENCES stt_job(id),
    INDEX idx_transcript_answer  (answer_id),
    INDEX idx_transcript_session (session_id),
    INDEX idx_transcript_storage (storage_object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
