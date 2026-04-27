# TTS-STT Service — Design Document

Service chịu trách nhiệm 2 việc:

1. **Text → Speech (TTS)**: sinh audio cho câu hỏi `behavioral` và `core` khi admin tạo / update câu hỏi, lưu audio vào `storage-service` (bucket `question-audio`).
2. **Speech → Text (STT)**: chuyển video câu trả lời của user (đã upload qua `storage-service` ở bucket `interview-videos`) thành transcript phục vụ AI-Evaluation chấm điểm.

Backend: ElevenLabs (TTS + Scribe STT). Service không serve audio trực tiếp — luôn đẩy bytes qua `storage-service` để giữ một nguồn lưu trữ duy nhất.

---

## Mục lục

- [1. Phạm vi & nguyên tắc](#1-phạm-vi--nguyên-tắc)
- [2. Kiến trúc](#2-kiến-trúc)
- [3. Flows](#3-flows)
  - [3.1 Flow TTS (admin tạo / update câu hỏi)](#31-flow-tts-admin-tạo--update-câu-hỏi)
  - [3.2 Flow STT (user trả lời xong)](#32-flow-stt-user-trả-lời-xong)
  - [3.3 Tại sao STT dùng event-driven, TTS dùng REST sync](#33-tại-sao-stt-dùng-event-driven-tts-dùng-rest-sync)
- [4. Tích hợp ElevenLabs](#4-tích-hợp-elevenlabs)
- [5. Database schema](#5-database-schema)
- [6. API specification](#6-api-specification)
- [7. Kafka topics](#7-kafka-topics)
- [8. Security model](#8-security-model)
- [9. Configuration](#9-configuration)
- [10. Error codes](#10-error-codes)
- [11. Failure modes & retry strategy](#11-failure-modes--retry-strategy)
- [12. Vận hành](#12-vận-hành)
- [13. Future work](#13-future-work)

---

## 1. Phạm vi & nguyên tắc

**Service làm:**
- Gọi ElevenLabs API (TTS + STT).
- Trích audio từ video phỏng vấn (ffmpeg).
- Cache TTS theo content hash để tránh sinh trùng.
- Lưu transcript của câu trả lời (text + word-level timestamps) vào DB riêng.
- Publish event khi STT hoàn tất / thất bại.

**Service KHÔNG làm:**
- KHÔNG serve audio bytes ra FE (FE lấy presigned URL từ `storage-service`).
- KHÔNG quyết định ACL — `interview-service` (hoặc service tương đương) verify quyền truy cập trước khi xin URL.
- KHÔNG ghi vào DB của `question-bank-service` — chỉ trả `objectKey`, caller tự cập nhật.
- KHÔNG đánh giá nội dung câu trả lời — đó là việc của `AI-Evaluation`.

**Nguyên tắc:**
- **Idempotent**: gọi STT cùng `storageObjectId` 2 lần không sinh 2 transcript / 2 lần tốn API.
- **Retryable**: mọi lỗi tạm thời (network, ElevenLabs 5xx, timeout) đều phải retry an toàn.
- **Single source of truth cho audio**: audio chỉ tồn tại ở MinIO qua `storage-service`. Service này không cache audio file cục bộ ngoài thời gian xử lý.
- **Pull-based worker, không phải data plane proxy**: chỉ nhận pointer (`storageObjectId`), tự xin presigned URL và tự fetch video. Không có caller nào "đẩy bytes" sang service này — bytes luôn đi MinIO → tts-stt theo đường ngắn nhất.
- **Control plane ở orchestrator, data plane ở đây**: `interview-service` quyết định *khi nào* STT chạy / *cho video nào*; `tts-stt-service` không tự kích hoạt và không tự quyết định gửi kết quả đi đâu — nó chỉ publish `transcript-ready` để orchestrator route tiếp.
- **Video là authoritative artifact**: AI-Evaluation luôn chấm trên transcript được trích từ video đã upload, không phải từ stream realtime (xem [§13](#13-future-work)).

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
                ┌──────────────┴──────────────┐
                │                             │
                ▼                             ▼
        ┌──────────────┐              ┌──────────────┐
        │ question-    │              │  interview-  │
        │ bank-service │              │   service    │   ◄── orchestrator
        │   (8084)     │              │   (8088)     │       (xem interview-service-design.md)
        └──────┬───────┘              └──────┬───────┘
               │ POST /internal/tts          │ publish "answer-submitted"
               │ (sync, REST)                │  (Kafka)
               │                             │
               ▼                             ▼
              ┌────────────────────────────────────────────┐
              │            tts-stt-service                 │   PostgreSQL: tts_stt
              │                 (8086)                     │   ─ tts_cache
              │                                            │   ─ stt_job
              │  ┌──────────────────────────┐              │   ─ transcript
              │  │  ffmpeg (extract audio)  │              │
              │  └──────────────────────────┘              │
              │                                            │
              │  Pull-based: tự xin presigned URL,         │
              │  tự download video. Không nhận bytes.      │
              └─────┬─────────────────────┬────────────────┘
                    │                     │
                    │                     │ POST /internal/question-audio
                    │                     │ (multipart) ──► storage-service ──► MinIO
                    │                     │                                     bucket: question-audio
                    │                     │
                    │                     │ POST /internal/download-url
                    │                     │ (lấy URL video) ──► storage-service ──► MinIO
                    │                     │                                         bucket: interview-videos
                    ▼
              ┌──────────────┐
              │  ElevenLabs  │  TTS:    /v1/text-to-speech/{voice_id}
              │     API      │  STT:    /v1/speech-to-text  (Scribe)
              └──────────────┘
                    │
                    │ on completion, publish:
                    │   transcript-ready  /  transcript-failed
                    ▼
              ┌────────────┐
              │   Kafka    │
              └─────┬──────┘
                    │
                    ▼
         ┌──────────────────┐
         │ interview-service│   ◄── ONLY consumer của transcript-ready.
         │   (orchestrator) │       Sau đó orchestrator quyết định
         └──────────────────┘       có publish "evaluation-requested"
                                    cho AI-Evaluation hay không.
```

**Lưu ý quan trọng**: `AI-Evaluation` **không** subscribe trực tiếp `transcript-ready`. Mọi event domain (transcript ready / failed) chỉ đi vào `interview-service`. Workflow quyết định gọi AI là việc của orchestrator — `tts-stt-service` không biết và không quan tâm sau STT thì làm gì.

### Vị trí trong stack

| Thuộc tính | Giá trị |
|---|---|
| Service name | `tts-stt-service` |
| Port | `8086` (giữa storage `8085` và mail `8087`) |
| DB | PostgreSQL, database `tts_stt` |
| Path prefix | `/api/v1/tts-stt` |
| Tech | Spring Boot 3.x, Java 21, Spring Kafka, Flyway, Lombok |
| Image | `ghcr.io/<owner>/mockwise_project/tts-stt-service` |

### Tách biệt 2 lớp endpoint (giống storage-service)

| Lớp | Path prefix | Auth | Caller |
|---|---|---|---|
| **Internal** | `/api/v1/tts-stt/internal/**` | `X-Internal-Auth: <secret>` | `question-bank`, `interview-service` |
| **Public** | `/api/v1/tts-stt/health` | none | infra (healthcheck) |

Service này **không có public endpoint** ngoài health — admin gọi `question-bank-service`, FE gọi `interview-service`, hai service đó mới gọi sang đây qua internal auth. Đảm bảo gateway trả 404 cho mọi path chứa `/internal/`.

### Quan hệ với `interview-service`

`interview-service` là orchestrator duy nhất của vòng đời session/answer. Quan hệ với `tts-stt-service`:

- **Trigger STT**: `interview-service` publish `answer-submitted` (chứa `storageObjectId` + meta) → `tts-stt-service` consume.
- **Kết quả STT**: `tts-stt-service` publish `transcript-ready` / `transcript-failed` → `interview-service` consume (và *chỉ* nó consume, không phải AI-Evaluation).
- **Lấy nội dung transcript**: `interview-service` gọi `GET /internal/transcripts/{id}` để lấy text + word timestamps khi cần assemble payload cho AI.

`tts-stt-service` **không** biết gì về session, không biết answer được chấm hay không, không gọi AI. Chỉ có nhiệm vụ: nhận pointer, sinh transcript, báo xong.

Xem chi tiết workflow ở [interview-service-design.md](../../interview-service/docs/interview-service-design.md).

---

## 3. Flows

### 3.1 Flow TTS (admin tạo / update câu hỏi)

```
admin                question-bank             tts-stt-service              ElevenLabs        storage-service
 │  POST /questions       │                          │                            │                  │
 │  (text, ...)           │                          │                            │                  │
 │───────────────────────►│ insert question          │                            │                  │
 │                        │                          │                            │                  │
 │                        │ POST /internal/tts       │                            │                  │
 │                        │ { questionId, text,      │                            │                  │
 │                        │   voiceId, language }    │                            │                  │
 │                        │ X-Internal-Auth          │                            │                  │
 │                        │─────────────────────────►│                            │                  │
 │                        │                          │ hash = sha256(text+voice)  │                  │
 │                        │                          │                            │                  │
 │                        │                          │ tts_cache lookup hash      │                  │
 │                        │                          │   ├─ HIT → return objectKey│                  │
 │                        │                          │   └─ MISS:                 │                  │
 │                        │                          │      POST /text-to-speech  │                  │
 │                        │                          │─────────────────────────────►                 │
 │                        │                          │◄────── audio/mpeg bytes ────                  │
 │                        │                          │                                               │
 │                        │                          │  POST /internal/question-audio (multipart)    │
 │                        │                          │──────────────────────────────────────────────►│
 │                        │                          │◄────── { objectKey, sizeBytes } ──────────────│
 │                        │                          │                                               │
 │                        │                          │ insert tts_cache(hash, objectKey)             │
 │                        │ ◄──── { objectKey,       │                                               │
 │                        │        durationMs,       │                                               │
 │                        │        cached: false }   │                                               │
 │                        │                          │                                               │
 │                        │ UPDATE question          │                                               │
 │                        │   SET audio_key = ...    │                                               │
 │ ◄──── 201 Created ─────│                          │                                               │
```

**Khi admin update text của câu hỏi**: `question-bank` gọi lại `/internal/tts` với text mới — nếu text không đổi (vẫn cùng hash) thì cache hit, không tốn ElevenLabs. Nếu đổi: sinh audio mới, **không xóa audio cũ** — `question-bank` chỉ ghi đè `audio_key`. Audio cũ sẽ được dọn bằng cron riêng (xem [§13](#13-future-work)).

**Vì sao sync (REST) chứ không phải event?** Admin click "Save" và cần biết audio đã sẵn sàng để gắn vào câu hỏi. Text TTS thường ngắn (< 500 token), ElevenLabs trả trong 2–5s. Trả response qua event sẽ buộc admin chờ callback hoặc poll → UX kém. Nếu ElevenLabs chậm, return 202 + jobId là phương án dự phòng (xem [§11](#11-failure-modes--retry-strategy)).

### 3.2 Flow STT (user trả lời xong)

```
FE          storage-service       interview-service        Kafka         tts-stt-service        ElevenLabs
 │ PUT video to MinIO    │                │                  │                  │                    │
 │ (presigned, browser → MinIO direct)    │                  │                  │                    │
 │                       │                │                  │                  │                    │
 │ POST /uploads/videos/ │                │                  │                  │                    │
 │  {id}/complete        │                │                  │                  │                    │
 │──────────────────────►│ stat + READY   │                  │                  │                    │
 │ ◄── 200 ──────────────│                │                  │                  │                    │
 │                                                                                                   │
 │ POST /interviews/{sid}/questions/{qid}/answers                                                    │
 │ { storageObjectId }   │                │                  │                  │                    │
 │──────────────────────────────────────► │ verify ACL,      │                  │                    │
 │                                        │ verify object    │                  │                    │
 │                                        │ owner+READY,     │                  │                    │
 │                                        │ insert answer    │                  │                    │
 │                                        │ (SUBMITTED →     │                  │                    │
 │                                        │  PROCESSING)     │                  │                    │
 │                                        │                  │                  │                    │
 │                                        │ publish          │                  │                    │
 │                                        │ "answer-submitted"                  │                    │
 │                                        │ (via outbox)     │                  │                    │
 │                                        │─────────────────►│                  │                    │
 │ ◄─── 202 { answerId } ──────────────── │                  │                  │                    │
 │                                                           │  consume         │                    │
 │                                                           │─────────────────►│                    │
 │                                                           │                  │ dedupe by          │
 │                                                           │                  │  storageObjectId   │
 │                                                           │                  │   ├─ exists+READY  │
 │                                                           │                  │   │   → ack + skip │
 │                                                           │                  │   └─ insert stt_job│
 │                                                           │                  │     (PROCESSING)   │
 │                                                           │                  │                    │
 │                                                           │   POST /internal/download-url         │
 │                                                           │   (kind=INTERVIEW_VIDEO, ttl=600s)    │
 │                                                           │                  │ ──► storage        │
 │                                                           │                  │ ◄── presigned URL  │
 │                                                           │                  │                    │
 │                                                           │                  │ stream from MinIO  │
 │                                                           │                  │ → ffmpeg → opus    │
 │                                                           │                  │ POST /v1/speech-to-text
 │                                                           │                  │───────────────────►│
 │                                                           │                  │◄── text + words[]──│
 │                                                           │                  │                    │
 │                                                           │                  │ insert transcript  │
 │                                                           │                  │ stt_job=READY      │
 │                                                           │                  │                    │
 │                                                           │  publish         │                    │
 │                                                           │  "transcript-ready"                   │
 │                                                           │◄─────────────────│                    │
 │                                                           │                  │                    │
 │                                        consume            │                  │                    │
 │                                       ◄───────────────────│                  │                    │
 │                                        GET /internal/transcripts/{id}        │                    │
 │                                       ─────────────────────────────────────► │ text + words       │
 │                                       ◄───────────────────────────────────── │                    │
 │                                        flip answer→READY,                    │                    │
 │                                        publish                               │                    │
 │                                        "evaluation-requested"                │                    │
 │                                        (sang AI-Evaluation, không show ở đây)│                    │
 │                                                                                                   │
 │ GET /interviews/{sid}/answers/{aid}    (poll hoặc SSE)                                            │
 │──────────────────────────────────────► (interview-service trả status mới nhất)                    │
```

**Điểm khác biệt so với phiên bản trước của doc**:

- Event nguồn không còn là `interview-video-uploaded` do `storage-service` publish, mà là `answer-submitted` do `interview-service` publish. Lý do: `storage-service` là infrastructure, không có ngữ cảnh business để biết video nào là answer thật và cần STT. `interview-service` mới có context đó.
- `transcript-ready` chỉ có 1 consumer là `interview-service` (orchestrator), **không** AI-Evaluation. Quyết định gọi AI là của orchestrator.
- `tts-stt-service` không thay đổi gì về phần media processing — chỉ đổi tên topic consume.

**FE khi nào lấy được transcript?** Đây là vấn đề của `interview-service`, không phải `tts-stt-service`. Service này chỉ đảm bảo event được publish; orchestrator lo phần thông báo về FE (poll / SSE).

### 3.3 Tại sao STT dùng event-driven, TTS dùng REST sync

| Tiêu chí | TTS (admin) | STT (user) |
|---|---|---|
| Tính tương tác | Synchronous (admin chờ) | Asynchronous (user đã chuyển trang) |
| Thời lượng | 2–5s | 30s – vài phút |
| Caller cần response | Có (audioUrl để gắn vào câu hỏi) | Không (chỉ trigger) |
| Fan-out | 1 consumer (question-bank) | 1 consumer (interview-service); orchestrator route tiếp |
| Mất event = mất gì | Audio không gắn được — admin retry là OK | Câu trả lời không được chấm — phải retry tự động |
| **Lựa chọn** | **REST sync** | **Kafka event-driven** |

Stack đã có Kafka sẵn (`judge-service` consume `code-submission`, publish `submission-judged`). Pattern STT đề xuất giống hệt: consume `answer-submitted` (do `interview-service` publish), publish `transcript-ready` (cho `interview-service` consume).

---

## 4. Tích hợp ElevenLabs

### TTS

- **Endpoint**: `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}`
- **Header**: `xi-api-key: ${ELEVENLABS_API_KEY}`, `Accept: audio/mpeg`
- **Body** (JSON):
  ```json
  {
    "text": "...",
    "model_id": "eleven_multilingual_v2",
    "voice_settings": { "stability": 0.5, "similarity_boost": 0.75 }
  }
  ```
- **Response**: binary `audio/mpeg` (mp3, mặc định 44.1kHz / 128kbps).
- **Voice mặc định**: cấu hình qua env `ELEVENLABS_DEFAULT_VOICE_ID`. Cho phép override per-question (field `voiceId` trong request `/internal/tts`).
- **Language**: `eleven_multilingual_v2` tự detect, không cần khai báo. Nếu cần ép tiếng (ví dụ tiếng Việt rõ ràng) dùng model `eleven_turbo_v2_5` + `language_code: vi`.

### STT (Scribe)

- **Endpoint**: `POST https://api.elevenlabs.io/v1/speech-to-text`
- **Header**: `xi-api-key: ${ELEVENLABS_API_KEY}`
- **Body** (multipart/form-data):
  - `file`: audio bytes (mp3/opus/wav)
  - `model_id`: `scribe_v1`
  - `language_code`: optional (`en`, `vi`, ...) — set rõ nếu biết, vì auto-detect đôi khi nhầm
  - `timestamps_granularity`: `word` — bắt buộc, cần word-level cho AI evaluation
- **Response** (JSON):
  ```json
  {
    "text": "Hello, my name is...",
    "language_code": "en",
    "language_probability": 0.99,
    "words": [
      { "text": "Hello", "type": "word", "start": 0.12, "end": 0.34, "speaker_id": "speaker_1" },
      ...
    ]
  }
  ```
- **Giới hạn**: 1 GB / file, ~4.5 giờ. Audio phỏng vấn vài phút nên không gặp, nhưng vẫn validate trước khi gọi.

### Trích audio từ video

```
ffmpeg -i input.webm -vn -acodec libopus -b:a 32k -ar 16000 -ac 1 output.opus
```

- `-vn`: bỏ video track.
- `libopus @ 32 kbps mono 16 kHz`: đủ cho STT, file nhỏ → upload ElevenLabs nhanh.
- File tạm trong `/tmp`, xóa ngay sau khi STT xong (kể cả khi lỗi).

### Quản lý chi phí

- **TTS cache** (xem [§5](#5-database-schema)): tránh sinh lại khi text/voice không đổi.
- **STT idempotency**: 1 video → 1 transcript. Re-run chỉ khi admin force qua endpoint riêng.
- **Rate limit phía service**: bucket token theo `userId` cho TTS, theo `sessionId` cho STT — tránh bị abuse hoặc retry storm.
- **Quota guard**: monitor monthly usage qua ElevenLabs API + alert khi đạt 80%.

---

## 5. Database schema

Database: `tts_stt`. Migration qua Flyway tại `src/main/resources/db/migration/`.

### `tts_cache`

Cache audio đã sinh để admin update text mà không đổi nội dung thì không tốn API.

```sql
CREATE TABLE tts_cache (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_hash    CHAR(64)     NOT NULL,         -- sha256(text + voice_id + model_id)
    voice_id        VARCHAR(64)  NOT NULL,
    model_id        VARCHAR(64)  NOT NULL,
    storage_object_id UUID       NOT NULL,         -- id ở storage-service
    object_key      VARCHAR(500) NOT NULL,         -- "audio/<uuid>"
    duration_ms     INTEGER,
    size_bytes      BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    use_count       INTEGER      NOT NULL DEFAULT 1,
    UNIQUE (content_hash, voice_id, model_id)
);
CREATE INDEX idx_tts_cache_last_used ON tts_cache(last_used_at);
```

### `stt_job`

Track 1 lần xử lý STT cho 1 video. Cho phép retry, observability.

```sql
CREATE TABLE stt_job (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_object_id   UUID         NOT NULL,
    answer_id           UUID         NOT NULL,     -- ref tới interview-service.answer.id
    session_id          VARCHAR(36)  NOT NULL,
    question_id         VARCHAR(36)  NOT NULL,
    owner_user_id       VARCHAR(36)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,     -- PENDING | PROCESSING | READY | FAILED
    error_code          VARCHAR(40),
    error_message       TEXT,
    transcript_id       UUID,                       -- FK → transcript.id (nullable, set khi READY)
    attempt_count       INTEGER      NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    published_event_at  TIMESTAMPTZ,                -- khi đã publish transcript-ready/failed
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (storage_object_id)                     -- idempotency: 1 video = 1 job
);
CREATE INDEX idx_stt_job_answer  ON stt_job(answer_id);
CREATE INDEX idx_stt_job_session ON stt_job(session_id);
CREATE INDEX idx_stt_job_status  ON stt_job(status);
```

`answer_id` cho phép correlate ngược về `interview-service.answer` (ref qua giá trị, không phải FK cứng — 2 service tách DB). `published_event_at` cho phép cron quét và republish event nếu service crash giữa lúc commit và publish.

### `transcript`

Kết quả STT cuối cùng. Tách khỏi `stt_job` để có thể giữ history khi force re-transcribe.

```sql
CREATE TABLE transcript (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stt_job_id          UUID         NOT NULL REFERENCES stt_job(id),
    storage_object_id   UUID         NOT NULL,
    answer_id           UUID         NOT NULL,
    session_id          VARCHAR(36)  NOT NULL,
    question_id         VARCHAR(36)  NOT NULL,
    text                TEXT         NOT NULL,
    language_code       VARCHAR(10)  NOT NULL,
    language_confidence REAL,
    words               JSONB        NOT NULL,     -- [{text,start,end,speakerId,confidence}]
    duration_ms         INTEGER,
    model_id            VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_transcript_answer  ON transcript(answer_id);
CREATE INDEX idx_transcript_session ON transcript(session_id);
CREATE INDEX idx_transcript_storage ON transcript(storage_object_id);
```

**Vì sao lưu `words` (jsonb)?** AI-Evaluation cần để phát hiện pause, filler words ("um", "ah"), pace nói (words/min). Lưu raw từ ElevenLabs là rẻ và linh hoạt — không cần cấu trúc cứng.

---

## 6. API specification

Chi tiết JSON schema sẽ được wire vào `api-spec/tts-stt-service.yaml` (OpenAPI 3.1) — tương tự các service khác.

### 6.1 `GET /api/v1/tts-stt/health`

Public, không auth. Trả `{ "code": 1000, "data": null }`.

### 6.2 `POST /api/v1/tts-stt/internal/tts`

**Auth**: `X-Internal-Auth: <INTERNAL_API_KEY>`
**Caller**: `question-bank-service` (sync khi admin tạo / update câu hỏi).

**Request**:
```json
{
  "questionId": "01JX9...",
  "text": "Tell me about a time you faced a tight deadline.",
  "voiceId": "21m00Tcm4TlvDq8ikWAM",
  "modelId": "eleven_multilingual_v2",
  "languageCode": "en"
}
```

| Field | Required | Default | Notes |
|---|---|---|---|
| `questionId` | yes | — | dùng cho audit log |
| `text` | yes | — | 1 ≤ length ≤ 5000 chars |
| `voiceId` | no | env default | ElevenLabs voice id |
| `modelId` | no | `eleven_multilingual_v2` | |
| `languageCode` | no | null (auto) | ISO 639-1 |

**Response 200**:
```json
{
  "code": 1000,
  "data": {
    "objectId": "ab12cd34-...",
    "objectKey": "audio/8f1c3a4d-...",
    "bucket": "question-audio",
    "sizeBytes": 184320,
    "durationMs": 8540,
    "cached": false
  }
}
```

`cached: true` khi cache hit — caller có thể dùng để tránh ghi đè field `audioKey` không cần thiết.

### 6.3 `POST /api/v1/tts-stt/internal/transcripts`

**Auth**: `X-Internal-Auth: <INTERNAL_API_KEY>`
**Caller**: `interview-service` — admin tooling (replay) hoặc fallback khi Kafka không khả dụng.

> Chế độ chính của STT là **event-driven** (xem [§7](#7-kafka-topics)). Endpoint này tồn tại để:
> - Backfill / replay cho video cũ (sau khi đã có DLQ).
> - Test thủ công khi develop.
> - Force re-transcribe (param `force: true`).

**Request**:
```json
{
  "storageObjectId": "0a1b2c3d-...",
  "answerId": "...",
  "sessionId": "01JX9P...",
  "questionId": "01JXAA...",
  "ownerUserId": "u-7f3a",
  "languageCode": "en",
  "force": false
}
```

**Response 202**:
```json
{
  "code": 1000,
  "data": {
    "jobId": "5e6f7a8b-...",
    "status": "PROCESSING",
    "storageObjectId": "0a1b2c3d-..."
  }
}
```

Nếu `storageObjectId` đã có job READY và `force: false` → 200 với `status: READY` + `transcriptId` (idempotent, không gọi lại ElevenLabs).

### 6.4 `GET /api/v1/tts-stt/internal/transcripts/{transcriptId}`

**Auth**: `X-Internal-Auth`
**Caller**: `AI-Evaluation`

**Response 200**:
```json
{
  "code": 1000,
  "data": {
    "transcriptId": "...",
    "sessionId": "...",
    "questionId": "...",
    "text": "Hello, my name is...",
    "languageCode": "en",
    "languageConfidence": 0.99,
    "durationMs": 92500,
    "words": [
      { "text": "Hello", "start": 0.12, "end": 0.34, "confidence": 0.98 }
    ],
    "createdAt": "2026-04-26T10:32:00Z"
  }
}
```

### 6.5 `GET /api/v1/tts-stt/internal/jobs/{jobId}`

**Auth**: `X-Internal-Auth`
**Caller**: dùng để debug / observability.

Trả status job + error nếu FAILED.

---

## 7. Kafka topics

Đặt tên đồng bộ với pattern hiện có (`code-submission`, `submission-judged`).

### Consume: `answer-submitted`

**Producer**: `interview-service` (publish khi user nộp answer kiểu VIDEO, qua transactional outbox).

**Payload**:
```json
{
  "eventId": "uuid",
  "eventType": "ANSWER_SUBMITTED",
  "occurredAt": "2026-04-26T10:25:30Z",
  "answerId": "...",
  "sessionId": "01JX9P2K8R3C4FZ8N7H5BWAQ12",
  "questionId": "01JXAA...",
  "ownerUserId": "u-7f3a",
  "storageObjectId": "0a1b2c3d-...",
  "videoMeta": {
    "bucket": "interview-videos",
    "objectKey": "u-7f3a/sess-01JX9P/8f1c3a4d-...",
    "contentType": "video/webm",
    "sizeBytes": 12582912
  },
  "languageHint": "en"
}
```

**Consumer group**: `tts-stt-service`
**Idempotency**: dedupe theo `storageObjectId` (UNIQUE constraint trên `stt_job`). Cùng `answerId` resubmit cũng tạo `storageObjectId` mới (do upload video mới), nên không cần dedupe theo `answerId`.

> **Lưu ý lịch sử**: phiên bản cũ của doc đề xuất `storage-service` publish event `interview-video-uploaded`. Đã refactor sang `answer-submitted` từ `interview-service` để giữ infrastructure service (storage) khỏi business semantics.

### Produce: `transcript-ready`

```json
{
  "eventId": "uuid",
  "eventType": "TRANSCRIPT_READY",
  "occurredAt": "...",
  "transcriptId": "...",
  "sttJobId": "...",
  "answerId": "...",
  "storageObjectId": "...",
  "sessionId": "...",
  "questionId": "...",
  "ownerUserId": "...",
  "languageCode": "en",
  "durationMs": 92500,
  "wordCount": 184
}
```

**Single consumer**: `interview-service` (orchestrator). Không có service khác subscribe topic này.

Payload **không** chứa text — consumer gọi lại `GET /internal/transcripts/{id}` để lấy. Lý do: tránh phình message Kafka, tránh duplicate text giữa Kafka log và DB. Field `answerId` được giữ trong event để consumer correlate trực tiếp tới record của nó mà không cần lookup ngược.

### Produce: `transcript-failed`

```json
{
  "eventId": "uuid",
  "eventType": "TRANSCRIPT_FAILED",
  "occurredAt": "...",
  "sttJobId": "...",
  "answerId": "...",
  "storageObjectId": "...",
  "sessionId": "...",
  "questionId": "...",
  "errorCode": "ELEVENLABS_5XX | AUDIO_EXTRACT_FAILED | DOWNLOAD_FAILED | INVALID_AUDIO",
  "errorMessage": "...",
  "attemptCount": 3
}
```

Consumer: `interview-service` — sẽ flip `answer.status = FAILED` và notify FE.

### DLQ

Sau N lần retry (xem [§11](#11-failure-modes--retry-strategy)), record được publish sang topic DLQ `answer-submitted.DLT` qua Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.

### Outbox

Producer side (`interview-service`) phải dùng **transactional outbox**: lưu event vào table `outbox_event` cùng transaction với business write, có poller riêng đẩy lên Kafka. Đảm bảo at-least-once mà không mất event nếu Kafka down giữa commit và publish.

> Quyết định scope: **outbox là việc của producer** (`interview-service`, `question-bank-service` khi cần). `tts-stt-service` chỉ cần idempotent consumer + idempotent producer (publish `transcript-ready` sau khi commit DB; nếu publish fail → republish lúc service restart vì stt_job vẫn ở READY mà chưa publish — track bằng cờ `published_event_at`).

---

## 8. Security model

### Layer 1 — Gateway

- Reject mọi path chứa `/internal/` từ public → 404.
- Service này **không có public endpoint** ngoài `/health`. Gateway có thể bỏ qua route, hoặc chỉ expose `/health` cho healthcheck.

### Layer 2 — Service

- `InternalAuthFilter`: so sánh `X-Internal-Auth` với `INTERNAL_API_KEY` (shared secret toàn stack). Match → seed `ROLE_INTERNAL`.
- `SecurityConfig`:
  - `/internal/**` → `ROLE_INTERNAL`
  - `/health` → public

### Layer 3 — Service logic

- **ElevenLabs API key**: chỉ tồn tại trong env của service này, không expose qua bất kỳ endpoint nào.
- **Storage download URL TTL**: xin với `ttlSeconds: 600` cho video (đủ để download), `60` cho audio. Service không lưu URL.
- **Audit log**: mọi lần gọi ElevenLabs ghi log với `sessionId/questionId`, response status, billing units. Phục vụ truy vết và đối soát chi phí.
- **PII**: text câu trả lời có thể chứa thông tin cá nhân của user. Lưu trong DB không mã hóa ở field level (chấp nhận tradeoff), nhưng:
  - DB nằm trong internal-net.
  - Backup bật encryption-at-rest.
  - Không log full transcript ra stdout.

### Layer 4 — Network

- Outbound đến `api.elevenlabs.io:443` qua HTTPS (TLS 1.2+).
- Inbound từ `question-bank`, `interview-service`, Kafka — internal-net only.

---

## 9. Configuration

### Environment variables

```bash
# Server
PORT=8086
SPRING_PROFILES_ACTIVE=local|prod

# Database
POSTGRES_HOST=...
POSTGRES_PORT=5432
POSTGRES_DB=tts_stt
POSTGRES_USER=mockwise
POSTGRES_PASSWORD=...

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=tts-stt-service
KAFKA_TOPIC_ANSWER_SUBMITTED=answer-submitted          # consume
KAFKA_TOPIC_TRANSCRIPT_READY=transcript-ready          # produce
KAFKA_TOPIC_TRANSCRIPT_FAILED=transcript-failed        # produce

# Internal auth
INTERNAL_API_KEY=<openssl rand -hex 32>     # shared với cả stack

# Storage service (S2S)
STORAGE_SERVICE_BASE_URL=http://storage-service:8085
STORAGE_INTERNAL_API_KEY=${INTERNAL_API_KEY}

# ElevenLabs
ELEVENLABS_API_KEY=...
ELEVENLABS_BASE_URL=https://api.elevenlabs.io
ELEVENLABS_DEFAULT_VOICE_ID=21m00Tcm4TlvDq8ikWAM
ELEVENLABS_TTS_MODEL=eleven_multilingual_v2
ELEVENLABS_STT_MODEL=scribe_v1
ELEVENLABS_TIMEOUT_TTS_MS=30000
ELEVENLABS_TIMEOUT_STT_MS=600000             # video dài → cần lâu

# ffmpeg
FFMPEG_BIN=/usr/bin/ffmpeg
FFMPEG_TMP_DIR=/tmp/tts-stt

# Limits
TTS_MAX_TEXT_LENGTH=5000
STT_MAX_VIDEO_SIZE_BYTES=524288000           # 500 MB (đồng bộ storage cap)
STT_MAX_DURATION_SECONDS=1800                # 30 phút
STT_MAX_ATTEMPTS=3
```

### Spring profiles

- `local`: Postgres + Kafka + MinIO trên localhost. ElevenLabs có thể stub bằng MockServer.
- `prod`: chạy trên VPS qua docker-compose, env truyền vào từ `.env` của infra.

### Dockerfile

Phải bundle `ffmpeg` vào image:

```dockerfile
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
COPY target/tts-stt-service-*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

---

## 10. Error codes

Pattern theo `storage-service` (`1000` = success).

| Code | HTTP | Where | Message |
|---|---|---|---|
| 1000 | 200/201/202 | all | Success |
| 4001 | 400 | TTS | Text length exceeds maximum (%d chars) |
| 4002 | 400 | TTS | Voice id not found |
| 4003 | 400 | STT | Storage object not found or not READY |
| 4004 | 400 | STT | Audio duration exceeds maximum (%d seconds) |
| 4005 | 400 | STT | Audio extraction failed (corrupted video) |
| 4011 | 401 | both | Missing or invalid internal API key |
| 4041 | 404 | both | Job / transcript not found |
| 4291 | 429 | both | Rate limit exceeded |
| 5001 | 502 | TTS | ElevenLabs upstream error |
| 5002 | 504 | TTS | ElevenLabs timeout |
| 5003 | 502 | STT | ElevenLabs Scribe error |
| 5004 | 500 | STT | ffmpeg failure |
| 5005 | 502 | both | Storage service unreachable |

---

## 11. Failure modes & retry strategy

### TTS (sync REST)

| Failure | Behavior |
|---|---|
| ElevenLabs timeout | Retry 1 lần với exponential backoff (1s, 4s). Vẫn fail → return 504 (5002). Caller (`question-bank`) hiển thị message cho admin retry. |
| ElevenLabs 5xx | Retry như trên. |
| ElevenLabs 4xx | Không retry — likely text/voice invalid. Return 400 (4002) hoặc bubble up. |
| `storage-service` upload fail | Retry 1 lần, sau đó fail toàn bộ request. Cache **không** được ghi (tránh "ghost cache entry" trỏ vào object không tồn tại). |
| Cache hit nhưng object đã bị xóa ở MinIO | Phát hiện qua sanity check thỉnh thoảng (cron). Khi gặp, invalidate cache entry và sinh lại. |

### STT (async event)

| Failure | Behavior |
|---|---|
| Download video fail (storage timeout) | Retry 3 lần (10s, 30s, 90s). Sau đó publish `transcript-failed` + DLQ. |
| ffmpeg fail | Không retry (likely video corrupted) → publish failed ngay. |
| ElevenLabs 5xx | Retry 3 lần, exponential. |
| ElevenLabs 4xx (audio invalid) | Không retry → failed. |
| ElevenLabs timeout | Retry 1 lần với deadline lớn hơn. |
| Service crash giữa chừng | `stt_job` ở trạng thái PROCESSING quá `STT_PROCESSING_TIMEOUT` (15 phút) → cron reset về PENDING + republish event. |

### Idempotency keys

- TTS: `(content_hash, voice_id, model_id)` — UNIQUE constraint chống race.
- STT: `storage_object_id` — UNIQUE constraint trên `stt_job`. Consumer trước khi tạo job, query existing — nếu READY thì ack ngay.

### Backpressure

- ElevenLabs rate limit (theo plan). Nếu hit 429, dừng consume Kafka tạm thời (`pause()`), retry với backoff lớn (1 phút), rồi `resume()`. Tránh dồn lỗi 429 lên DLQ.

---

## 12. Vận hành

### Monitor cần có

- **Metric**: TTS calls/min, STT calls/min, ElevenLabs latency p50/p95/p99, ElevenLabs error rate, ffmpeg failures, cache hit rate, DLQ size.
- **Alert**:
  - Cache hit rate < 30% kéo dài 1h → có thể text đang bị nhiễu (timestamp lén lút trong text...).
  - ElevenLabs error rate > 5% trong 10 phút.
  - DLQ > 10 records.
  - Monthly ElevenLabs usage > 80% quota.
- **Log**: structured JSON, include `sessionId`, `questionId`, `storageObjectId`, `jobId` để correlate.

### Healthcheck

`GET /health` kiểm tra:
- DB (Postgres ping)
- Kafka (admin client)
- ElevenLabs (HEAD `/v1/voices`, cache 5 phút — không gọi mỗi request)
- ffmpeg (run `ffmpeg -version` 1 lần lúc startup, đánh dấu)

### Build & deploy

Theo pattern hiện tại:
- CI: `ci.yml` thêm job `tts-stt-service` (build + test + push image).
- CD: `cd.yml` thêm step deploy.
- `docker/docker-compose.yml`: thêm service block, mount `.env`, network `internal-net`.

---

## 13. Future work

### a. Real-time STT cho live caption

Phase sau, nếu UX cần caption hiển thị khi user đang nói:
- Mở WebSocket endpoint `/api/v1/tts-stt/internal/live-stt` (hoặc proxy qua `interview-service`).
- Stream PCM frames từ FE → ElevenLabs streaming STT → đẩy text về FE.
- **Quan trọng**: text từ live STT chỉ dùng để hiển thị, **không** thay thế transcript batch của video đã upload. AI-Evaluation vẫn chấm trên transcript batch (chính xác hơn, đầy đủ hơn).

### b. Dọn audio orphan của TTS cache

Khi admin đổi text câu hỏi nhiều lần, audio cũ trở thành orphan. Cron hàng ngày:
- Quét `tts_cache` mà không có record nào trong `question-bank.*.audio_key` trỏ vào (cần API ngược từ `question-bank` để liệt kê audio_key đang dùng).
- Audio orphan > 30 ngày → delete khỏi MinIO + xóa cache entry.

### c. Multi-voice / accent

Hiện tại chọn 1 voice mặc định. Tương lai cho admin chọn voice khi tạo câu hỏi (phỏng vấn UK English vs US English vs tiếng Việt).

### d. Speaker diarization

ElevenLabs Scribe trả `speaker_id`. Hiện tại phỏng vấn 1-1 nên chỉ có 1 speaker, không cần tách. Nếu sau này có panel interview (nhiều phỏng vấn viên), feature này có sẵn.

### e. Self-hosted fallback

Nếu chi phí ElevenLabs quá cao hoặc cần on-prem, có thể switch sang Whisper (STT) + Coqui/Bark (TTS). Service này thiết kế behind interface `TtsProvider` / `SttProvider` — đổi backend không cần đổi caller.
