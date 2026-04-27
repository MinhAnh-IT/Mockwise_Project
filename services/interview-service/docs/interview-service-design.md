# Interview Service — Design Document

Service đóng vai trò **orchestrator** cho toàn bộ vòng đời 1 buổi phỏng vấn: từ lúc user bắt đầu session, lấy danh sách câu hỏi, nộp câu trả lời, cho đến khi nhận điểm và phản hồi từ AI.

Đây là service domain trung tâm — *không* lưu video / audio / transcript / score trực tiếp, mà điều phối các service chuyên trách (`storage`, `tts-stt`, `judge`, `AI-Evaluation`) và quản lý **state machine của session + answer**.

---

## Mục lục

- [1. Phạm vi & nguyên tắc](#1-phạm-vi--nguyên-tắc)
- [2. Kiến trúc](#2-kiến-trúc)
- [3. Domain model & state machine](#3-domain-model--state-machine)
- [4. Flows](#4-flows)
  - [4.1 Bắt đầu session — lấy câu hỏi](#41-bắt-đầu-session--lấy-câu-hỏi)
  - [4.2 Nộp câu trả lời (behavioral / core)](#42-nộp-câu-trả-lời-behavioral--core)
  - [4.3 Nộp câu trả lời (coding)](#43-nộp-câu-trả-lời-coding)
  - [4.4 Kết thúc session — tổng hợp điểm](#44-kết-thúc-session--tổng-hợp-điểm)
- [5. API specification](#5-api-specification)
- [6. Kafka topics](#6-kafka-topics)
- [7. Database schema](#7-database-schema)
- [8. Security model](#8-security-model)
- [9. Configuration](#9-configuration)
- [10. Error codes](#10-error-codes)
- [11. Failure modes & retry strategy](#11-failure-modes--retry-strategy)
- [12. Vận hành](#12-vận-hành)
- [13. Future work](#13-future-work)

---

## 1. Phạm vi & nguyên tắc

**Service làm:**
- Khởi tạo / quản lý vòng đời session: `CREATED → IN_PROGRESS → COMPLETED | CANCELLED`.
- Lấy bộ câu hỏi từ `question-bank-service` theo cấu hình session (level, topic, mix behavioral/core/coding).
- Nhận `storageObjectId` từ FE sau khi user upload video xong, validate ACL, tạo `Answer` record.
- Phát event `answer-submitted` để `tts-stt-service` chuyển video → transcript.
- Nhận `transcript-ready`, áp dụng business rules, assemble context (question + rubric + transcript), phát `evaluation-requested`.
- Nhận `evaluation-completed` từ `AI-Evaluation`, persist score.
- Đối với câu coding: forward submission tới `judge-service`, nhận `submission-judged`, persist verdict.
- Tổng hợp điểm cuối session, hiển thị feedback cho user.

**Service KHÔNG làm:**
- KHÔNG xử lý media (download video, ffmpeg, STT) — đó là `tts-stt-service`.
- KHÔNG chấm điểm (LLM call, scoring logic) — đó là `AI-Evaluation`.
- KHÔNG chạy code user — đó là `judge-service`.
- KHÔNG lưu bytes — bytes ở MinIO qua `storage-service`.
- KHÔNG quản lý câu hỏi gốc / rubric — đó là `question-bank-service`.

**Nguyên tắc:**
- **Single source of truth cho workflow state**: mọi quyết định "bước tiếp theo là gì" chỉ ở service này. Các worker (tts-stt, AI-Evaluation, judge) không tự quyết định nhau gọi nhau.
- **Control plane, không phải data plane**: chỉ truyền pointer (`storageObjectId`, `transcriptId`), không truyền bytes.
- **Stateful workflow, stateless workers**: session state lưu trong DB của service này; các worker stateless theo từng request.
- **Idempotent**: mọi event đều có `eventId`, mọi API có thể retry mà không sinh dữ liệu trùng.

---

## 2. Kiến trúc

```
                  ┌──────────────┐
                  │   Browser    │
                  │   (FE/SPA)   │
                  └──────┬───────┘
                         │ JWT
                         ▼
                  ┌──────────────┐
                  │ api-gateway  │
                  │   (8888)     │
                  └──────┬───────┘
                         │
                         ▼
                  ┌─────────────────────────────────┐
                  │     interview-service           │
                  │           (8088)                │
                  │                                 │   PostgreSQL: interview
                  │  ┌─────────────────────────┐    │   ─ interview_session
                  │  │  Session orchestrator   │    │   ─ session_question
                  │  │  Answer state machine   │    │   ─ answer
                  │  │  ACL & ownership        │    │   ─ outbox_event
                  │  └─────────────────────────┘    │
                  └────┬───┬───┬───┬───────┬────────┘
                       │   │   │   │       │
       ┌───────────────┘   │   │   │       └─────────── Kafka ───►
       │                   │   │   │                  topics:
       ▼                   ▼   ▼   ▼                  ─ answer-submitted          (produce)
┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ─ transcript-ready          (consume)
│  question-   │  │   storage-   │  │    iam +     │  ─ transcript-failed         (consume)
│  bank        │  │   service    │  │ user-profile │  ─ evaluation-requested      (produce)
│  (8084)      │  │   (8085)     │  │ (8081, 8082) │  ─ evaluation-completed      (consume)
└──────────────┘  └──────────────┘  └──────────────┘  ─ code-submission           (produce)
                                                       ─ submission-judged         (consume)

       (interview-service is the only service that sees these domain events.
        Workers don't subscribe to each other's events — they only subscribe
        to events addressed to them by the orchestrator.)
```

### Vị trí trong stack

| Thuộc tính | Giá trị |
|---|---|
| Service name | `interview-service` |
| Port | `8088` |
| DB | PostgreSQL, database `interview` |
| Path prefix | `/api/v1/interviews` |
| Tech | Spring Boot 3.x, Java 21, Spring Kafka, Flyway, Lombok |
| Image | `ghcr.io/<owner>/mockwise_project/interview-service` |

### Endpoint layers

| Lớp | Path prefix | Auth | Caller |
|---|---|---|---|
| **Public** | `/api/v1/interviews/**` | JWT (Bearer) | Browser qua gateway |
| **Internal** | `/api/v1/interviews/internal/**` | `X-Internal-Auth` | Các service khác (admin tooling, replay) |
| **Health** | `/api/v1/interviews/health` | none | infra |

---

## 3. Domain model & state machine

### Session (1 buổi phỏng vấn)

```
        ┌──────────┐
        │ CREATED  │   user gọi /interviews/start
        └────┬─────┘
             │ first answer submitted (hoặc explicit /resume)
             ▼
       ┌──────────────┐
       │ IN_PROGRESS  │   user đang trả lời
       └────┬────┬────┘
            │    │
   user end │    │ inactivity timeout (30 phút)
   /finish  │    │ hoặc admin force-end
            ▼    ▼
       ┌──────────────┐         ┌──────────────┐
       │  COMPLETED   │         │  CANCELLED   │
       └──────────────┘         └──────────────┘
              │
              │ all answers scored
              ▼
       ┌──────────────┐
       │   SCORED     │   final report ready
       └──────────────┘
```

`SCORED` là sub-state của `COMPLETED` — session đã end và mọi answer đã có verdict/score. Phân biệt giúp FE biết khi nào hiển thị "đang chấm" vs "có report".

### Answer (1 câu trả lời)

```
       ┌──────────┐
       │ SUBMITTED│   FE gọi /answers, đã có storageObjectId
       └────┬─────┘
            │ behavioral/core: publish answer-submitted
            │ coding: publish code-submission
            ▼
       ┌─────────────┐
       │ PROCESSING  │   tts-stt hoặc judge đang xử lý
       └────┬────┬───┘
            │    │
            │    │ failed (DLQ, timeout)
            ▼    ▼
       ┌──────────┐    ┌──────────┐
       │ READY    │    │ FAILED   │   có thể retry thủ công
       └────┬─────┘    └──────────┘
            │ transcript ready / judge result ready
            │ → trigger evaluation
            ▼
       ┌──────────────┐
       │ EVALUATING   │   AI đang chấm
       └────┬─────────┘
            │
            ▼
       ┌──────────┐
       │ SCORED   │   AI trả về score + feedback
       └──────────┘
```

Mỗi transition đều ghi vào `answer_event_log` (audit + debug). Transition không hợp lệ (ví dụ: `SCORED → PROCESSING`) → reject + alert.

### Tại sao tách Session và Answer state?

- Session state: thuộc về user experience (đang trả lời / đã xong).
- Answer state: thuộc về processing pipeline (đang STT / đã chấm).
- Một session có thể `COMPLETED` (user đã end) trong khi một số answer còn `PROCESSING` (background đang chạy). FE có thể hiển thị "đang chấm câu 3..." mà không khoá session.

---

## 4. Flows

### 4.1 Bắt đầu session — lấy câu hỏi

```
FE                 interview-service        question-bank          user-profile
 │  POST /interviews/start │                       │                     │
 │  { topic, level,        │                       │                     │
 │    questionCount }      │                       │                     │
 │────────────────────────►│                       │                     │
 │                         │ verify user (JWT)     │                     │
 │                         │                       │                     │
 │                         │ GET /users/{id}       │                     │
 │                         │ ─────────────────────────────────────────►  │
 │                         │ ◄── { profile, prefs } ──────────────────── │
 │                         │                       │                     │
 │                         │ POST /questions/sample│                     │
 │                         │ { topic, level,       │                     │
 │                         │   count, mix }        │                     │
 │                         │ ─────────────────────►│                     │
 │                         │ ◄── question[] ───────│                     │
 │                         │                       │                     │
 │                         │ insert session(CREATED)                     │
 │                         │ insert session_question[] (snapshot ids)    │
 │                         │                       │                     │
 │ ◄─── { sessionId,       │                       │                     │
 │       questions[] } ────│                       │                     │
 │                                                                       │
 │  (FE hiển thị câu hỏi đầu tiên, user bắt đầu trả lời)                 │
```

**Tại sao snapshot question ids vào `session_question`?**
Admin có thể delete/edit câu hỏi sau khi session đã bắt đầu. Snapshot đảm bảo session vẫn replay được. Câu hỏi gốc giữ ở `question-bank` nhưng `interview-service` lưu **id reference + version pin**.

### 4.2 Nộp câu trả lời (behavioral / core)

```
FE              storage-service       interview-service          tts-stt        AI-Eval
 │  POST /storage/uploads/videos                                                  │
 │  (sessionId, contentType, size)                                                │
 │  ──────────────────►│                                                          │
 │  ◄── presigned URL ─│                                                          │
 │                                                                                │
 │  PUT video to MinIO (browser → MinIO direct, KHÔNG qua interview-service)      │
 │                                                                                │
 │  POST /storage/uploads/videos/{id}/complete                                    │
 │  ──────────────────►│                                                          │
 │  ◄── { objectId, READY }                                                       │
 │                                                                                │
 │  POST /interviews/{sid}/questions/{qid}/answers          │                     │
 │  { storageObjectId }                                     │                     │
 │ ────────────────────────────────────────────────────────►│                     │
 │                          ┌── verify:                     │                     │
 │                          │   - JWT.sub == session.userId │                     │
 │                          │   - session.status IN_PROGRESS│                     │
 │                          │   - qid ∈ session_question    │                     │
 │                          │   - storageObjectId chưa dùng │                     │
 │                          │     cho answer khác           │                     │
 │                          │   - storage object owner ==   │                     │
 │                          │     session.userId            │                     │
 │                          └── (call /storage/internal      │                     │
 │                              cho check cuối)              │                     │
 │                                                          │                     │
 │                          insert answer (SUBMITTED)       │                     │
 │                          insert outbox_event             │                     │
 │                          flip → PROCESSING               │                     │
 │ ◄──── 202 { answerId } ──│                               │                     │
 │                                                          │                     │
 │                          ─── publish "answer-submitted"  │                     │
 │                                  via outbox              │                     │
 │                                                          │ ─── consume         │
 │                                                          │ pull video, STT     │
 │                                                          │ insert transcript   │
 │                                                          │ ─── publish         │
 │                                                          │   "transcript-ready"│
 │                          consume                         │                     │
 │                          ─────────────────────────────── │                     │
 │                          GET /tts-stt/internal/transcripts/{id}                │
 │                          ───────────────────────────────►│                     │
 │                          ◄── { text, words, lang } ──────│                     │
 │                          flip answer → READY                                   │
 │                                                                                │
 │                          apply business rules:                                 │
 │                            - transcript đủ dài?                                │
 │                            - session còn ACTIVE?                               │
 │                            - đã có answer khác cho qid? (newest wins)          │
 │                                                                                │
 │                          assemble eval payload:                                │
 │                            { transcript, question (full text + criteria),      │
 │                              rubric, sessionMeta }                             │
 │                                                                                │
 │                          flip answer → EVALUATING                              │
 │                          publish "evaluation-requested" ─────────────────────► │
 │                                                                                │ run LLM
 │                                                                                │ score+feedback
 │                          consume "evaluation-completed" ◄──────────────────────│
 │                          flip answer → SCORED                                  │
 │                          persist score, feedback                               │
 │                                                                                │
 │  GET /interviews/{sid}/answers/{aid}    (poll)           │                     │
 │  hoặc SSE /interviews/{sid}/stream                       │                     │
 │ ────────────────────────────────────────────────────────►│                     │
 │ ◄── { status: SCORED, score, feedback } ─────────────────│                     │
```

**Vì sao FE gọi `interview-service` để confirm answer chứ không phải `storage-service` publish event?**

`storage-service` là infrastructure — nó chỉ biết "object đã upload xong", không biết object đó là *câu trả lời cho câu hỏi nào trong session nào*. Nếu `storage` publish `interview-video-uploaded`, `tts-stt` không biết nên gọi STT cho video này hay không (có thể là test upload, có thể là video bị abort).

`interview-service` là người duy nhất có ngữ cảnh đầy đủ → nó là người duy nhất quyết định "video này là answer thật, hãy STT".

### 4.3 Nộp câu trả lời (coding)

```
FE                interview-service          judge-service              AI-Eval (optional)
 │ POST /interviews/{sid}/questions/{qid}/answers                                  │
 │ { code, language }                                                              │
 │ ──────────────────────────►│                                                    │
 │                            │ verify (giống 4.2)                                 │
 │                            │ insert answer (SUBMITTED)                          │
 │                            │ flip → PROCESSING                                  │
 │ ◄── 202 { answerId } ──────│                                                    │
 │                            │                                                    │
 │                            │ publish "code-submission"                          │
 │                            │   { submissionId=answerId, code, language,         │
 │                            │     functionMeta, testCases }                      │
 │                            │ ──────────────────────────► consume                │
 │                            │                              run Judge0            │
 │                            │                              evaluate test cases   │
 │                            │ consume "submission-judged"                        │
 │                            │ ◄────────────────────────── publish                │
 │                            │ { verdicts[], totalPassed }                        │
 │                            │                                                    │
 │                            │ flip answer → READY                                │
 │                            │                                                    │
 │                            │ (optional) trigger AI eval cho code quality        │
 │                            │ publish "evaluation-requested"                     │
 │                            │ ────────────────────────────────────────────────► │
 │                            │ ...                                                │
```

`judge-service` đã có sẵn pattern Kafka `code-submission` / `submission-judged` (xem `judge-service/src/main/java/.../kafka/`). `interview-service` là caller mới — thay thế cho bất kỳ caller cũ nào.

### 4.4 Kết thúc session — tổng hợp điểm

```
FE                interview-service
 │ POST /interviews/{sid}/finish
 │ ──────────────►│
 │                │ flip session → COMPLETED
 │                │ stop accepting new answers
 │                │
 │                │ check: tất cả answer đã SCORED?
 │                │   ├─ yes → flip → SCORED, build report
 │                │   └─ no  → giữ COMPLETED, background tiếp tục
 │                │
 │ ◄── { status }─│
 │
 │ (background) khi answer cuối cùng SCORED:
 │                │ flip session → SCORED
 │                │ build aggregated report (avg score per category)
 │                │ publish "interview-scored" (cho mail-service gửi report)
```

---

## 5. API specification

### 5.1 Public endpoints (qua gateway, JWT)

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/interviews/start` | Tạo session mới, trả question list |
| `GET` | `/interviews/{sid}` | Lấy session detail + answers |
| `POST` | `/interviews/{sid}/questions/{qid}/answers` | Nộp câu trả lời (video hoặc code) |
| `GET` | `/interviews/{sid}/answers/{aid}` | Lấy 1 answer + status + score (nếu có) |
| `POST` | `/interviews/{sid}/finish` | End session |
| `GET` | `/interviews/{sid}/report` | Lấy report cuối (chỉ khi `SCORED`) |
| `GET` | `/interviews/{sid}/stream` | SSE stream cho status updates |

### 5.2 Internal endpoints (`X-Internal-Auth`)

| Method | Path | Caller | Mô tả |
|---|---|---|---|
| `POST` | `/interviews/internal/replay/{aid}` | admin tooling | Re-trigger STT + AI eval cho 1 answer |
| `GET` | `/interviews/internal/answers/{aid}` | AI-Eval | Lấy full context khi nhận `evaluation-requested` (alternative cho việc embed trong event) |

### 5.3 Health

`GET /interviews/health` — public, kiểm tra DB + Kafka + dependent services.

### 5.4 Submit answer schema

```json
// POST /interviews/{sid}/questions/{qid}/answers
// Behavioral / Core
{
  "type": "VIDEO",
  "storageObjectId": "0a1b2c3d-..."
}

// Coding
{
  "type": "CODE",
  "language": "java",
  "code": "class Solution { ... }"
}
```

Response:
```json
{
  "code": 1000,
  "data": {
    "answerId": "...",
    "status": "PROCESSING",
    "submittedAt": "2026-04-26T..."
  }
}
```

### 5.5 Get answer schema

```json
{
  "code": 1000,
  "data": {
    "answerId": "...",
    "questionId": "...",
    "type": "VIDEO",
    "status": "SCORED",
    "transcriptId": "...",       // null nếu chưa READY
    "score": 8.5,                // null nếu chưa SCORED
    "feedback": "...",
    "submittedAt": "...",
    "scoredAt": "..."
  }
}
```

---

## 6. Kafka topics

### Produce: `answer-submitted`

Phát khi answer behavioral/core được persist với `status=SUBMITTED`. Thay thế cho `interview-video-uploaded` mà `storage-service` từng đề xuất publish.

```json
{
  "eventId": "uuid",
  "eventType": "ANSWER_SUBMITTED",
  "occurredAt": "...",
  "answerId": "...",
  "sessionId": "...",
  "questionId": "...",
  "ownerUserId": "...",
  "storageObjectId": "...",
  "videoMeta": {
    "bucket": "interview-videos",
    "objectKey": "u-7f3a/sess-.../...",
    "contentType": "video/webm",
    "sizeBytes": 12582912
  },
  "languageHint": "en"
}
```

**Consumer**: `tts-stt-service` (xem [tts-stt-service-design.md](../../tts-stt-service/docs/tts-stt-service-design.md)).

### Consume: `transcript-ready` / `transcript-failed`

Producer: `tts-stt-service`. Consumer group: `interview-service`.

Khi nhận `transcript-ready`:
1. Find answer by `storageObjectId` (or via `sttJobId` mapping nếu lưu).
2. Flip `answer.status = READY`.
3. Trigger evaluation (xem dưới).

Khi nhận `transcript-failed`:
1. Flip `answer.status = FAILED`, lưu `errorCode`.
2. Notify FE qua SSE / poll.
3. Cho phép admin retry qua `/internal/replay/{aid}`.

### Produce: `evaluation-requested`

Phát khi answer ở trạng thái `READY` và đã pass business rules.

```json
{
  "eventId": "uuid",
  "eventType": "EVALUATION_REQUESTED",
  "occurredAt": "...",
  "answerId": "...",
  "sessionId": "...",
  "questionId": "...",
  "answerType": "VIDEO",
  "context": {
    "questionText": "...",
    "questionType": "behavioral",
    "rubric": { ... },
    "transcript": {
      "text": "...",
      "words": [ ... ],
      "languageCode": "en",
      "durationMs": 92500
    },
    "sessionMeta": {
      "level": "senior",
      "topic": "system-design"
    }
  }
}
```

**Quan trọng**: payload tự đủ — AI-Evaluation không cần gọi ngược lại bất kỳ service nào để lấy context. Trade-off message size, đổi lấy decoupling. Nếu transcript quá dài, fallback: payload chỉ chứa `answerId`, AI-Eval gọi `GET /interviews/internal/answers/{aid}` để lấy full context.

### Consume: `evaluation-completed`

Producer: `AI-Evaluation`. Consumer group: `interview-service`.

```json
{
  "eventId": "uuid",
  "eventType": "EVALUATION_COMPLETED",
  "occurredAt": "...",
  "answerId": "...",
  "score": 8.5,
  "maxScore": 10,
  "rubricScores": { "clarity": 9, "structure": 8, "relevance": 8.5 },
  "feedback": "...",
  "model": "claude-opus-4-7"
}
```

### Produce: `code-submission` / Consume: `submission-judged`

Đã có sẵn topic và schema từ `judge-service` — `interview-service` là caller mới.

### Produce: `interview-scored`

Phát khi session chuyển sang `SCORED`. `mail-service` consume để gửi report.

### Outbox pattern

Mọi produce đều đi qua `outbox_event` table (transactional với business write):

```
@Transactional
public void submitAnswer(...) {
    answerRepo.save(answer);
    outboxRepo.save(new OutboxEvent("answer-submitted", payload));
    // commit cả 2 cùng tx
}

// Background poller (mỗi 1s):
//   SELECT * FROM outbox_event WHERE published=false
//   → kafkaTemplate.send → set published=true
```

Đảm bảo at-least-once mà không mất event nếu Kafka down giữa commit và publish.

---

## 7. Database schema

### `interview_session`

```sql
CREATE TABLE interview_session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         VARCHAR(36)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,    -- CREATED|IN_PROGRESS|COMPLETED|CANCELLED|SCORED
    topic           VARCHAR(100),
    level           VARCHAR(20),              -- junior|mid|senior
    question_count  INTEGER      NOT NULL,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    scored_at       TIMESTAMPTZ,
    final_score     REAL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_session_user ON interview_session(user_id);
CREATE INDEX idx_session_status ON interview_session(status);
```

### `session_question`

Snapshot list câu hỏi của session — cố định khi session start.

```sql
CREATE TABLE session_question (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID         NOT NULL REFERENCES interview_session(id),
    question_id     VARCHAR(36)  NOT NULL,    -- ref tới question-bank
    question_type   VARCHAR(20)  NOT NULL,    -- behavioral|core|coding
    sequence        INTEGER      NOT NULL,    -- thứ tự hiển thị
    snapshot        JSONB        NOT NULL,    -- frozen copy của question (text, audio_key, rubric)
    UNIQUE (session_id, sequence)
);
CREATE INDEX idx_session_question_session ON session_question(session_id);
```

### `answer`

```sql
CREATE TABLE answer (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID         NOT NULL REFERENCES interview_session(id),
    question_id         VARCHAR(36)  NOT NULL,
    type                VARCHAR(10)  NOT NULL,   -- VIDEO|CODE
    status              VARCHAR(20)  NOT NULL,   -- SUBMITTED|PROCESSING|READY|EVALUATING|SCORED|FAILED
    storage_object_id   UUID,                    -- only for VIDEO
    transcript_id       UUID,                    -- only for VIDEO, set khi READY
    code                TEXT,                    -- only for CODE
    language            VARCHAR(20),             -- only for CODE
    score               REAL,
    max_score           REAL,
    rubric_scores       JSONB,
    feedback            TEXT,
    error_code          VARCHAR(40),
    error_message       TEXT,
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    scored_at           TIMESTAMPTZ,
    UNIQUE (session_id, question_id)             -- newest answer per (session, question)
);
CREATE INDEX idx_answer_session ON answer(session_id);
CREATE INDEX idx_answer_status ON answer(status);
CREATE INDEX idx_answer_storage ON answer(storage_object_id);
```

> **Newest-wins policy**: nếu user resubmit (record video lại), service overwrite answer cũ. Nếu cần history → bảng `answer_revision`.

### `answer_event_log`

Append-only audit log cho state transitions.

```sql
CREATE TABLE answer_event_log (
    id          BIGSERIAL PRIMARY KEY,
    answer_id   UUID         NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    reason      VARCHAR(100),
    metadata    JSONB,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_answer_event_answer ON answer_event_log(answer_id);
```

### `outbox_event`

```sql
CREATE TABLE outbox_event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic       VARCHAR(100) NOT NULL,
    aggregate_id UUID,
    event_type  VARCHAR(50)  NOT NULL,
    payload     JSONB        NOT NULL,
    published   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event(published, created_at) WHERE published = FALSE;
```

---

## 8. Security model

### Layer 1 — Gateway

- JWT introspection → inject `X-User-Id`, `X-User-Role`, `X-User-Email`.
- Strip header `X-Internal-Auth` từ public.
- Reject path chứa `/internal/` → 404.

### Layer 2 — Service

- `UserContextFilter`: parse `X-User-*`, seed `ROLE_USER`/`ROLE_ADMIN`.
- `InternalAuthFilter`: match `X-Internal-Auth` với `INTERNAL_API_KEY` → `ROLE_INTERNAL`.
- `SecurityConfig`:
  - `/interviews/start`, `/interviews/{sid}/**` → ROLE_USER (chỉ owner).
  - `/interviews/internal/**` → ROLE_INTERNAL.
  - `/interviews/health` → public.

### Layer 3 — Service logic

- **Ownership check**: mọi access `/interviews/{sid}/**` đều verify `JWT.sub == session.userId` (admin có thể bypass với role check).
- **Storage object verification**: khi nhận `storageObjectId`, gọi `storage-service` `/internal/objects/{id}` để verify:
  - Object thuộc về `session.userId`.
  - Object status = READY.
  - Object kind = INTERVIEW_VIDEO.
  - Chưa được dùng cho answer khác (UNIQUE constraint).
- **Idempotency**: dedupe theo `eventId` ở consumer. Dedupe theo `(sessionId, questionId)` ở submit answer (resubmit thì update, không insert mới).
- **Rate limit**: cap submit rate per user (5 answers/phút) để chống spam upload.

### Layer 4 — Network

- DB + Kafka trong internal-net.
- Service-to-service calls đều dùng `X-Internal-Auth`.

---

## 9. Configuration

```bash
PORT=8088
SPRING_PROFILES_ACTIVE=local|prod

# Database
POSTGRES_HOST=...
POSTGRES_PORT=5432
POSTGRES_DB=interview
POSTGRES_USER=mockwise
POSTGRES_PASSWORD=...

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=interview-service
KAFKA_TOPIC_ANSWER_SUBMITTED=answer-submitted
KAFKA_TOPIC_TRANSCRIPT_READY=transcript-ready
KAFKA_TOPIC_TRANSCRIPT_FAILED=transcript-failed
KAFKA_TOPIC_EVALUATION_REQUESTED=evaluation-requested
KAFKA_TOPIC_EVALUATION_COMPLETED=evaluation-completed
KAFKA_TOPIC_CODE_SUBMISSION=code-submission
KAFKA_TOPIC_SUBMISSION_JUDGED=submission-judged
KAFKA_TOPIC_INTERVIEW_SCORED=interview-scored

# Internal auth
INTERNAL_API_KEY=<openssl rand -hex 32>

# Dependent services
QUESTION_BANK_BASE_URL=http://question-bank-service:8084
STORAGE_BASE_URL=http://storage-service:8085
TTS_STT_BASE_URL=http://tts-stt-service:8086
USER_PROFILE_BASE_URL=http://user-profile-service:8082

# Workflow tuning
SESSION_INACTIVITY_TIMEOUT_MINUTES=30
ANSWER_PROCESSING_TIMEOUT_MINUTES=10
TRANSCRIPT_MIN_WORD_COUNT=5             # below này → skip eval, mark FAILED
RATE_LIMIT_ANSWERS_PER_MINUTE=5
```

---

## 10. Error codes

| Code | HTTP | Message |
|---|---|---|
| 1000 | 200/201/202 | Success |
| 4001 | 400 | Question id not found in session |
| 4002 | 400 | Storage object not READY |
| 4003 | 400 | Storage object owner mismatch |
| 4004 | 400 | Storage object already used by another answer |
| 4005 | 409 | Session is not IN_PROGRESS |
| 4006 | 409 | Cannot resubmit a SCORED answer |
| 4011 | 401 | Missing or invalid JWT / internal key |
| 4031 | 403 | Not session owner |
| 4041 | 404 | Session / answer not found |
| 4291 | 429 | Rate limit exceeded |
| 5001 | 502 | Question bank unreachable |
| 5002 | 502 | Storage service unreachable |
| 5003 | 502 | Tts-stt service unreachable |

---

## 11. Failure modes & retry strategy

### Submit answer

| Failure | Behavior |
|---|---|
| Storage verification fail | Reject 400, không tạo answer record. |
| DB write fail | Bubble lên 500. FE retry idempotent (dedupe theo `(session,question)`). |
| Outbox write fail (cùng tx với answer) | Toàn bộ rollback — answer chưa tồn tại. |

### Workflow processing

| Failure | Behavior |
|---|---|
| `transcript-failed` event | Flip answer → FAILED. Log + notify. Không auto-retry (lỗi audio thường không tự lành). Admin retry qua `/internal/replay`. |
| `transcript-ready` arrives nhưng answer không tồn tại | Likely race / replay. Log warning, ack message. |
| `evaluation-completed` cho answer không EVALUATING | Likely duplicate event. Idempotent: nếu đã SCORED rồi → ignore. |
| AI-Evaluation timeout (no `evaluation-completed`) | Cron mỗi 5 phút quét answer EVALUATING > 15 phút → republish `evaluation-requested` (đến lần 3 → mark FAILED). |
| Outbox publisher down | Backlog tăng. Alert > 100 unpublished. |

### Session lifecycle

| Failure | Behavior |
|---|---|
| User abandon session | Cron: session IN_PROGRESS không có hoạt động > 30 phút → flip CANCELLED. Background processing vẫn tiếp tục cho answer đã submit. |
| Service crash giữa flow | State machine recovery: tất cả transitions đều persistent. Restart → tiếp tục từ DB state. |

### Idempotency keys

- Submit answer: `(sessionId, questionId)` UNIQUE → resubmit overwrite.
- Consumer dedupe: `eventId` cache (Redis hoặc in-memory với TTL 24h).

---

## 12. Vận hành

### Metrics

- Sessions started/completed/cancelled per hour.
- Avg answer count per session.
- p50/p95/p99 latency:
  - submit → SCORED (end-to-end).
  - SUBMITTED → READY (STT pipeline).
  - READY → SCORED (AI eval).
- Outbox lag (oldest unpublished event).
- Answer FAILED rate per failure type.

### Alerts

- Outbox lag > 30s.
- Answer EVALUATING > 15 phút (stuck).
- Session SCORED rate drop > 50% so với baseline.
- 5xx rate > 1%.

### Healthcheck

`GET /health`:
- DB ping.
- Kafka admin client.
- Mỗi dependent service: HEAD `/health` (cache 30s).

### Deploy

- Theo pattern hiện tại: `ci.yml` build/test, `cd.yml` SSH deploy.
- Image: `ghcr.io/<owner>/mockwise_project/interview-service`.
- Docker compose: thêm vào `docker/docker-compose.yml` cùng network `internal-net`.

---

## 13. Future work

### a. Saga/Workflow engine

Hiện tại state machine code thủ công trong service. Nếu workflow phức tạp hơn (nhiều branching, compensation), có thể chuyển sang **Temporal** hoặc **Camunda** — không thay đổi domain model, chỉ thay engine.

### b. Real-time progress streaming

Server-Sent Events `/interviews/{sid}/stream`: push status update khi answer thay đổi (PROCESSING → READY → SCORED). Tránh polling.

### c. Answer revision history

Hiện tại resubmit overwrite. Future: bảng `answer_revision` lưu lịch sử mỗi lần user nộp lại (debug + analytics: user trả lời đi trả lời lại bao nhiêu lần).

### d. Live coaching

Khi `tts-stt-service` có realtime STT (xem doc tts-stt §13), `interview-service` có thể stream partial transcript về FE để hiển thị live caption + AI hint nhẹ giữa câu trả lời.

### e. Admin replay tooling

UI admin để: xem session bất kỳ, force re-transcribe, force re-evaluate, override score thủ công (kèm reason).
