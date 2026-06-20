# Plan: Enrich AI grading context for more faithful, better-calibrated interview scores

> **Purpose of this doc.** A self-contained implementation plan to be executed in a
> fresh session. It assumes no prior context. Read it top-to-bottom, then work the
> phases in order. Every change is a **two-sided** change: the Java `interview-service`
> must put new fields into the AI request payload, AND the Python `AI/` service must
> extend its Pydantic models + prompts to actually consume them. Adding payload
> fields alone changes nothing.

## Goal

The AI grader currently scores each answer with almost no situational context. Make it
understand: (1) the **difficulty** of the question, (2) that an answer is a **follow-up**
and the parent Q&A + the gap being probed, (3) the candidate's **level/role**, and
(4) the **adaptive trajectory** (a fast/strong candidate gets escalated to harder
questions). Outcome: per-answer feedback that judges follow-ups correctly, and a final
score that is calibrated to difficulty/level instead of treating every question the same.

## Repository layout

- Java orchestrator: `services/interview-service/` (Spring Boot, Maven).
- Python AI service: `AI/` (FastAPI + LangGraph; per-answer evaluator + overall reviewer).
- Build/test Java: from `services/interview-service/` run `mvn -o test`.
- Deploy: merge to `main` → GitHub Actions `CD — Build & Deploy` → VPS. After deploy,
  **recreated backend containers get a new IP → restart `api-gateway`** (ssh `contabo`,
  `docker restart mockwise-api-gateway-1`). No DB migration is involved in this plan.

---

## Background: the two AI grading paths

### 1. Per-answer grading (immediate, per question)
- Java builds the request in `AnswerService.stageSpokenEvaluation(...)`
  (`services/interview-service/src/main/java/com/mockwise/interview/service/AnswerService.java:318-386`)
  and stages it to the `evaluation-requested` outbox topic. (CODE answers use
  `stageCodingEvaluationRequested`, `AnswerService.java:999`.)
- Python consumes it; the payload is parsed into `BehavioralInput` / `ConceptualInput`
  (`AI/models/inputs.py:75-118`), graded by
  `AI/evaluator/nodes/behavioral_evaluator.py` / `conceptual_evaluator.py` using
  prompts in `AI/evaluator/prompts/behavioral.py` / `conceptual.py`.
- The result returns on `evaluation-completed` and is applied by
  `AnswerService.applyEvaluationCompleted` (`AnswerService.java:1092`), which stores
  `Answer.score` (0–10) and runs the planner.

**Current per-answer payload (`stageSpokenEvaluation`):**
```
question: { id, text, competency|domain, expectedSignals|keyConcepts, depthExpected }
answer:   { transcript, durationSeconds, language }
interviewType, responseLanguage
```

### 2. Overall session review (holistic, end of session)
- Java builds it in `SessionFinalizerService.buildPayload(...)`
  (`services/interview-service/src/main/java/com/mockwise/interview/service/SessionFinalizerService.java:154-244`),
  staged on `session-evaluation-requested` when the gate fires.
- Python parses it into `SessionEvaluationPayload` / `SessionAnswerSummary`
  (`AI/models/session_review.py:18-68`), graded by `AI/overall_reviewer/` using
  `AI/overall_reviewer/prompts/overall_review.py`. Output is `OverallReviewOutput`
  with `overall_score` bounded 0–10 (`session_review.py:82-91`).

**Current overall per-answer item (`SessionAnswerSummary`)** already includes:
`sequence, question_type, topic_kind, topic_value, difficulty, is_follow_up, text,
expected_signals, key_concepts, depth_expected, transcript, code, per_answer_score,
per_answer_verdict, answer_status`. Top-level has `target_role, level, blueprint`.

---

## Gap analysis (data exists in DB but is NOT sent to the grader)

| Context | Source in DB | Per-answer | Overall |
|---|---|---|---|
| Question **difficulty** (EASY/MED/HARD) | `SessionQuestion.difficulty` | ❌ not sent | ✅ sent, but **prompt ignores it** |
| **is_follow_up** flag | `SessionQuestion.isFollowUp` | ❌ | ✅ flag only |
| **Parent question text + parent answer** | `SessionQuestion.parentSessionQuestionId` → parent row + its `Answer` | ❌ | ❌ (only the flag) |
| **Probing gap / rationale** of a follow-up | follow-up `snapshot.rationale` (set in `QuestionPicker.buildAiSnapshot`, `QuestionPicker.java:512`) | ❌ | ❌ |
| Candidate **level / role** | `InterviewSession.level`, `targetRole` | ❌ | ✅ |
| **Adaptive state** (fast/strong → escalated) | `InterviewSession.runningStrongCount`, `stretchMode`, `globalDifficultyOffset` (`InterviewSession.java:70-82`) | ❌ | ❌ |
| Per-answer **pace** | `durationSeconds` | ✅ sent (prompt use?) | ❌ |
| **expectedPoints** rubric of AI follow-ups | `SessionQuestion.inlineExpectedPoints` | ❌ (only signals) | ❌ |

**Biggest fidelity gap:** a follow-up is graded **blind** — the grader sees a question
that only makes sense given the parent Q&A, with no parent context and no statement of
the gap being probed, so it cannot judge whether the follow-up resolved that gap.
**Second:** difficulty is never weighted, so a strong answer to a HARD question scores
the same as the same answer to an EASY one.

---

## Design decisions (read before coding)

1. **Per-answer score stays an absolute 0–10 = "how well did they answer THIS question."**
   The planner consumes `Answer.score` against fixed thresholds
   (`NextQuestionPlanner.CASE_A_SCORE_THRESHOLD=3.0`, `CASE_D_SCORE_THRESHOLD=7.0`,
   `ADEQUATE_SCORE_THRESHOLD=6.0`). Do **not** make the per-answer score "relative to
   difficulty" — that silently shifts what those thresholds mean and changes adaptivity.
   Instead, the new per-answer context (difficulty, follow-up parent, level) is for the
   grader to **set the right bar and understand the question**, not to rescale the number.

2. **Difficulty/level weighting goes into the OVERALL reviewer**, which produces the
   final reported score and already owns aggregation. That's where "harder questions
   count more" and "a junior isn't held to senior depth" belong.

3. **camelCase ↔ snake_case.** Python models use `CamelModel` (snake_case fields,
   camelCase JSON aliases). Java must emit **camelCase** keys; Python fields are
   snake_case. Match the existing pattern in `stageSpokenEvaluation` / `buildPayload`.

4. **Backward compatibility.** Make every new Python field `Optional` with a default so
   in-flight events produced before the deploy still parse. Roll out Java + Python
   together (CD builds both); the AI service tolerates missing fields.

---

## Phase 1 — Per-answer: difficulty (highest ROI, smallest change)

**Java** — `AnswerService.stageSpokenEvaluation` (`AnswerService.java:347-357`): add
`question.put("difficulty", sq.getDifficulty() != null ? sq.getDifficulty().name() : null);`

**Python**
- `AI/models/inputs.py`: add `difficulty: Optional[str] = None` to `BehavioralQuestion`
  (line ~75) and `ConceptualQuestion` (line ~98).
- `AI/evaluator/prompts/behavioral.py` and `conceptual.py`: surface difficulty in the
  prompt and instruct: *"Calibrate the expected depth to the question difficulty
  (EASY/MEDIUM/HARD). Score how well the answer meets the bar for THIS difficulty;
  do not penalise an EASY question for lacking HARD-level depth, and hold HARD
  questions to a higher bar."*

**Acceptance:** submit a HARD and an EASY question with comparable answers; the HARD
answer's feedback references a higher bar. Unit tests still green (`mvn -o test`).

---

## Phase 2 — Per-answer: follow-up lineage + probing gap (biggest fidelity win)

**Java** — in `stageSpokenEvaluation`, when `sq.isFollowUp()`:
1. Load the parent: `sessionQuestionRepo.findById(sq.getParentSessionQuestionId())`.
2. Load the parent's answer transcript: `answerRepo.findBySessionQuestionId(parent.getId())`
   → `extractTranscript(...)` (already a helper in `AnswerService`).
3. Read the gap from this follow-up's snapshot: `sq.getSnapshot().get("rationale")`
   (set by `QuestionPicker.buildAiSnapshot`, `QuestionPicker.java:512`).
4. Add a `context` block to the payload:
```
context: {
  isFollowUp: true,
  parentQuestionText: <parent.inlineText or parent.snapshot.text>,
  parentAnswerExcerpt: <parent transcript, truncate ~800 chars>,
  probingGap: <snapshot.rationale>          // why this follow-up was asked
}
```
For non-follow-up answers send `context: { isFollowUp: false }` (or omit).

**Python**
- `AI/models/inputs.py`: add an `EvalContext(CamelModel)` with
  `is_follow_up: bool = False`, `parent_question_text: Optional[str] = None`,
  `parent_answer_excerpt: Optional[str] = None`, `probing_gap: Optional[str] = None`;
  add `context: Optional[EvalContext] = None` to `BehavioralInput` and `ConceptualInput`.
- `behavioral.py` / `conceptual.py` prompts: when `context.is_follow_up`, add a section:
  *"This is a FOLLOW-UP. The candidate previously answered: «parent_question_text» →
  «parent_answer_excerpt». It was asked to probe this gap: «probing_gap». Judge whether
  this answer closes that gap and builds on the prior answer; do not re-grade the
  original question."*

**Acceptance:** a follow-up whose answer resolves the probed gap scores higher than one
that repeats the prior answer; feedback explicitly references the gap/parent.

---

## Phase 3 — Per-answer: level/role + expectedPoints

**Java** — `stageSpokenEvaluation`: add `payload.put("level", session.getLevel())` and
`payload.put("targetRole", session.getTargetRole())`. For follow-ups, also send
`question.put("expectedPoints", sq.getInlineExpectedPoints())`.

**Python**
- `inputs.py`: add `level: Optional[str] = None`, `target_role: Optional[str] = None`
  to `BehavioralInput` / `ConceptualInput`; add `expected_points: Optional[List[str]] = None`
  to the question models.
- prompts: *"Expectations scale with seniority (junior/mid/senior). Hold a senior to
  deeper reasoning; do not penalise a junior for missing senior-level depth."* Use
  `expected_points` as the rubric when present (richer than `expected_signals`).

**Acceptance:** same answer graded under `level=junior` vs `level=senior` yields a
higher bar (and typically lower score) for senior.

---

## Phase 4 — Overall reviewer: follow-up threading + adaptive trajectory + difficulty weighting

**Java** — `SessionFinalizerService.buildPayload` (per-answer loop ~`:171-200`):
- For follow-up entries add `parentQuestionText` and `parentAnswerExcerpt` (resolve via
  `parentSessionQuestionId`, same as Phase 2). Add `durationSeconds` per answer (from the
  `Answer`). Optionally add `probingGap`.
- At the top level add an adaptive-state block from the session:
```
adaptive: {
  stretchMode: session.isStretchMode(),
  runningStrongCount: session.getRunningStrongCount(),
  globalDifficultyOffset: session.getGlobalDifficultyOffset()
}
```
  (and/or per-answer `wasDeepen`/`wasStretch` if you choose to persist it; otherwise the
  difficulty progression across `sequence` already conveys escalation).

**Python**
- `AI/models/session_review.py`: add `parent_question_text`, `parent_answer_excerpt`,
  `probing_gap`, `duration_seconds` to `SessionAnswerSummary`; add an optional
  `adaptive` block to `SessionEvaluationPayload` (new `SessionAdaptiveState` model, all
  optional).
- `AI/overall_reviewer/prompts/overall_review.py`: instruct the reviewer to
  (a) **weight harder questions more** in the aggregate `overall_score`;
  (b) read the **trajectory** — *"if the candidate answered quickly and strongly and was
  escalated to harder questions (stretchMode / rising difficulty) and still handled them,
  reward that; if difficulty was lowered after weak answers, factor that in"*;
  (c) thread follow-ups to their parent for the narrative.
- Keep `overall_score` bounded 0–10 (planner/grade projections depend on it,
  `session_review.py:82-91`).

**Acceptance:** a session where the candidate aced base questions and the harder
deepen/follow-up questions scores higher than one that only answered easy questions;
the narrative references the difficulty escalation and follow-up threads.

---

## Cross-cutting verification

1. `cd services/interview-service && mvn -o test` — all green (35+ tests).
2. Python: run the AI service test suite / a local `/evaluate` and `/session-review`
   call with a crafted payload containing the new fields; confirm models parse and the
   prompt renders the new sections (check `AI/` test dir + `api.py`).
3. End-to-end on staging if available: a follow-up question's feedback must reference the
   parent/gap; a HARD question must be held to a higher bar.
4. Confirm **old-format events still parse** (all new Python fields Optional).

## Deploy (established flow)

1. Branch off `main`, commit Java + Python together (English commit messages).
2. Merge `--no-ff` to `main`, push → CD builds & deploys both services.
3. After deploy: `ssh contabo` → confirm `mockwise-interview-service-1` and the AI
   service container booted clean → `docker restart mockwise-api-gateway-1` (stale-IP).
4. Smoke-test one behavioral + one core session with a follow-up.

## Risks / watch-outs

- **Do not rescale per-answer scores by difficulty** (breaks planner thresholds — see
  Design decision #1). Difficulty weighting belongs only in the overall reviewer.
- **Prompt token bloat / cost:** truncate `parentAnswerExcerpt` (~800 chars) and
  transcripts; don't dump the whole session into every per-answer call.
- **Schema drift:** every new Python field must be `Optional` with a default, or
  in-flight Kafka events emitted before the deploy will fail to parse and strand answers.
- The default LLM for grading is Gemini in this project; **a bare `dict` in a
  structured-output schema triggers a Gemini 500** (additionalProperties) — model new
  blocks as explicit Pydantic models, not free dicts (see the existing
  `gemini_structured_output_no_bare_dict` lesson).
- Keep `overall_score` 0–10; `grade`/`hire_signal` are projections of it.

## File reference index

- `services/interview-service/.../service/AnswerService.java`
  — `stageSpokenEvaluation:318`, `applyEvaluationCompleted:1092`, `stageCodingEvaluationRequested:999`.
- `services/interview-service/.../service/SessionFinalizerService.java` — `buildPayload:154`.
- `services/interview-service/.../service/QuestionPicker.java` — `buildAiSnapshot:512`, `pickFollowUpFromAi:353`.
- `services/interview-service/.../entity/SessionQuestion.java` — difficulty / parent* / isFollowUp / snapshot / inlineExpectedPoints.
- `services/interview-service/.../entity/InterviewSession.java` — runningStrongCount / stretchMode / globalDifficultyOffset (`:70-82`), level / targetRole.
- `AI/models/inputs.py` — `BehavioralInput/Question/Answer:75`, `ConceptualInput/Question/Answer:98`.
- `AI/models/session_review.py` — `SessionAnswerSummary:18`, `SessionEvaluationPayload:51`, `OverallReviewOutput:82`.
- `AI/evaluator/prompts/behavioral.py`, `conceptual.py`; `AI/overall_reviewer/prompts/overall_review.py`.
- `AI/evaluator/nodes/behavioral_evaluator.py`, `conceptual_evaluator.py`.
