# Live-Coding (LeetCode-style) — FE↔BE contract

Status: **frontend implemented, backend NOT implemented.** The FE ships the
full LeetCode-style workspace today; it degrades to a built-in demo problem
until these endpoints exist. This doc is the spec the backend must satisfy.

Source of truth for the TypeScript shapes: `frontend/src/types/coding.ts`.

---

## 0. What already exists on the backend (reuse, don't rebuild)

- `question-bank · CodingQuestion` — `title`, `description` (markdown),
  `timeLimitMinutes`, `optimalTimeComplexity/SpaceComplexity`,
  `functionMeta {fn, params[{name,type}], return, orderMatters, inPlace}`,
  `starterCode {java, python, cpp, javascript}`,
  `testCases[{id, inputData, expectedOutput, is_hidden}]`.
- `QuestionPicker.buildSnapshot` already freezes all of the above into
  `session_question.snapshot` for `LIVE_CODING` (keys: `title`,
  `description`, `functionMeta`, `starterCode`, `testCases`).
- Final CODE submit already works:
  `POST /api/v1/interviews/{sid}/questions/{sqid}/answers`
  `{type:'CODE', code, language}` → Kafka `CODE_SUBMISSION` → judge-service
  → `LiveCodingEvaluationDetail` once the session is `SCORED`.
- judge-service has `POST /api/v1/judge/submit` (SubmissionEvent
  `{submissionId, language, code, functionMeta, testCases}`) +
  `GET /api/v1/judge/status/{submissionId}` (per-case `TaskResultView`),
  reachable through api-gateway route `/api/v1/judge/**`.

## 1. Gaps the backend must close

### 1a. `InterviewType.CODING` + picker path
`InterviewType` is `BEHAVIORAL|CORE|MIXED` only; nothing pins a
`LIVE_CODING` question into a session. Add `CODING` and a picker branch that
selects `LIVE_CODING` questions (snapshot already handles them). FE already
sends `POST /api/v1/interviews/start {interviewType:'CODING'}`.

### 1b. Expose the problem to the in-flight FE
`PinnedQuestionView.redacted()` strips everything but
`sessionQuestionId/sequence/questionType/text/audioUrl` mid-flight, and it
carries no coding fields. Add the endpoint below (reads the frozen snapshot;
**must drop `is_hidden` cases** before returning).

### 1c. "Run" passthrough
Per-question verdict stays hidden until `SCORED`. For the LeetCode "Run"
feel, add the run endpoints below; implement by calling judge-service
`/api/v1/judge/submit` with the snapshot's `functionMeta` + the non-hidden
sample `testCases`, then mapping `/judge/status/{id}` per case.

---

## 2. New endpoints

All responses use the standard `ApiResponse`/`unwrap` envelope (`data` payload).

### GET `/api/v1/interviews/{sid}/questions/{sqid}/coding`
→ `200 CodingProblemView`

```jsonc
{
  "sessionQuestionId": "uuid",
  "sequence": 2,
  "title": "Two Sum",
  "description": "markdown…",
  "timeLimitMinutes": 30,
  "optimalTimeComplexity": "O(n)",
  "optimalSpaceComplexity": "O(n)",
  "functionMeta": {
    "fn": "twoSum",
    "params": [{ "name": "nums", "type": "int[]" }, { "name": "target", "type": "int" }],
    "returnType": "int[]",          // BE `return` → serialize as returnType
    "orderMatters": false,
    "inPlace": false
  },
  "starterCode": { "java": "…", "python": "…", "cpp": "…", "javascript": "…" },
  "sampleTestCases": [               // is_hidden === false ONLY
    { "id": "tc1", "inputData": { "nums": [2,7,11,15], "target": 9 },
      "expectedOutput": { "result": [0,1] } }
  ]
}
```
- Auth: owner of `sid` only. 403 otherwise.
- Wrong type (`questionType != LIVE_CODING`) → `409`.
- Until implemented, return `404`/`501` — FE shows the demo problem.

### POST `/api/v1/interviews/{sid}/questions/{sqid}/code/run`
body `{ "language": "java|python|cpp|javascript", "code": "…" }`
→ `202 { "runId": "uuid" }`

Pull non-hidden sample `testCases` + `functionMeta` from the snapshot
(client never sends `expectedOutput` — keep it server-side), call
judge-service, return its `submissionId` as `runId`. Run results are **not**
persisted as the official answer and **must not** affect scoring.

### GET `/api/v1/interviews/{sid}/questions/{sqid}/code/run/{runId}`
→ `200 RunResultView`

```jsonc
{
  "status": "PENDING|RUNNING|DONE|FAILED",
  "compileError": "string|null",     // set ⇒ FE shows this instead of cases
  "cases": [
    { "index": 0, "testCaseId": "tc1", "status": "PASSED|FAILED|ERROR|TIMEOUT",
      "stdout": "[0,1]", "expected": "[0,1]", "stderr": null,
      "runtimeMs": 3, "memoryKb": 14336 }
  ]
}
```
Map from judge-service `GET /api/v1/judge/status/{submissionId}`
(`TaskResultView`): compare `stdout` vs expected → `PASSED/FAILED`,
`TaskStatus` error/timeout → `ERROR/TIMEOUT`; surface compile failure as
`compileError`.

## 3. Unchanged (FE already uses these)

- Start: `POST /api/v1/interviews/start {interviewType:'CODING'}` → first
  pinned question with `questionType:'LIVE_CODING'`.
- Submit: `POST /api/v1/interviews/{sid}/questions/{sqid}/answers`
  `{type:'CODE', code, language}`; FE then polls `GET /api/v1/interviews/{sid}`
  and advances exactly like the video flow.

## 4. FE artifacts (remove demo bits when BE lands)

- `src/components/practice/coding/mockProblem.ts` — demo fallback.
- `src/pages/practice/PracticeCodingPreviewPage.tsx` + route
  `/practice/coding/preview` — design-review only.
- Fallback branches keyed on `ApiError.status === 404 || 501` in
  `CodingWorkspace.tsx`.
