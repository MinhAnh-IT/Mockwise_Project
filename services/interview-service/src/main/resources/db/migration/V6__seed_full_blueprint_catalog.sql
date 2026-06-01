-- Interview Service — authoritative blueprint catalog (V6)
--
-- Single source of truth for the (role x level x type) matrix. SUPERSEDES the
-- seed data of V2 (MIXED), V3 (behavioral/core) and V5 (coding). Design rationale,
-- topic kits, budget/time formula and the minimum question-bank spec live in
-- services/interview-service/docs/blueprint-catalog-and-question-bank-spec.md.
--
-- DESIGN STANCE: each blueprint models ONE realistic interview ROUND — depth over
-- breadth. Few topics, generous follow-up budget so the planner probes deeply
-- (real interviews live in follow-ups), not a wide shallow trivia sweep.
--
-- Interview types: BEHAVIORAL, CORE, CODING. There is NO dedicated system-design
-- interview; SYSTEM_DESIGN appears only as an ordinary CORE conceptual domain
-- (architecture-relevant roles), never as a stand-alone round.
--
-- BEHAVIORAL competency taxonomy = Amazon's 16 Leadership Principles (question-bank
-- Competency enum + question-bank migration V5). Role/level tokens are the CLEAN
-- BlueprintNormalizer forms (QA, AI_ML, BA; Director/Manager/Principal -> senior) —
-- assumes that normalizer change is deployed.
--
-- Invariants:
--   * question_budget >= #topics + max_follow_ups_per_session (full coverage)
--   * BEHAVIORAL/CODING role-agnostic (3 level templates); CORE per-role kit.
--   * BA has no CODING; BA CORE leans BUSINESS_ANALYSIS, not deep engineering.
--   * CODING = 2 problems per round (one real coding round, not an online assessment).
--
-- Cleanup (FK interview_session.blueprint_id -> interview_blueprint is RESTRICT):
--   1. Demote every existing default. 2. DELETE old rows no session references
--   (incl. orphan MIXED); referenced rows survive as non-default for history.
--   3. INSERT the catalog as the sole defaults. One-shot; Flyway applies once.

UPDATE interview_blueprint SET is_default = FALSE WHERE is_default = TRUE;

DELETE FROM interview_blueprint b
WHERE NOT EXISTS (SELECT 1 FROM interview_session s WHERE s.blueprint_id = b.id);


-- ===================== BEHAVIORAL (Amazon LPs, depth-first) =====================

-- ---- BACKEND . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- FRONTEND . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- FULLSTACK . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- MOBILE . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- DEVOPS . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- QA . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- DATA_ENGINEER . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- AI_ML . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);

-- ---- BA . BEHAVIORAL ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BA', 'junior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "LEARN_AND_BE_CURIOUS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    7, 2, 4, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BA', 'mid', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "CUSTOMER_OBSESSION", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "DELIVER_RESULTS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    9, 3, 6, 50, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BA', 'senior', 'BEHAVIORAL',
    '[{"kind": "COMPETENCY", "topicValue": "OWNERSHIP", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "COMPETENCY", "topicValue": "ARE_RIGHT_A_LOT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "COMPETENCY", "topicValue": "HAVE_BACKBONE_DISAGREE_AND_COMMIT", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 3}, {"kind": "COMPETENCY", "topicValue": "HIRE_AND_DEVELOP_THE_BEST", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    10, 3, 6, 55, FALSE, TRUE
);


-- ===================== CORE (per-role kit, depth-first) =====================

-- ---- BACKEND . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "DESIGN_PATTERN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- FRONTEND . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "FRONTEND_DEV", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRAMEWORK", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "FRONTEND_DEV", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRAMEWORK", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "FRONTEND_DEV", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRAMEWORK", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "DESIGN_PATTERN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- FULLSTACK . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRONTEND_DEV", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRONTEND_DEV", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRONTEND_DEV", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "FRAMEWORK", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- MOBILE . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "MOBILE_DEV", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRAMEWORK", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "MOBILE_DEV", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRAMEWORK", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "MOBILE_DEV", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "FRAMEWORK", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "DESIGN_PATTERN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- DEVOPS . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DEVOPS_TOOLS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "NETWORKING", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DEVOPS_TOOLS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "NETWORKING", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DEVOPS_TOOLS", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "NETWORKING", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "OS", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- QA . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "TESTING", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "TESTING", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "TESTING", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "LANGUAGE_SPECIFIC", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "NETWORKING", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- DATA_ENGINEER . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATA_ENGINEERING", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATA_ENGINEERING", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "DATA_ENGINEERING", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "MESSAGING", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- AI_ML . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "AI_ML", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATA_ENGINEERING", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "AI_ML", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATA_ENGINEERING", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "AI_ML", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATA_ENGINEERING", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "SYSTEM_DESIGN", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);

-- ---- BA . CORE ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BA', 'junior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "BUSINESS_ANALYSIS", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "EASY", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "TESTING", "importance": "MED", "targetDifficulty": "EASY", "orderHint": 3}]'::jsonb,
    6, 1, 3, 40, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BA', 'mid', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "BUSINESS_ANALYSIS", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "TESTING", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}]'::jsonb,
    7, 2, 4, 45, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BA', 'senior', 'CORE',
    '[{"kind": "DOMAIN", "topicValue": "BUSINESS_ANALYSIS", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"kind": "DOMAIN", "topicValue": "DATABASE", "importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}, {"kind": "DOMAIN", "topicValue": "TESTING", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 3}, {"kind": "DOMAIN", "topicValue": "SECURITY", "importance": "MED", "targetDifficulty": "MEDIUM", "orderHint": 4}]'::jsonb,
    9, 2, 5, 55, FALSE, TRUE
);


-- ===================== CODING (2 problems / round) =====================

-- ---- BACKEND . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'BACKEND', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);

-- ---- FRONTEND . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FRONTEND', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);

-- ---- FULLSTACK . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'FULLSTACK', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);

-- ---- MOBILE . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'MOBILE', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);

-- ---- DEVOPS . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DEVOPS', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);

-- ---- QA . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'QA', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);

-- ---- DATA_ENGINEER . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'DATA_ENGINEER', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);

-- ---- AI_ML . CODING ----

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'junior', 'CODING',
    '[{"importance": "MED", "targetDifficulty": "EASY", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 2}]'::jsonb,
    2, 0, 0, 60, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'mid', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "MEDIUM", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 80, FALSE, TRUE
);

INSERT INTO interview_blueprint (
    target_role, level, interview_type, topics,
    question_budget, max_follow_ups_per_topic, max_follow_ups_per_session,
    time_budget_minutes, use_ai_selector, is_default
) VALUES (
    'AI_ML', 'senior', 'CODING',
    '[{"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 1}, {"importance": "HIGH", "targetDifficulty": "HARD", "orderHint": 2}]'::jsonb,
    2, 0, 0, 90, FALSE, TRUE
);
