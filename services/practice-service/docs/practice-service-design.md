# Practice Service — Design Document

Service cung cấp tính năng **luyện thuật toán kiểu LeetCode**: user duyệt kho đề coding, viết code trong workspace, bấm **Run** (chạy thử với sample cases) hoặc **Submit** (chấm với toàn bộ testcases gồm hidden), và **lưu lại lịch sử** submission + trạng thái đã giải + thống kê.

Đây là một **bounded context riêng**, tách khỏi `interview-service`. Practice **không** có AI scoring, **không** TTS, **không** session/timer/report — chỉ là vòng lặp `pick problem → run/submit → verdict → history/stats`. Service tái dùng nguyên `judge-service` (chạy code) và `question-bank-service` (kho đề), chỉ sở hữu thêm phần *lịch sử & trạng thái theo user*.

> Quyết định kiến trúc (đã chốt với chủ dự án):
> 1. **Service mới `practice-service`** — không nhồi vào interview-service/question-bank.
> 2. **Gửi code sang judge qua Kafka async**, phân biệt luồng bằng field `origin`.
> 3. Bản đầu làm **đầy đủ**: history + problem status (solved/attempted) + stats tổng hợp.

---

## Mục lục

- [1. Phạm vi & nguyên tắc](#1-phạm-vi--nguyên-tắc)
- [2. Kiến trúc](#2-kiến-trúc)
- [3. Domain model & state machine](#3-domain-model--state-machine)
- [4. Flows](#4-flows)
  - [4.1 Duyệt kho đề](#41-duyệt-kho-đề)
  - [4.2 Mở 1 đề](#42-mở-1-đề)
  - [4.3 Run (chạy thử sample cases)](#43-run-chạy-thử-sample-cases)
  - [4.4 Submit (chấm full testcases)](#44-submit-chấm-full-testcases)
  - [4.5 Lịch sử & stats](#45-lịch-sử--stats)
- [5. API specification](#5-api-specification)
- [6. Kafka topics & contract](#6-kafka-topics--contract)
- [7. Database schema](#7-database-schema)
- [8. Thay đổi ở service khác](#8-thay-đổi-ở-service-khác)
- [9. Security model](#9-security-model)
- [10. Configuration](#10-configuration)
- [11. Error codes](#11-error-codes)
- [12. Failure modes & retry](#12-failure-modes--retry)
- [13. Vận hành & deploy](#13-vận-hành--deploy)
- [14. Kế hoạch triển khai (phases)](#14-kế-hoạch-triển-khai-phases)
- [15. Future work](#15-future-work)

---

## 1. Phạm vi & nguyên tắc

**Service làm:**
- Cung cấp **facade kho đề** hướng user: list/filter đề CODING (difficulty, tags, search, trạng thái solved của tôi), xem chi tiết 1 đề (description, constraints, complexity, `functionMeta`, `starterCode`, **sample testcases** không-hidden).
- Điều phối **Run / Submit**: dựng `code-submission` (gắn `origin=PRACTICE`), gửi judge, nhận `submission-judged`, persist kết quả + per-case.
- Lưu **lịch sử submission** theo user.
- Duy trì **trạng thái theo (user, đề)**: `ATTEMPTED` / `SOLVED`.
- Tính **thống kê tổng hợp**: số bài đã giải (tổng + theo difficulty), acceptance rate, streak ngày.

**Service KHÔNG làm:**
- KHÔNG chạy code user — đó là `judge-service`.
- KHÔNG quản lý đề gốc / testcases gốc — đó là `question-bank-service` (practice chỉ đọc).
- KHÔNG chấm bằng AI / sinh feedback ngôn ngữ tự nhiên — đó là đặc thù của interview, **không** áp dụng cho practice.
- KHÔNG đụng tới `interview-service` (session, answer, planner).

**Nguyên tắc:**
- **Read-only đối với kho đề**: practice không bao giờ ghi vào question-bank; mọi đề/testcase lấy qua API.
- **Stateful history, stateless judge**: lịch sử/trạng thái nằm ở DB của service này; judge stateless theo từng submission.
- **Idempotent theo `submissionId`**: verdict có thể tới nhiều lần (Kafka at-least-once) → áp dụng đúng 1 lần.
- **Ownership filter**: practice và interview cùng nghe `submission-judged`; mỗi bên chỉ xử lý submission của mình (lọc theo `origin`).
- **Hidden testcases không bao giờ rời backend qua API user**: chỉ đi từ question-bank → practice (service-to-service) → judge.

---

## 2. Kiến trúc

```
                         ┌──────────────────────────────────────────────┐
                         │                  FRONTEND                     │
                         │  Problem list · Problem detail (workspace) ·  │
                         │  Submissions history · Stats dashboard        │
                         └───────────────┬──────────────────────────────┘
                                         │  /api/v1/practice/**  (JWT user)
                                         ▼
                                 ┌───────────────┐
                                 │  api-gateway  │
                                 └───────┬───────┘
                                         ▼
              ┌──────────────────────────────────────────────────┐
              │               practice-service                    │
              │  ┌────────────┐  ┌──────────────┐  ┌───────────┐  │
              │  │ Catalog    │  │ Submission   │  │ Stats     │  │
              │  │ facade     │  │ orchestrator │  │ service   │  │
              │  └─────┬──────┘  └──────┬───────┘  └─────┬─────┘  │
              │        │                │                │        │
              │   Feign (read)     Kafka producer    practice_db  │
              └────────┼────────────────┼───────────────┼────────┘
                       │                │ code-submission│
        GET problems / │                ▼                │
        full testcases │        ┌───────────────┐        │
                       ▼        │ judge-service │        │
              ┌──────────────┐  │ (Judge0 run)  │        │
              │ question-bank│  └───────┬───────┘        │
              │  -service    │          │ submission-judged
              └──────────────┘          ▼  (origin=PRACTICE)
                                consume + áp verdict ─────┘
```

**Stack**: Spring Boot (giống các service hiện có), package gốc `com.mockwise.practice`. MySQL/Postgres theo chuẩn dự án (xem [§7](#7-database-schema)), Flyway migrations, Kafka, OpenFeign tới question-bank.

---

## 3. Domain model & state machine

### Thực thể chính

| Thực thể | Mô tả |
|---|---|
| `PracticeSubmission` | Một lần Run hoặc Submit của 1 user cho 1 đề. `id` = `submissionId` gửi sang judge. |
| `PracticeSubmissionCase` | Kết quả từng testcase của 1 submission (chỉ lưu cho SUBMIT; RUN lưu inline/transient). |
| `PracticeProblemStatus` | Trạng thái của (user, đề): ATTEMPTED / SOLVED + metric tốt nhất. |
| `PracticeUserStats` *(rollup, tùy chọn)* | Cache thống kê tổng hợp; bản đầu có thể tính on-read. |

### State machine của `PracticeSubmission`

```
PENDING ──(đã publish code-submission)──▶ JUDGING ──(submission-judged)──▶ DONE
   │                                          │
   └──────────── lỗi publish ─────────────────┴──────────────────────────▶ FAILED
```

- `PENDING`: vừa tạo row, chưa publish xong.
- `JUDGING`: đã publish `code-submission`, đang chờ verdict.
- `DONE`: nhận verdict (bất kể ACCEPTED hay WRONG_ANSWER…). `verdict` mang kết quả thật.
- `FAILED`: lỗi hạ tầng (không publish được, judge báo lỗi hệ thống). Khác với WRONG_ANSWER — đó vẫn là `DONE`.

### State machine của `PracticeProblemStatus`

```
(chưa có) ──Submit lần đầu──▶ ATTEMPTED ──Submit ACCEPTED──▶ SOLVED
                                  ▲                              │
                                  └──────── (giữ SOLVED) ────────┘
```

- Chỉ **Submit** mới đổi trạng thái. **Run không bao giờ đổi.**
- Đã `SOLVED` thì không tụt về `ATTEMPTED`; submit lại chỉ cập nhật `best_runtime_ms`, `attempt_count`, `last_attempt_at`.

### Verdict (tái dùng nguyên của judge-service)
`ACCEPTED`, `WRONG_ANSWER`, `TIME_LIMIT_EXCEEDED`, `RUNTIME_ERROR`, `COMPILATION_ERROR`, … (đúng tập verdict judge đang trả). Practice không định nghĩa verdict mới.

---

## 4. Flows

### 4.1 Duyệt kho đề
1. FE gọi `GET /api/v1/practice/problems?difficulty=&tags=&q=&status=&page=`.
2. practice-service gọi question-bank `GET /api/v1/coding-problems` (chỉ đề `ACTIVE`), join với `practice_problem_status` của user hiện tại để gắn cờ `myStatus` (SOLVED/ATTEMPTED/NONE) và sort/filter.
3. Trả danh sách gọn (id, title, difficulty, tags, acceptanceRate, myStatus). **Không** kèm testcases.

### 4.2 Mở 1 đề
1. `GET /api/v1/practice/problems/{id}`.
2. practice-service gọi question-bank `GET /api/v1/coding-problems/{id}` → nhận description/constraints/complexity/`functionMeta`/`starterCode` + **sample (non-hidden) testcases** (có `expectedOutput` + `note` kiểu LeetCode).
3. Trả về FE đúng shape `CodingProblemView` (tái dùng `frontend/src/types/coding.ts`). FE render workspace + nạp `starterCode` theo ngôn ngữ.

### 4.3 Run (chạy thử sample cases)
1. `POST /api/v1/practice/problems/{id}/run` body `{language, code}`.
2. practice-service:
   - Tạo `PracticeSubmission(mode=RUN, status=PENDING)`, `id = submissionId`.
   - Lấy **sample testcases** (non-hidden) từ question-bank (đã có ở bước open, hoặc gọi lại endpoint detail).
   - Publish `code-submission` `{submissionId, origin=PRACTICE, language, code, functionMeta, testCases=sample}`.
   - Set `JUDGING`, trả `{submissionId}` (202).
3. judge chạy → `submission-judged`. Consumer áp kết quả per-case vào submission, set `DONE`.
4. FE poll `GET /api/v1/practice/submissions/{submissionId}` → hiển thị pass/fail từng sample + stdout/stderr.
5. **Không** cập nhật `problem_status`/stats.

> RUN submissions có thể prune định kỳ (TTL) để không phình bảng — xem [§15](#15-future-work).

### 4.4 Submit (chấm full testcases)
1. `POST /api/v1/practice/problems/{id}/submit` body `{language, code}`.
2. practice-service:
   - Tạo `PracticeSubmission(mode=SUBMIT, status=PENDING)`.
   - Gọi **internal** question-bank `GET /internal/coding-problems/{id}/full` (service-to-service, `SERVICE_API_KEY`) → nhận **đủ** testcases gồm hidden.
   - Publish `code-submission` với toàn bộ testcases + `origin=PRACTICE`.
   - Set `JUDGING`, trả `{submissionId}`.
3. judge chạy toàn bộ → `submission-judged` `{submissionId, origin=PRACTICE, verdict, results[]}`.
4. Consumer (idempotent theo submissionId):
   - Lưu `verdict`, `passed_cases/total_cases`, `runtime_ms`, `memory_kb`, per-case (ẩn stdout/stderr của hidden case khỏi API user).
   - Set submission `DONE`.
   - **Upsert `practice_problem_status`**: lần đầu → `ATTEMPTED`; nếu `verdict=ACCEPTED` → `SOLVED` (set `first_solved_at` nếu chưa có), cập nhật `best_runtime_ms`, `attempt_count++`, `last_attempt_at`.
   - Cập nhật/invalid hóa rollup stats.
5. FE poll submission → hiển thị verdict + số case pass (per-case của hidden chỉ hiện status, không lộ I/O).

### 4.5 Lịch sử & stats
- `GET /api/v1/practice/submissions?problemId=&mode=&verdict=&page=` → lịch sử của tôi (mới nhất trước).
- `GET /api/v1/practice/submissions/{id}` → chi tiết 1 submission (source code, per-case).
- `GET /api/v1/practice/stats` → `{ solvedTotal, solvedByDifficulty{EASY,MEDIUM,HARD}, attemptedTotal, acceptanceRate, currentStreakDays, longestStreakDays }`.

---

## 5. API specification

> Tất cả dưới prefix `/api/v1/practice`, yêu cầu JWT user. `userId` lấy từ token, **không** nhận từ body/param.

| Method & path | Mô tả | Body / Query | Response |
|---|---|---|---|
| `GET /problems` | List đề + filter | `difficulty, tags, q, status, page, size` | `Page<ProblemSummary>` |
| `GET /problems/{id}` | Chi tiết đề (sample cases) | — | `CodingProblemView` |
| `POST /problems/{id}/run` | Chạy thử sample | `{language, code}` | `202 {submissionId}` |
| `POST /problems/{id}/submit` | Chấm full | `{language, code}` | `202 {submissionId}` |
| `GET /submissions` | Lịch sử của tôi | `problemId, mode, verdict, page, size` | `Page<SubmissionSummary>` |
| `GET /submissions/{id}` | Chi tiết submission | — | `SubmissionDetail` (per-case) |
| `GET /stats` | Thống kê của tôi | — | `UserStats` |

**`ProblemSummary`**: `{ id, title, difficulty, tags[], acceptanceRate, myStatus }`
**`SubmissionSummary`**: `{ id, problemId, problemTitle, language, mode, status, verdict, passedCases, totalCases, runtimeMs, createdAt }`
**`SubmissionDetail`**: `SubmissionSummary` + `{ sourceCode, cases:[{orderIndex, status, runtimeMs, memoryKb, stdout?, stderr?}] }` *(stdout/stderr chỉ cho sample case)*
**`UserStats`**: `{ solvedTotal, solvedByDifficulty, attemptedTotal, acceptanceRate, currentStreakDays, longestStreakDays }`

Giới hạn input: `code ≤ 256KB` (đồng bộ với interview `SubmitAnswerInput`), `language ≤ 20 ký tự`, whitelist ngôn ngữ theo những gì judge hỗ trợ.

---

## 6. Kafka topics & contract

| Topic | Hướng | Ghi chú |
|---|---|---|
| `code-submission` | **produce** | Tái dùng nguyên contract `SubmissionEvent` của judge + thêm `origin`. |
| `submission-judged` | **consume** | Group-id riêng `practice-service`. Lọc theo `origin=PRACTICE`. |

### `code-submission` payload (đã thêm `origin`)
```json
{
  "submissionId": "uuid",
  "origin": "PRACTICE",
  "language": "python",
  "code": "....",
  "functionMeta": { "fn": "...", "params": [...], "return": "...", "orderMatters": true, "inPlace": false },
  "testCases": [ { "id": "...", "inputData": {...}, "expectedOutput": {...}, "is_hidden": true } ]
}
```

### `submission-judged` payload (judge echo `origin`)
```json
{
  "submissionId": "uuid",
  "origin": "PRACTICE",
  "verdict": "ACCEPTED",
  "results": [ { "testCaseId": "...", "status": "ACCEPTED", "stdout": "...", "stderr": "...", "runtimeMs": 12, "memoryKb": 3400 } ]
}
```

**Vì sao cần `origin`:** `submission-judged` hiện chỉ interview-service nghe. Khi practice cũng nghe (group khác), cả hai nhận **mọi** verdict. `origin` cho mỗi consumer fast-filter, khỏi phải query DB để biết "có phải của tôi không".
**Phương án dự phòng (nếu không sửa judge):** mỗi consumer lookup `submissionId` trong DB của mình, không thấy thì bỏ qua — đúng vì `submissionId` là UUID nên không đụng. `origin` chỉ là tối ưu, không phải bắt buộc về mặt đúng/sai.

---

## 7. Database schema

> DB riêng `practice_db` (hoặc schema riêng), Flyway `V1__init.sql`. Kiểu cột theo chuẩn dự án (CHAR(36) cho UUID, JSON cho blob — giống judge/question-bank).

```sql
CREATE TABLE practice_submission (
    id            CHAR(36)    NOT NULL,          -- = submissionId
    user_id       CHAR(36)    NOT NULL,
    question_id   VARCHAR(64) NOT NULL,          -- id đề bên question-bank
    language      VARCHAR(20) NOT NULL,
    source_code   MEDIUMTEXT  NOT NULL,
    mode          VARCHAR(10) NOT NULL,          -- RUN | SUBMIT
    status        VARCHAR(10) NOT NULL,          -- PENDING|JUDGING|DONE|FAILED
    verdict       VARCHAR(30),                   -- ACCEPTED|WRONG_ANSWER|...
    passed_cases  INT         NOT NULL DEFAULT 0,
    total_cases   INT         NOT NULL DEFAULT 0,
    runtime_ms    INT,
    memory_kb     INT,
    created_at    DATETIME    NOT NULL,
    finished_at   DATETIME,
    PRIMARY KEY (id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_user_question (user_id, question_id)
);

CREATE TABLE practice_submission_case (
    id            CHAR(36)    NOT NULL,
    submission_id CHAR(36)    NOT NULL,
    order_index   INT         NOT NULL,
    test_case_id  VARCHAR(64),
    status        VARCHAR(30) NOT NULL,
    runtime_ms    INT,
    memory_kb     INT,
    stdout        TEXT,                          -- chỉ điền cho sample case
    stderr        TEXT,
    PRIMARY KEY (id),
    KEY idx_submission (submission_id),
    CONSTRAINT fk_case_submission FOREIGN KEY (submission_id)
        REFERENCES practice_submission (id) ON DELETE CASCADE
);

CREATE TABLE practice_problem_status (
    user_id         CHAR(36)    NOT NULL,
    question_id     VARCHAR(64) NOT NULL,
    status          VARCHAR(10) NOT NULL,        -- ATTEMPTED | SOLVED
    attempt_count   INT         NOT NULL DEFAULT 0,
    best_runtime_ms INT,
    first_solved_at DATETIME,
    last_attempt_at DATETIME    NOT NULL,
    PRIMARY KEY (user_id, question_id)
);

-- Tùy chọn (rollup để tránh tính lại mỗi lần). Bản đầu có thể bỏ, tính on-read.
CREATE TABLE practice_user_stats (
    user_id            CHAR(36) NOT NULL,
    solved_total       INT      NOT NULL DEFAULT 0,
    solved_easy        INT      NOT NULL DEFAULT 0,
    solved_medium      INT      NOT NULL DEFAULT 0,
    solved_hard        INT      NOT NULL DEFAULT 0,
    submit_total       INT      NOT NULL DEFAULT 0,
    submit_accepted    INT      NOT NULL DEFAULT 0,
    current_streak     INT      NOT NULL DEFAULT 0,
    longest_streak     INT      NOT NULL DEFAULT 0,
    last_solved_date   DATE,
    PRIMARY KEY (user_id)
);
```

**Acceptance rate**: theo user = `submit_accepted / submit_total`. (Acceptance rate *global* của đề thì lấy từ question-bank hoặc tính sau — xem future work.)
**Streak**: dựa trên các ngày có ít nhất 1 submit `ACCEPTED`; cập nhật khi verdict ACCEPTED tới.

---

## 8. Thay đổi ở service khác

### judge-service (tối thiểu)
- Thêm field `String origin` (nullable) vào `SubmissionEvent` → lưu cột `origin` trong `judge_jobs` → echo vào `JudgeResultEvent`.
- Tác động: 1 cột DB (Flyway), 2 DTO field, 1 dòng map. Interview giữ nguyên (origin null ⇒ coi như INTERVIEW, hoặc interview cũng set `origin=INTERVIEW`).

### question-bank-service (thêm read endpoints)
| Endpoint | Auth | Trả về |
|---|---|---|
| `GET /api/v1/coding-problems?difficulty=&tags=&q=&page=` | user (qua gateway) | List đề `ACTIVE`, **không** testcases |
| `GET /api/v1/coding-problems/{id}` | user | Detail + **sample** (non-hidden) testcases |
| `GET /internal/coding-problems/{id}/full` | service-to-service (`SERVICE_API_KEY`) | Detail + **đủ** testcases gồm hidden |

> Logic strip hidden testcase đã có sẵn pattern ở `CodingProblemView.fromSessionQuestion` (interview) — tái dùng ý tưởng.

### api-gateway
- Route `/api/v1/practice/**` → practice-service (JWT user, giống các route hiện có).

### frontend
- Trang **Problem list** (filter difficulty/tags/status).
- Trang **Problem detail** = tái dùng coding workspace + `types/coding.ts`, thêm nút Run/Submit gọi API practice.
- Tab **Submissions** (lịch sử + chi tiết per-case).
- Trang **Stats** (solved/difficulty/acceptance/streak).

### Hạ tầng
- Thêm module `practice-service` vào `pom.xml` cha.
- Thêm service vào `docker/docker-compose.prod.yml` (mạng internal, env `SERVICE_API_KEY`, Kafka, DB) theo đúng pattern service hiện có.
- Biến môi trường vào `.env.example`.

---

## 9. Security model

- Mọi endpoint `/api/v1/practice/**` cần **JWT user hợp lệ**; `userId` lấy từ token (không tin body).
- Endpoint `/internal/coding-problems/{id}/full` ở question-bank chỉ chấp nhận **service-to-service** qua `SERVICE_API_KEY` (header), **không** expose qua gateway cho user — vì nó trả hidden testcases.
- API user **không bao giờ** trả `expectedOutput`/`stdout`/`stderr` của hidden testcases. Per-case của hidden chỉ lộ `status` + `runtimeMs`.
- User chỉ xem được submission của chính mình (filter cứng theo `userId` từ token).
- Đề ở trạng thái `DRAFT`/`ARCHIVED` không xuất hiện trong catalog practice (chỉ `ACTIVE`).

---

## 10. Configuration

| Biến | Mô tả |
|---|---|
| `SPRING_DATASOURCE_*` | Kết nối `practice_db` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Broker |
| `practice.kafka.topic.code-submission` | mặc định `code-submission` |
| `practice.kafka.topic.submission-judged` | mặc định `submission-judged` |
| `spring.kafka.consumer.group-id` | `practice-service` |
| `practice.questionbank.base-url` | Feign tới question-bank |
| `SERVICE_API_KEY` | Gọi internal endpoint question-bank |
| `practice.run.max-code-bytes` | mặc định `262144` (256KB) |

---

## 11. Error codes

| Code | HTTP | Khi nào |
|---|---|---|
| `PROBLEM_NOT_FOUND` | 404 | Đề không tồn tại hoặc không `ACTIVE` |
| `SUBMISSION_NOT_FOUND` | 404 | submissionId không thuộc user |
| `LANGUAGE_NOT_SUPPORTED` | 400 | Ngôn ngữ ngoài whitelist judge |
| `CODE_TOO_LARGE` | 400 | `code` vượt 256KB |
| `INVALID_PAYLOAD` | 400 | Thiếu `code`/`language` |
| `QUESTION_BANK_UNAVAILABLE` | 502 | Feign tới question-bank lỗi |
| `JUDGE_DISPATCH_FAILED` | 502 | Publish `code-submission` thất bại |

---

## 12. Failure modes & retry

- **Publish `code-submission` lỗi** → submission `FAILED`, trả `JUDGE_DISPATCH_FAILED`; user bấm lại tạo submission mới (không tự retry ngầm để tránh nhân đôi).
- **Verdict không bao giờ tới** (judge chết): submission kẹt `JUDGING`. Có watchdog đánh dấu `FAILED` sau timeout (vd 60s) — tham khảo cơ chế resilience judge ([[project_testcase_uuid_fix]] DLQ). Bản đầu có thể để job quét đơn giản.
- **Verdict tới nhiều lần** (at-least-once): consumer idempotent — nếu submission đã `DONE` thì bỏ qua (giống interview dedup theo submissionId).
- **question-bank down lúc Submit** → trả `QUESTION_BANK_UNAVAILABLE`, không tạo submission rác.
- **Cross-talk verdict**: nếu thiếu `origin`, fallback lookup-by-id đảm bảo không xử lý nhầm submission của interview.

---

## 13. Vận hành & deploy

- Theo layout `/opt/mockwise` trên VPS ([[project_vps_deploy_ops]]): nhớ `--env-file` khi recreate, và **restart api-gateway** sau khi tạo container mới để tránh stale-IP 502 ([[project_gateway_stale_ip_outage]]).
- Khi deploy đồng thời judge-service (thêm `origin`) và practice-service: deploy judge trước (backward-compatible vì `origin` nullable), rồi question-bank (endpoints mới), rồi practice-service, cuối cùng mở route gateway + FE.
- Grep file thực thi trước deploy để tránh lỗi rò `</content>` ([[feedback_write_tool_content_tag_leak]]).
- Log/disk: practice sinh nhiều RUN submission → bật prune định kỳ; chú ý dung lượng (bài học [[project_prod_disk_full_judge_logs]]).

---

## 14. Kế hoạch triển khai (phases)

**Phase 0 — Catalog facade (nền tảng).**
- question-bank: 2 endpoint user (`list`, `detail`) + 1 internal (`full`).
- practice-service skeleton + Feign client + `GET /problems`, `GET /problems/{id}`.
- FE: trang Problem list + mở detail (chưa Run/Submit).

**Phase 1 — Run.**
- judge: thêm `origin`.
- practice: producer `code-submission`, consumer `submission-judged`, `POST /run`, `GET /submissions/{id}`.
- FE: nút Run + hiển thị sample pass/fail.

**Phase 2 — Submit + history.**
- practice: `POST /submit` (lấy full testcases), upsert `problem_status`, `GET /submissions`.
- FE: nút Submit + tab Submissions.

**Phase 3 — Stats.**
- practice: rollup/agg `GET /stats` (solved/difficulty/acceptance/streak).
- FE: trang Stats; gắn `myStatus` vào Problem list.

**Phase 4 — Hoàn thiện vận hành.**
- Watchdog timeout `JUDGING`, prune RUN, docker-compose + gateway route + `.env.example`.

---

## 15. Future work

- **Acceptance rate global theo đề** (đồng bộ ngược về question-bank hoặc bảng đếm riêng).
- **Prune RUN submissions** theo TTL để giữ bảng gọn.
- **Bộ lọc "Companies"/"Topics"** nâng cao, đề xuất bài kế tiếp (recommended next).
- **Bảng xếp hạng / hồ sơ công khai** (đối chiếu chính sách quyền riêng tư).
- **Đa lời giải, đo phân vị runtime** so với người dùng khác (cần lưu phân phối runtime).
- **Phân biệt RUN với custom input** do user tự nhập (ngoài sample cases).
- **Chia sẻ điểm "đã giải"** sang hồ sơ user-profile-service nếu cần hiển thị chéo.
