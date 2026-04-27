-- Migrate coding_questions.starter_code from TEXT to JSONB.
-- Old TEXT values (if any) are wrapped under the "java" key so existing
-- starter code is not lost. New rows write the full 4-language object.

ALTER TABLE coding_questions
    ALTER COLUMN starter_code TYPE JSONB
    USING CASE
        WHEN starter_code IS NULL THEN NULL
        ELSE jsonb_build_object('java', starter_code)
    END;
