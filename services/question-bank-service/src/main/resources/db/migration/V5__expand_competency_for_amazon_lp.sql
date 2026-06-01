-- Question Bank — widen competency for Amazon Leadership Principle tokens
--
-- The behavioral competency taxonomy moves to Amazon's 16 Leadership Principles
-- (see interview-service/docs/blueprint-catalog-and-question-bank-spec.md). The
-- enum is stored as a STRING in behavioral_questions.competency, and a few LP
-- tokens exceed the original VARCHAR(30):
--   HAVE_BACKBONE_DISAGREE_AND_COMMIT        (33)
--   STRIVE_TO_BE_EARTHS_BEST_EMPLOYER        (33)
--   SUCCESS_AND_SCALE_BROAD_RESPONSIBILITY   (38)
--
-- Widen the column to 64 to fit every LP token with headroom. No data is
-- rewritten — existing legacy values (OWNERSHIP, TEAMWORK, ...) stay valid; the
-- Competency enum keeps them in a LEGACY block. Idempotent: ALTER TYPE to the
-- same width is a no-op on re-run.

ALTER TABLE behavioral_questions
    ALTER COLUMN competency TYPE VARCHAR(64);
</content>
