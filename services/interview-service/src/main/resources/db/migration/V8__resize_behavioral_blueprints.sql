-- Interview Service — resize BEHAVIORAL blueprints to the time budget (V8)
--
-- WHY: the planner ends a session on coverage-complete (all topics asked), not
-- on question_budget. BEHAVIORAL blueprints carried only 3-4 topics, so sessions
-- ended after ~3 questions and used ~15 min of a 40-55 min time_budget_minutes
-- clock — the deadline was effectively dead weight. This sizes #topics to the
-- session time so a paced candidate actually fills the budget.
--
-- FORMULA: N = floor((time_budget - buffer) / (answer_cap + overhead))
--   answer_cap   = 5 min/question (BEHAVIORAL)
--   overhead     = ~0.5 min  (measured on prod: submit -> next-question shown
--                  averages 16s, max 44s; AI grading is async and non-blocking)
--   buffer       = ~3 min   (opening briefing + closing review)
--   => junior 40m -> 6, mid 50m -> 8, senior 55m -> 9 question slots.
-- N is split into guaranteed topics (fill the clock even with zero follow-ups)
-- plus a follow-up budget for depth on weak answers. CORE/CODING are unchanged:
-- with a 10-min CORE cap the existing 3-4 topics already match their clock.
--
-- CONSTRAINT: only 7 behavioral competencies are seeded in question-bank, each
-- with 3 EASY / 3 MEDIUM / 2 HARD ACTIVE+audio questions (V6 assumed Amazon's
-- 16 LPs but only 7 shipped). Max topics per session is therefore 7; senior=6
-- leaves one spare. Every (competency, targetDifficulty) used below is covered.
--
-- Invariant preserved: question_budget >= #topics + max_follow_ups_per_session.
--   junior 6 >= 4+2 ; mid 8 >= 5+3 ; senior 9 >= 6+3.
--
-- BEHAVIORAL is role-agnostic (one template per level shared by all 9 roles), so
-- each UPDATE rewrites all 9 default rows for its level at once. UPDATE (not
-- delete+insert) keeps blueprint ids, is_default, and the interview_session FK
-- intact so sessions already finished still resolve their blueprint for history.

-- ---- junior: 3 -> 4 topics, all EASY ----
UPDATE interview_blueprint
SET topics = '[
        {"kind": "COMPETENCY", "topicValue": "OWNERSHIP",            "importance": "MED",  "targetDifficulty": "EASY", "orderHint": 1},
        {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION",   "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2},
        {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3},
        {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS",      "importance": "MED",  "targetDifficulty": "EASY", "orderHint": 4}
    ]'::jsonb,
    question_budget = 6,
    max_follow_ups_per_topic = 2,
    max_follow_ups_per_session = 2,
    updated_at = now()
WHERE interview_type = 'BEHAVIORAL' AND level = 'junior' AND is_default = TRUE;

-- ---- mid: 3 -> 5 topics, EASY/MEDIUM ----
UPDATE interview_blueprint
SET topics = '[
        {"kind": "COMPETENCY", "topicValue": "OWNERSHIP",            "importance": "MED",  "targetDifficulty": "EASY",   "orderHint": 1},
        {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION",   "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2},
        {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS",      "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3},
        {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "MED",  "targetDifficulty": "MEDIUM", "orderHint": 4},
        {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT",      "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 5}
    ]'::jsonb,
    question_budget = 8,
    max_follow_ups_per_topic = 2,
    max_follow_ups_per_session = 3,
    updated_at = now()
WHERE interview_type = 'BEHAVIORAL' AND level = 'mid' AND is_default = TRUE;

-- ---- senior: 4 -> 6 topics, MEDIUM/HARD ----
UPDATE interview_blueprint
SET topics = '[
        {"kind": "COMPETENCY", "topicValue": "OWNERSHIP",                         "importance": "MED",  "targetDifficulty": "MEDIUM", "orderHint": 1},
        {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT",                   "importance": "HIGH", "targetDifficulty": "HARD",   "orderHint": 2},
        {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD",   "orderHint": 3},
        {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST",         "importance": "MED",  "targetDifficulty": "MEDIUM", "orderHint": 4},
        {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS",                   "importance": "HIGH", "targetDifficulty": "HARD",   "orderHint": 5},
        {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION",                "importance": "MED",  "targetDifficulty": "MEDIUM", "orderHint": 6}
    ]'::jsonb,
    question_budget = 9,
    max_follow_ups_per_topic = 2,
    max_follow_ups_per_session = 3,
    updated_at = now()
WHERE interview_type = 'BEHAVIORAL' AND level = 'senior' AND is_default = TRUE;
