-- Lever 2 (realtime-stt-plan.md §7): take video upload + batch STT off the
-- submit critical path. The fast path scores from a real-time transcript the
-- browser builds while the candidate speaks; the batch STT from the video
-- still runs in the background as the authoritative record.
--
--   transcript_source        — which transcript scored this answer.
--   realtime_transcript       — the text used to score (fast path).
--   authoritative_transcript  — the batch-STT text from the video, attached
--                               later for display/reconciliation (never
--                               re-scores by default — see §8.1 #3).
--   transcript_mismatch       — set true when reconcile finds the batch text
--                               diverges materially from the real-time one.
--
-- storage_object_id is already nullable (V1) — a fast-path answer is created
-- before its video finishes uploading and attaches the id afterwards.
ALTER TABLE answer
    ADD COLUMN transcript_source        VARCHAR(32) NULL,
    ADD COLUMN realtime_transcript      TEXT        NULL,
    ADD COLUMN authoritative_transcript TEXT        NULL,
    ADD COLUMN transcript_mismatch      BOOLEAN     NULL;
