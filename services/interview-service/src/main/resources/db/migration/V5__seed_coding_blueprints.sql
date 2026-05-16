-- Interview Service — seed CODING blueprints
--
-- Adds interview_type='CODING' rows for the same four (role, level) combos
-- V2/V3 already cover, so /start with interviewType=CODING resolves a
-- blueprint instead of returning BLUEPRINT_NOT_FOUND (4042).
--
-- CODING is non-adaptive: there is no topic matrix, no planner and no
-- follow-ups. The `topics` array is reused purely as an ordered
-- *difficulty plan* — one slot per question. The picker reads
-- targetDifficulty per slot (ordered by orderHint) and pulls that many
-- distinct LIVE_CODING questions from question-bank. `kind` / `topicValue`
-- are intentionally omitted (BlueprintTopic ignores unknown/missing JSON
-- props) since coding questions have no competency/domain; question_budget
-- equals the number of slots.
--
-- Inserts use WHERE NOT EXISTS (same rationale as V3) so the migration is
-- idempotent and safe to re-run after a manual backfill.

-- ─── BACKEND-junior-CODING ───────────────────────────────────────────────────
-- 3 problems, easing in: two EASY warm-ups then one MEDIUM.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'BACKEND', 'junior', 'CODING',
    '[
        {"importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"importance":"MED", "targetDifficulty":"EASY",   "orderHint":2},
        {"importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":3}
    ]'::jsonb,
    3, 0, 0, 75, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='BACKEND' AND level='junior' AND interview_type='CODING' AND is_default=TRUE
);

-- ─── BACKEND-mid-CODING ──────────────────────────────────────────────────────
-- 4 problems ramping EASY → MEDIUM → MEDIUM → HARD.
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'BACKEND', 'mid', 'CODING',
    '[
        {"importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":2},
        {"importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":3},
        {"importance":"MED", "targetDifficulty":"HARD",   "orderHint":4}
    ]'::jsonb,
    4, 0, 0, 110, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='BACKEND' AND level='mid' AND interview_type='CODING' AND is_default=TRUE
);

-- ─── FRONTEND-mid-CODING ─────────────────────────────────────────────────────
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'FRONTEND', 'mid', 'CODING',
    '[
        {"importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":2},
        {"importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":3},
        {"importance":"MED", "targetDifficulty":"HARD",   "orderHint":4}
    ]'::jsonb,
    4, 0, 0, 110, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='FRONTEND' AND level='mid' AND interview_type='CODING' AND is_default=TRUE
);

-- ─── FULLSTACK-mid-CODING ────────────────────────────────────────────────────
INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
)
SELECT
    'FULLSTACK', 'mid', 'CODING',
    '[
        {"importance":"MED", "targetDifficulty":"EASY",   "orderHint":1},
        {"importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":2},
        {"importance":"HIGH","targetDifficulty":"MEDIUM", "orderHint":3},
        {"importance":"MED", "targetDifficulty":"HARD",   "orderHint":4}
    ]'::jsonb,
    4, 0, 0, 110, FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM interview_blueprint
    WHERE target_role='FULLSTACK' AND level='mid' AND interview_type='CODING' AND is_default=TRUE
);
