Here is Claude's plan:
╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌
Fix Judge: compile-once batched execution + transient-error retry

Context

Two user-reported problems share one root cause: judge-service submits one
Judge0 submission per test case.

1. Run/Submit chậm. Each submission recompiles the program. Measured single-
   submission wall time on prod: python ~0.5s, js ~0.7–1.3s, cpp ~2s,
   java ~5.5s (javac+JVM dominates). A 24-case Java Submit therefore pays
   ~24× compile ≈ 30s+ even spread over workers. This is the latency.
2. False RE. 24 submissions/run flood Judge0's queue → 503 queue is full,
   and isolate occasionally returns status 13 (Internal Error) under load.
   JudgeOrchestrator.resolveTaskStatus (judge-service .../service/JudgeOrchestrator.java:269)
   maps status 13/14 straight to RE with no execution retry. During the
   2026-06-13 verify, 13/100 correct solutions got a spurious RE (each missing
   exactly 1 case); all 13 flipped to AC on individual retry.

Goal (user picked both): make each Run/Submit one Judge0 submission that
runs all cases in a single execution (compile once → ~N× faster, ~N× less
queue load → 503 largely gone), and retry transient Judge0 internal errors
instead of marking RE. Worker scaling (2→4) and MAX_QUEUE_SIZE 60→200 from this
session stay.

Approach

Part A — Batched, single-submission execution (compile once)

Batch stdin protocol (extends the current one in
.../codebuilder/StdinBuilder.java; today: line0=meta JSON, then 1 line/param):
- line0 = functionMeta JSON (one per job — same code for all cases)
- line1 = T (case count)
- then T blocks, each params.length lines (the existing per-param
  serialization, reused verbatim via TypeSerializer).
- Add StdinBuilder.buildBatch(FunctionMeta, List<Map<String,Object>>).

Drivers (.../resources/drivers/Universal{Python,Js,Java,Cpp}Driver.*):
loop T times; wrap each case's Solution call in try/catch; frame each
case's output so partial/failed runs stay per-case attributable:
- emit \x1e (RS, 0x1E) + OK/ERR + \n + body, then flush stdout after
  each case (so output survives a later crash). RS never appears in these
  answers; body may be multi-line and is read until the next RS.
- keep all existing parse/serialize helpers (_parse_value, _to_json_top_level,
  tree/list builders, etc.) unchanged — only _main()/main() becomes a loop.

JudgeOrchestrator.handle() (.../service/JudgeOrchestrator.java:65): build
fullSource once + one batch stdin, persist per-case JudgeTaskResult rows
(kept for the per-case UI breakdown) plus fullSource, batchStdin, token
on the JudgeJob, then one judge0Client.submitAsync(...) with callback
/api/v1/judge/callback/job/{jobId}.

Callback becomes per-job (Judge0CallbackController +
handleCallback): parse the single Judge0 result:
- status 6 (CE) → all cases CE; status 5 (TLE) → all/remaining TLE.
- status 3 → split stdout on RS into per-case bodies; for each, OK →
  OutputComparator.compare(body, expected, orderMatters) → AC/WA, ERR → RE.
  Cases with no emitted body (process died mid-batch) → RE.
- then VerdictAggregator.aggregate(...) (unchanged) → finalize → publish.
  The per-task incrementDoneCases/findByIdForUpdate race machinery is no longer
  needed (one callback/job); keep markDoneIfRunning as the idempotency guard
  against duplicate callbacks.

Part B — Retry transient Judge0 internal error

In the per-job callback, if status ∈ {13,14} (Internal/Exec-Format Error) and
job.retryCount < judge0.exec-max-retries (new config, default 2): increment
retryCount, re-submitAsync the stored fullSource+batchStdin with the same
callback URL, and return without finalizing. On exhaustion → mark remaining
cases RE + finalize. (Submit-side Retry.backoff for 429/502/503/504 in
Judge0Client.java:93 already exists and stays.)

DB migration

New judge-service/.../db/migration/V3__batch_execution.sql: add to
judge_jobs: full_source LONGTEXT, batch_stdin LONGTEXT,
retry_count INT NOT NULL DEFAULT 0, judge0_token VARCHAR(64). (Per-task
judge0_token column stays for back-compat, unused in batch mode.)

Judge0 tuning (/root/judge/judge0/judge0.conf on VPS)

Per-job execution now covers all cases, so raise CPU_TIME_LIMIT/WALL_TIME_LIMIT
headroom (e.g. CPU 15→20, wall 20→30) — correct solutions still finish <1s, but a
24-case batch needs slack. Recreate judge0-server then restart
judge-service (stale-IP gotcha, see memory project_judge0_scaling_and_recreate).

Files (representative)

- judge-service/src/main/resources/drivers/UniversalPythonDriver.py (+ Js/Java/Cpp) — batch loop + RS framing + per-case try/catch + flush
- judge-service/src/main/java/com/interview/judge/codebuilder/StdinBuilder.java — buildBatch
- judge-service/src/main/java/com/interview/judge/service/JudgeOrchestrator.java — one submission/job, batched callback parse, status-13 retry
- judge-service/src/main/java/com/interview/judge/controller/Judge0CallbackController.java — route by jobId
- judge-service/src/main/java/com/interview/judge/entity/JudgeJob.java — new fields
- judge-service/src/main/resources/db/migration/V3__batch_execution.sql — new
- judge-service/src/main/resources/application*.yml — judge0.exec-max-retries
- Reused unchanged: TypeSerializer, OutputComparator, VerdictAggregator, CodeBuilder injection, Judge0Client.submitAsync

Verification

1. Driver suite (safety net): adapt the local harness
   question-bank-service/seeds/coding/verify_drivers.py (+ engine.py) to the
   batch protocol and re-run all 2871 cases × 4 langs — must stay byte-identical.
2. Unit/driver tests: update src/test/python/test_python_driver.py,
   src/test/js/test_js_driver.js, CppDriverIntegrationTest.java to batch I/O.
3. Local boot judge-service (Flyway V3 applies) — see memory
   feedback_spring_service_runtime_gotchas.
4. Live, after deploy: rerun seeds/coding/verify_orig25.py — expect 100/100
   AC with no spurious RE, and time a Java 24-case Submit (expect ~5–8s vs
   ~30s before). Light concurrency (e.g. 20 parallel submits) should show ~0 × 503
   in judge-service logs.
5. Clean up test submissions from practice.practice_submission (admin user) and
   AC with no spurious RE, and time a Java 24-case Submit (expect ~5–8s vs
   ~30s before). Light concurrency (e.g. 20 parallel submits) should show ~0 × 503
   judge0_token column stays for back-compat, unused in batch mode.)

   Judge0 tuning (/root/judge/judge0/judge0.conf on VPS)

   Per-job execution now covers all cases, so raise CPU_TIME_LIMIT/WALL_TIME_LIMIT
   headroom (e.g. CPU 15→20, wall 20→30) — correct solutions still finish <1s, but a
   24-case batch needs slack. Recreate judge0-server then restart
   judge-service (stale-IP gotcha, see memory project_judge0_scaling_and_recreate).

   Files (representative)

    - judge-service/src/main/resources/drivers/UniversalPythonDriver.py (+ Js/Java/Cpp) — batch loop + RS framing + per-case try/catch + flush
    - judge-service/src/main/java/com/interview/judge/codebuilder/StdinBuilder.java — buildBatch
    - judge-service/src/main/java/com/interview/judge/service/JudgeOrchestrator.java — one submission/job, batched callback parse, status-13 retry
    - judge-service/src/main/java/com/interview/judge/controller/Judge0CallbackController.java — route by jobId
    - judge-service/src/main/java/com/interview/judge/entity/JudgeJob.java — new fields
    - judge-service/src/main/resources/db/migration/V3__batch_execution.sql — new
    - judge-service/src/main/resources/application*.yml — judge0.exec-max-retries
    - Reused unchanged: TypeSerializer, OutputComparator, VerdictAggregator, CodeBuilder injection, Judge0Client.submitAsync

   Verification

    1. Driver suite (safety net): adapt the local harness
       question-bank-service/seeds/coding/verify_drivers.py (+ engine.py) to the
       batch protocol and re-run all 2871 cases × 4 langs — must stay byte-identical.
    2. Unit/driver tests: update src/test/python/test_python_driver.py,
       src/test/js/test_js_driver.js, CppDriverIntegrationTest.java to batch I/O.
    3. Local boot judge-service (Flyway V3 applies) — see memory
       feedback_spring_service_runtime_gotchas.
    4. Live, after deploy: rerun seeds/coding/verify_orig25.py — expect 100/100
       AC with no spurious RE, and time a Java 24-case Submit (expect ~5–8s vs
       ~30s before). Light concurrency (e.g. 20 parallel submits) should show ~0 × 503
       in judge-service logs.
    5. Clean up test submissions from practice.practice_submission (admin user) and
       judge0_db.submissions afterwards (as done this session).

   Risks / notes

    - Per-case isolation: try/catch covers thrown exceptions; a hard crash
      (C++ segfault / OOM) still kills the process — already-flushed cases keep their
      verdict, the rest become RE (correct, just coarser). Per-case flush is required.
    - One TLE case now TLEs the whole batch (shared process limit) → job verdict TLE.
      Acceptable; raised limits keep correct solutions safe.
    - Deploy drains in-flight jobs (callback scheme changes); low traffic, do during a
      quiet window. No frontend or practice-service API change — Run/Submit/poll
      contract is unchanged.
