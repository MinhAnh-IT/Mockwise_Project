# Optimization — Submit Answer → Next Question

Tài liệu này mô tả **chi tiết** luồng từ lúc user bấm "Nộp câu trả lời" cho tới khi câu hỏi tiếp theo được pin vào session, **các điểm gây chậm**, **đề xuất tối ưu**, và **kế hoạch triển khai theo phase** để bạn có thể tự viết lại sau này.

Mục đích: tài liệu spec, không phải PR — tất cả số liệu (latency, RTT) là ước lượng dựa trên đọc code; cần confirm bằng metric / trace thực tế trước khi cam kết.

---

## Mục lục

- [0. TL;DR](#0-tldr)
- [1. Luồng hiện tại — chi tiết từng bước](#1-luồng-hiện-tại--chi-tiết-từng-bước)
  - [1.1 Sơ đồ tổng](#11-sơ-đồ-tổng)
  - [1.2 Stage A — FE submit (đồng bộ)](#12-stage-a--fe-submit-đồng-bộ)
  - [1.3 Stage B — STT pipeline (tts-stt-service)](#13-stage-b--stt-pipeline-tts-stt-service)
  - [1.4 Stage C — applyTranscriptReady](#14-stage-c--applytranscriptready)
  - [1.5 Stage D — AI evaluator](#15-stage-d--ai-evaluator)
  - [1.6 Stage E — applyEvaluationCompleted + planner + pin next](#16-stage-e--applyevaluationcompleted--planner--pin-next)
  - [1.7 Stage F — FE polling biết câu mới](#17-stage-f--fe-polling-biết-câu-mới)
- [2. Phân tích latency](#2-phân-tích-latency)
  - [2.1 Bảng phân rã thời gian](#21-bảng-phân-rã-thời-gian)
  - [2.2 Latency "rác" (cứu được)](#22-latency-rác-cứu-được)
  - [2.3 Latency cứng (không cứu được trong scope service)](#23-latency-cứng-không-cứu-được-trong-scope-service)
- [3. Quy ước về REST internal hiện có](#3-quy-ước-về-rest-internal-hiện-có)
- [4. Đề xuất tối ưu — phase 1 (không đổi giao thức)](#4-đề-xuất-tối-ưu--phase-1-không-đổi-giao-thức)
  - [4.1 Giảm OutboxPoller polling interval](#41-giảm-outboxpoller-polling-interval)
  - [4.2 Nhúng transcript text vào `transcript-ready` event](#42-nhúng-transcript-text-vào-transcript-ready-event)
  - [4.3 Snapshot user profile vào session khi /start](#43-snapshot-user-profile-vào-session-khi-start)
  - [4.4 Gộp question-bank `/filter` + `/snapshot`](#44-gộp-question-bank-filter--snapshot)
  - [4.5 `markAskedSoft` chạy async thực sự](#45-markaskedsoft-chạy-async-thực-sự)
  - [4.6 Bỏ storage check đồng bộ trên `/submit`](#46-bỏ-storage-check-đồng-bộ-trên-submit)
  - [4.7 Parallel hoá trong `handleMoveNext`](#47-parallel-hoá-trong-handlemovenext)
  - [4.8 Bật connection pool keep-alive cho Feign](#48-bật-connection-pool-keep-alive-cho-feign)
- [5. Đề xuất phase 2 — gRPC migration có chọn lọc](#5-đề-xuất-phase-2--grpc-migration-có-chọn-lọc)
  - [5.1 Vì sao gRPC giúp (và không giúp) ở đây](#51-vì-sao-grpc-giúp-và-không-giúp-ở-đây)
  - [5.2 Phạm vi migrate đề nghị](#52-phạm-vi-migrate-đề-nghị)
  - [5.3 Mapping REST → gRPC (proto skeleton)](#53-mapping-rest--grpc-proto-skeleton)
  - [5.4 Áp dụng phía client (interview-service)](#54-áp-dụng-phía-client-interview-service)
  - [5.5 Áp dụng phía server](#55-áp-dụng-phía-server)
  - [5.6 Auth + error mapping](#56-auth--error-mapping)
  - [5.7 Rollout strategy — chạy song song REST/gRPC](#57-rollout-strategy--chạy-song-song-restgrpc)
- [6. Đề xuất phase 3 — chuyển polling sang push (SSE/WebSocket)](#6-đề-xuất-phase-3--chuyển-polling-sang-push-ssewebsocket)
- [7. Observability — đo đạc trước/sau](#7-observability--đo-đạc-trướcsau)
- [8. Test plan](#8-test-plan)
- [9. Rủi ro & rollback](#9-rủi-ro--rollback)
- [10. Phụ lục — file references](#10-phụ-lục--file-references)

---

## 0. TL;DR

Tổng quan kết luận để bạn không cần đọc cả tài liệu:

- **Phần lớn latency user thấy là LLM/STT, KHÔNG cứu được** trong scope interview-service (ước 7–22 giây mỗi câu).
- Có **~0.5–3 giây latency "rác"** đến từ:
  - OutboxPoller `fixedDelay = 1000ms` × 3 hop trong flow → tích lũy 0–3s.
  - 7–10 REST call nội bộ với connection setup không pool, payload JSON nặng → tích 200–600ms.
  - 1 RTT thừa từ `transcript-ready` không kèm text → buộc gọi REST GET transcript.
  - 3 RTT thừa từ việc gọi `user-profile` cho cùng userId nhiều lần.
- **Phase 1 (đề mục §4)** chỉ là config + small refactor → cắt được **~1.5–3 giây p99** mà không đổi giao thức.
- **Phase 2 (gRPC)** đáng làm nhưng là phụ trợ; thực thi sau khi phase 1 đã chạy ổn.
- **Phase 3 (SSE/WebSocket)** loại bỏ polling overhead nếu vẫn cần thêm độ mượt.

---

## 1. Luồng hiện tại — chi tiết từng bước

### 1.1 Sơ đồ tổng

```
[FE]  POST /api/v1/interviews/{sid}/questions/{sqid}/answers
   │
   ▼
[interview-service]  AnswerController.submit
   │ Stage A — đồng bộ, người dùng đợi 202
   ▼
   ├─ DB select × 2 (session, sessionQuestion)
   ├─ REST → storage-service  GET /internal/objects/{id}          [Call #1]
   ├─ DB insert answer (PROCESSING)
   ├─ DB insert outbox_event (ANSWER_SUBMITTED)
   └─ 202 returned to FE
                                            │
   OutboxPoller (every 1000ms)              │ delay A: 0–1s
                                            ▼
                                  Kafka topic: answer-submitted
                                            │
                                            ▼
                          [tts-stt-service]  AnswerSubmittedConsumer
                                            │  Stage B
                                            ▼
                          REST → storage presign + MinIO download
                          ffmpeg extract audio
                          Whisper transcribe   (3–10s)
                          DB insert transcript
                          publish transcript-ready  (KHÔNG kèm text)
                                            │
                                            ▼
                                  Kafka topic: transcript-ready
                                            │
[interview-service]  TranscriptConsumer.onTranscriptReady  ◄──────┘
   │ Stage C
   ▼
   ├─ DB select × 3 (answer, sessionQuestion, session)
   ├─ REST → tts-stt  GET /internal/transcripts/{id}              [Call #2]
   ├─ REST → user-profile  GET /profiles/{userId}                 [Call #3]
   ├─ DB update answer (EVALUATING)
   └─ DB insert outbox_event (EVALUATION_REQUESTED)
                                            │
   OutboxPoller                             │ delay B: 0–1s
                                            ▼
                                  Kafka topic: evaluation-requested
                                            │
                                            ▼
                              [AI-Evaluation]  Stage D
                              LLM evaluate     (4–12s)
                              publish evaluation-completed
                                            │
                                            ▼
                                Kafka topic: evaluation-completed
                                            │
[interview-service]  EvaluationConsumer.onEvaluationCompleted ◄────┘
   │ Stage E
   ▼
   ├─ DB select × N (answer, sq, session, blueprint, topicStates, count pinned)
   ├─ DB update answer (SCORED) + verdict
   ├─ planner.plan(...)  (pure CPU, <5ms)
   ├─ side-effect apply (DB writes)
   │
   ├─ Decision = AskFollowUp
   │   ├─ REST → question-bank  GET /follow-ups?…                 [Call #4]
   │   └─ (nếu rỗng) Tier 2 — AI follow-up:
   │       ├─ REST → user-profile  GET /profiles/{userId}         [Call #5]
   │       ├─ REST → AI  POST /generate-follow-up   (3–8s LLM)    [Call #6]
   │       └─ DB insert sessionQuestion (source=AI_GENERATED)
   │
   ├─ Decision = MoveToNextTopic
   │   ├─ REST → user-profile  GET /profiles/{userId}             [Call #5']
   │   ├─ DB select existing question ids (excludeIds)
   │   ├─ REST → question-bank  POST /filter                      [Call #6']
   │   ├─ REST → question-bank  POST /mark-asked/{id}             [Call #7']
   │   ├─ REST → question-bank  GET /snapshot/{id}                [Call #8']
   │   └─ DB insert sessionQuestion (source=BANK)
   │
   └─ sessionFinalizer.maybeRequestOverallReview(...)
                                            │
   OutboxPoller                             │ delay C: 0–1s (chỉ khi end of session)
                                            ▼
                                  Kafka topic: session-evaluation-requested
```

### 1.2 Stage A — FE submit (đồng bộ)

File: `controller/AnswerController.java:48`, service: `service/AnswerService.java:104`.

Đường đi:

1. `AnswerController.submit` nhận `SubmitAnswerInput`.
2. `AnswerService.submit`:
   - `sessionRepo.findById(sessionId)` — kiểm tra ownership + status `IN_PROGRESS`.
   - `sessionQuestionRepo.findById(sessionQuestionId)` — đảm bảo `sqid` thuộc session.
   - Nếu `type == VIDEO`: gọi `storageAdapter.getObject(input.storageObjectId())` → REST `GET /internal/objects/{id}` tới storage-service. Verify: `kind == INTERVIEW_VIDEO`, `status == READY`, `ownerUserId == userId`.
   - `answerRepo.save(...)` với `status = PROCESSING`.
   - `outboxWriter.stage(KafkaTopics.ANSWER_SUBMITTED, ...)` — chèn `outbox_event` row (chưa publish).
3. Trả về `202 ACCEPTED` với `answerId`.

**Đặc điểm latency:** mọi thứ trong stage này nằm trong response time mà user nhìn thấy. 1 REST call out-of-process (storage) là **80–200ms**.

### 1.3 Stage B — STT pipeline (tts-stt-service)

File: `tts-stt-service/.../stt/service/SttOrchestrator.java`.

- Consumer `AnswerSubmittedConsumer` đọc `answer-submitted` từ Kafka.
- `SttOrchestrator`:
  - Lấy `objectKey` từ `videoMeta` trong payload (avoid 1 RTT presign-by-id).
  - `storageClient.requestDownloadUrl(...)` → presigned MinIO URL.
  - Download bytes từ MinIO → tmp file.
  - ffmpeg extract audio (`.wav` 16kHz mono).
  - Whisper transcribe → text + duration + word count.
  - Insert `transcript` row, status `READY`.
  - Publish `transcript-ready` (event: `TranscriptReadyEvent` — KHÔNG chứa text, chỉ `transcriptId`).

**Latency thường thấy:** 3–10 giây cho 60–120s video (phụ thuộc model size + GPU). Đây là phần **không cứu được** ở scope interview-service.

### 1.4 Stage C — applyTranscriptReady

File: `service/AnswerService.java:291`.

1. `answerRepo.findById(answerId)` — early return nếu `status != PROCESSING` (idempotent guard).
2. `sessionQuestionRepo.findById(answer.getSessionQuestionId())`.
3. `ttsSttAdapter.getTranscript(transcriptId)` → **REST** `GET /internal/transcripts/{id}` để lấy `text`, `durationMs`. **[Call #2 — RTT thừa]**
4. Set `answer.status = EVALUATING`, lưu.
5. `sessionRepo.findById(...)`.
6. `userProfileAdapter.getProfile(userId)` → **REST** `GET /profiles/{userId}` để lấy `preferredLanguage`. **[Call #3 — repeat]**
7. Build envelope `EVALUATION_REQUESTED` (BehavioralInput / ConceptualInput format theo AI service).
8. `outboxWriter.stage(EVALUATION_REQUESTED, ...)`.

**Đặc điểm:** chạy trong consumer thread, không ảnh hưởng response time `/submit`, **nhưng** kéo dài thời gian từ "có transcript" → "gửi cho AI". User chờ ở stage F (polling).

### 1.5 Stage D — AI evaluator

File: ngoài Java repo, `AI/api.py` (Python FastAPI).

- Consumer đọc `evaluation-requested` → tạo `BehavioralInput`/`ConceptualInput`.
- LLM call (Anthropic/OpenAI). Latency p50 ~4s, p99 ~12s.
- Publish `evaluation-completed` với `result` (verdict).

### 1.6 Stage E — applyEvaluationCompleted + planner + pin next

File: `service/AnswerService.java:460`. Đây là điểm "thông minh" của orchestrator.

Bước:

1. Load `answer` (idempotent guard nếu đã `SCORED`).
2. Load `sessionQuestion`, `session`.
3. `deriveVerdict(...)` — convert raw map → `AssessmentVerdict` (pure CPU).
4. Save `answer` với `status = SCORED`, `score`, `verdict`, `rawEvaluation`.
5. `runPlannerForAnswer(...)`:
   - Load `blueprint`, `topicStates`, `pinnedSoFar` count.
   - Save `currentState` với `lastDifficulty/lastScore/lastAssessment`.
   - `planner.plan(inputs)` → `PlannerOutcome { decision, sideEffects }`.
   - `sideEffectApplier.apply(...)` — DB writes (cập nhật topic statuses).
   - Switch trên `decision`:
     - `AskFollowUp`: gọi `questionPicker.pickFollowUpFromBank(...)` (REST `/follow-ups`). Nếu rỗng → `pickFollowUpFromAi(...)`: REST `userProfile.getProfile` + REST AI `/generate-follow-up`.
     - `MoveToNextTopic`: `questionPicker.pickForTopic(...)` → REST `userProfile.getProfile` (nếu chưa cache) → REST QB `/filter` → REST QB `/mark-asked` (soft) → REST QB `/snapshot` (soft).
     - `EndSession`: chỉ set `session.status = COMPLETED`.
6. `sessionFinalizer.maybeRequestOverallReview(sessionId)` — chỉ fire khi session đã COMPLETED + tất cả answer terminal.

**Latency:** 100–400ms cho path `MoveToNextTopic` (3–4 REST sequential), 3–8 giây cho path AI follow-up.

### 1.7 Stage F — FE polling biết câu mới

FE hiện đang polling theo 2 endpoint:

- `GET /api/v1/interviews/{sid}/answers/{aid}` — `AnswerController.get` — trả về `AnswerView` với `status`. Khi `sessionStatus == SCORED` mới reveal verdict.
- `GET /api/v1/interviews/{sid}` — `SessionController.get` → `SessionView` — chứa danh sách `pinnedQuestions`. Câu mới xuất hiện ở đây sau stage E.

**Đặc điểm:** FE phải đoán cadence; nếu polling 2s thì user thấy "trễ thêm 0–2s" sau khi stage E commit.

---

## 2. Phân tích latency

### 2.1 Bảng phân rã thời gian

Mỗi câu trả lời behavioral/conceptual, end-to-end (FE bấm Submit → FE nhận câu mới):

| Stage | Phần | p50 | p99 | Source |
|---|---|---|---|---|
| A | Sync sub-stage trên `/submit` | 150ms | 350ms | 1 REST + 2 DB + 1 outbox insert |
| Outbox A | OutboxPoller fixedDelay | 500ms | 1000ms | `application.yml:61` |
| B | STT (Whisper) | 5s | 10s | `tts-stt-service` |
| C | applyTranscriptReady (2 REST + 3 DB) | 200ms | 500ms | `AnswerService:291` |
| Outbox B | OutboxPoller fixedDelay | 500ms | 1000ms | |
| D | AI evaluate (LLM) | 5s | 12s | AI service |
| E | applyEvaluationCompleted + pick next | 200ms | 400ms | nếu MoveToNextTopic |
| E' | applyEvaluationCompleted + pick AI follow-up | 4s | 8s | nếu AskFollowUp + bank miss |
| Outbox C | (không trên hot path, chỉ end-of-session) | — | — | |
| F | FE polling cadence | 1s | 2s | client-side |

**Tổng end-to-end:** ~12.5s p50, ~25s p99 (MoveToNextTopic path); 16–32s nếu AI follow-up.

### 2.2 Latency "rác" (cứu được)

Tổng "lãng phí" có thể cắt:

| Nguồn | Cứu được | Cách |
|---|---|---|
| 3 hop OutboxPoller × 0–1s | ~1.5–3s p99 | §4.1 |
| REST GET transcript thừa | 50–150ms | §4.2 |
| REST GET user-profile × 3 | 100–250ms | §4.3 |
| QB filter + snapshot sequential | 30–80ms | §4.4 |
| `markAsked` block thread | 50–150ms | §4.5 |
| Storage sync check trong `/submit` | 80–200ms | §4.6 |
| Sequential `getProfile` + `findBySession...` | 30–80ms | §4.7 |
| Per-call TCP/TLS reconnect (Feign default) | 5–20ms × N | §4.8 |

**Tổng tối ưu phase 1:** ~2.0–3.5s p99 (chủ yếu là OutboxPoller).

### 2.3 Latency cứng (không cứu được trong scope service)

- Whisper STT (~3–10s) → cứu bằng GPU upgrade / model nhỏ hơn / streaming STT.
- LLM evaluate (~4–12s) → cứu bằng model nhỏ hơn / output token cap / parallel evaluator instances.
- LLM follow-up gen (~3–8s) → cứu bằng cache cao tier 1 (pre-authored bank) nhiều hơn để giảm tỉ lệ rơi vào AI.

Những món này nằm ngoài scope tài liệu này.

---

## 3. Quy ước về REST internal hiện có

Để hiểu rõ vì sao optimization như thế nào, tóm tắt cách interview-service đang gọi service khác:

- **Framework:** Spring Cloud OpenFeign (`spring-cloud-starter-openfeign`), declarative `@FeignClient`.
- **HTTP client mặc định:** `java.net.HttpURLConnection` (KHÔNG có connection pool tự động — mỗi call mở TCP mới, hoặc reuse rất hạn chế).
- **Envelope:** mọi response wrap trong `ApiResponse<T>` (core-apiresponse lib): `{ code, message, success, data }`. Adapter unwrap → throw `BusinessException` nếu `!success`.
- **Auth:** mọi `/internal/...` request inject header `X-Internal-Auth: <api-key>` qua `RequestInterceptor` ở mỗi `*FeignConfig`.
- **Timeout:** `connectTimeout = 2s`, `readTimeout = 5s`, `followRedirects = true` (xem `QuestionBankFeignConfig.options()`).
- **Retry:** `Retryer.NEVER_RETRY`.
- **Error decoder:** `UpstreamErrorDecoder` map status code → `BusinessException` với `StatusCode.*_UNAVAILABLE`.

Các client hiện hữu trong `interview/client/`:

| Adapter | Service | Endpoint chính | Soft-fail? |
|---|---|---|---|
| `StorageAdapter` | storage-service | `/internal/objects/{id}`, `/internal/objects/{id}/video-download-url` | Video URL: yes; getObject: no |
| `UserProfileAdapter` | user-profile-service | `/profiles/{userId}` | No |
| `TtsSttAdapter` | tts-stt-service | `/internal/transcripts/{id}` | No |
| `QuestionBankAdapter` | question-bank-service | `/filter`, `/snapshot/{id}`, `/follow-ups`, `/mark-asked/{id}` | Snapshot + markAsked: yes; filter + follow-ups: no |
| `AiServiceAdapter` | AI service (Python) | `/generate-follow-up` | No |

---

## 4. Đề xuất tối ưu — phase 1 (không đổi giao thức)

### 4.1 Giảm OutboxPoller polling interval

**Vấn đề:** `OutboxPoller.poll()` chạy với `fixedDelay = ${interview.outbox.poll-interval-ms:1000}` (`application.yml:61`). Với 3 outbox hop trong flow (answer-submitted → transcript-ready trigger; evaluation-requested → AI; bất kỳ event terminal nào khác), trung bình thêm `1.5s`, p99 thêm `3s` so với việc publish ngay sau commit.

**Giải pháp tức thì:**

```yaml
# application.yml
interview:
  outbox:
    poll-interval-ms: ${INTERVIEW_OUTBOX_POLL_INTERVAL_MS:100}   # was 1000
    batch-size: ${INTERVIEW_OUTBOX_BATCH_SIZE:50}
    send-timeout-ms: ${INTERVIEW_OUTBOX_SEND_TIMEOUT_MS:5000}
```

**Tác động:** poll 10×/s thay vì 1×/s. DB tải nhẹ vì query có partial index `WHERE published = false` (đã có sẵn — xem comment trong `OutboxPoller.poll`).

**Giải pháp triệt để (nếu vẫn chưa đủ):** thay polling bằng Postgres `LISTEN/NOTIFY`. Trong `OutboxWriter.stage(...)` thêm `entityManager.createNativeQuery("NOTIFY outbox_event_pending").executeUpdate()` ngay sau insert (vẫn cùng tx, NOTIFY chỉ flush khi commit). `OutboxPoller` thay `@Scheduled` bằng background thread `LISTEN outbox_event_pending`. Khi nhận notification → trigger drain ngay. Trade-off:

- Pros: latency ~0–10ms thay vì 100ms.
- Cons: thêm code; cần persistent connection riêng cho LISTEN; phải fallback poll periodically (e.g. 30s) phòng khi miss notify.

**Khuyến nghị:** bắt đầu bằng `poll-interval-ms = 100`. Đánh giá metric. Chỉ làm LISTEN/NOTIFY nếu vẫn cần thêm.

### 4.2 Nhúng transcript text vào `transcript-ready` event

**Vấn đề:** `TranscriptReadyEvent` (`tts-stt-service/.../stt/kafka/event/TranscriptReadyEvent.java`) chỉ chở `transcriptId, durationMs, wordCount`. Interview-service phải gọi REST `GET /internal/transcripts/{id}` (`TtsSttAdapter.getTranscript`) để lấy text. RTT ~50–150ms cho mỗi answer.

**Giải pháp:** thêm field `transcriptText` (hoặc `transcript` nested object) vào event. Lý do:

- Transcript text thường 200–2000 chars → payload Kafka tăng ~1–4KB, không đáng kể.
- Bỏ được 1 RTT trên mọi answer.
- Event self-contained — replay safer (không phụ thuộc tts-stt up).

**Triển khai:**

1. Phía `tts-stt-service`:
   ```java
   // TranscriptReadyEvent.java
   String transcriptText;   // thêm field
   ```
   Trong `SttOrchestrator` khi publish, set `transcriptText = scribeResult.text()`.

2. Phía `interview-service`:
   ```java
   // message/event/TranscriptReadyEvent.java
   public record TranscriptReadyEvent(
           String eventId, String eventType,
           OffsetDateTime occurredAt,
           String transcriptId, String answerId,
           String sessionId, String languageCode,
           Integer durationMs,
           String transcriptText   // thêm
   ) {}
   ```

3. Trong `AnswerService.applyTranscriptReady`:
   - Đổi signature nhận thêm `transcriptText`:
     ```java
     public void applyTranscriptReady(
             UUID answerId, UUID transcriptId, String languageCode, String transcriptText) {
         ...
         // Bỏ ttsSttAdapter.getTranscript(...)
         // Dùng transcriptText trực tiếp.
     }
     ```
   - Nếu `transcriptText == null` (backward-compat khi replay event cũ), fallback gọi `ttsSttAdapter.getTranscript`.

**Tác động:** cắt 1 RTT × 100% answers. Trung bình 100ms.

**Risk:** payload Kafka lớn hơn. Confirm `max.message.bytes` đủ (default 1MB là dư cho transcript < 10KB).

### 4.3 Snapshot user profile vào session khi /start

**Vấn đề:** `UserProfileResponse` được fetch qua REST tại:

- `SessionService.start:101` — khi /start (đúng).
- `AnswerService.applyTranscriptReady:337` — cho `preferredLanguage` (RTT thừa).
- `AnswerService.handleMoveNext:584` — cho `excludeIds` build + tag bias (RTT thừa).
- `AnswerService.handleFollowUp:560` — cho language (RTT thừa).

→ 3 RTT × 50–80ms = ~200ms wasted, mỗi answer.

Profile thay đổi cực ít trong scope 1 session ~20 phút.

**Giải pháp:** lưu blob profile vào `InterviewSession.metadata.profileSnapshot` tại `/start`. Đọc lại từ session khi cần.

**Triển khai:**

1. `entity/InterviewSession` — `metadata` đã là `Map<String, Object>` (jsonb), không cần migration.

2. `SessionService.start` — sau khi load profile, store:
   ```java
   Map<String, Object> meta = session.getMetadata() != null
           ? new HashMap<>(session.getMetadata())
           : new HashMap<>();
   meta.put("profileSnapshot", objectMapper.convertValue(profile, Map.class));
   meta.put("profileSnapshotAt", OffsetDateTime.now().toString());
   session.setMetadata(meta);
   ```

3. Helper trong `AnswerService` (hoặc thành dedicated `SessionProfileLoader` service):
   ```java
   private UserProfileResponse loadProfile(InterviewSession session) {
       if (session.getMetadata() != null) {
           Object snap = session.getMetadata().get("profileSnapshot");
           if (snap instanceof Map<?, ?> m) {
               return objectMapper.convertValue(m, UserProfileResponse.class);
           }
       }
       // Fallback: pre-existing session không có snapshot, refresh từ REST + lưu lại.
       UserProfileResponse fresh = userProfileAdapter.getProfile(session.getUserId());
       // (optional) write-back to session.metadata for next time
       return fresh;
   }
   ```

4. Thay 3 chỗ gọi `userProfileAdapter.getProfile(...)` (`applyTranscriptReady:337`, `handleFollowUp:560`, `handleMoveNext:584`) bằng `loadProfile(session)`.

**Tác động:** ~200ms tiết kiệm mỗi answer + giảm tải user-profile-service.

**Risk:** nếu user cập nhật `preferredLanguage` giữa session, language hint bị stale. Hệ quả nhỏ — AI vẫn trả về language hợp lý dựa trên STT detected. Có thể chấp nhận.

### 4.4 Gộp question-bank `/filter` + `/snapshot`

**Vấn đề:** `QuestionPicker.pickForTopic:91-104` gọi:

1. `questionBankAdapter.filter(req)` — trả list candidate (thin).
2. `questionBankAdapter.getSnapshotSoft(chosen.id())` — fetch rich snapshot.

2 RTT sequential cho cùng câu hỏi.

**Giải pháp:** mở rộng `/filter` để optionally trả full snapshot cho top-K candidate.

**Phía question-bank-service** (tham khảo):

```java
// QuestionFilterRequest
boolean includeRichSnapshot;
int richSnapshotTopK;   // default 1
```

```java
// QuestionFilterResponse
List<QuestionCandidate> candidates;
// thêm:
Map<String, QuestionSnapshotResponse> richSnapshots;   // key = candidate.id
```

**Phía interview-service** (`QuestionPicker.pickForTopic`):

```java
QuestionFilterRequest req = new QuestionFilterRequest(
        ...,
        /* includeRichSnapshot */ true,
        /* richSnapshotTopK */ 1);
QuestionFilterResponse res = questionBankAdapter.filter(req);
QuestionCandidate chosen = applyLocalScoring(...);
QuestionSnapshotResponse rich = res.richSnapshots() != null
        ? res.richSnapshots().get(chosen.id())
        : null;
// nếu rich null (fallback), gọi getSnapshotSoft như cũ
```

**Tác động:** -1 RTT mỗi `MoveToNextTopic` (~50–100ms).

**Note:** với `applyLocalScoring`, candidate được chọn từ top-K từ filter — `richSnapshotTopK = 1` đủ vì score order đã ổn định. Nếu local scoring có thể đảo (do tag bias), tăng `topK` = 3–5.

### 4.5 `markAskedSoft` chạy async thực sự

**Vấn đề:** `QuestionBankAdapter.markAskedSoft:69` swallow exception nhưng vẫn **block thread hiện tại**. RTT 50–150ms nằm trong consumer thread hoặc /start path.

**Giải pháp:** chạy fire-and-forget thực sự.

**Cách 1 — `@Async` Spring** (đơn giản):

```java
// Enable trên Application class
@EnableAsync

// QuestionBankAdapter.java
@Async("questionBankExecutor")
public void markAskedAsync(String questionId) {
    try {
        client.markAsked(questionId);
    } catch (Exception ex) {
        log.warn("mark-asked failed for question {}: {}", questionId, ex.getMessage());
    }
}
```

Bean executor:

```java
@Bean(name = "questionBankExecutor")
public Executor questionBankExecutor() {
    ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
    e.setCorePoolSize(2);
    e.setMaxPoolSize(4);
    e.setQueueCapacity(100);
    e.setThreadNamePrefix("qb-mark-asked-");
    return e;
}
```

**Cách 2 — outbox event** (an toàn hơn, đồng bộ với phong cách "all writes via outbox"):

Thay vì REST call, stage event `MARK_ASKED { questionId, ts }`. Question-bank consumer xử lý. Trade-off: thêm topic + consumer, nhưng nhất quán với pattern outbox; nếu QB down, event tích lũy và replay sau khi up.

**Khuyến nghị:** cách 1 cho counter bump (không quan trọng nếu mất); cân nhắc cách 2 nếu cần audit trail nghiêm ngặt.

**Tác động:** -1 RTT trên path `pickForTopic` (50–150ms).

### 4.6 Bỏ storage check đồng bộ trên `/submit`

**Vấn đề:** `AnswerService.submit:128` gọi `storageAdapter.getObject(...)` cho VIDEO answer trước khi insert. RTT 80–200ms nằm trong response time `/submit`.

**Mục đích check hiện tại:** verify ownership, kind, READY status. Tránh tạo answer trỏ tới object lỗi.

**Giải pháp 1 — defer xuống consumer:**

- `/submit` chỉ validate `storageObjectId != null` và `type == VIDEO`. Insert answer ngay, status `PROCESSING`.
- Trong stage outbound (`stageOutbound`), payload chỉ chứa `storageObjectId` (không cần `videoMeta` ngay — tts-stt sẽ presign-by-id).
- tts-stt consumer download → nếu storage object không tồn tại / wrong kind / wrong owner → publish `transcript-failed` với error code → `applyTranscriptFailed` flip answer FAILED.

Pros:
- `/submit` p99 cắt 100–200ms.
- Tts-stt anyway phải nói chuyện với storage để presign — không thêm RTT.

Cons:
- Bad request không reject sớm; user thấy "Đang xử lý..." rồi mới biết fail.
- Error reporting trễ ~3–5s thay vì 200ms.

**Giải pháp 2 — signed token từ storage:**

Khi FE upload xong, storage trả về `storageObjectId` kèm short-lived JWT `{ objectId, ownerUserId, kind, status, exp }` ký bằng shared secret. FE gửi token này lên cùng `/submit`. Interview-service verify offline (HMAC), không cần REST.

Pros:
- Verify offline (~10µs).
- Vẫn fail nhanh nếu token sai.

Cons:
- Thêm shared secret + key rotation logic.
- Storage phải sửa endpoint upload-complete.

**Khuyến nghị:** giải pháp 1 đủ cho hầu hết case; UX trade-off chấp nhận được vì 200ms savings là đáng giá so với 3–5s STT chờ phía sau anyway.

### 4.7 Parallel hoá trong `handleMoveNext`

**Vấn đề:** trong `AnswerService.handleMoveNext:583-588`:

```java
UserProfileResponse profile = userProfileAdapter.getProfile(...);          // REST, ~50ms
List<String> excludeIds = sessionQuestionRepo.findBySession...(...);       // DB, ~10ms
SessionQuestion next = questionPicker.pickForTopic(...);                   // REST chain
```

`getProfile` và `findBySession...` độc lập nhưng chạy sequential.

**Giải pháp:** sau khi áp dụng §4.3 (snapshot profile), `getProfile` không còn REST → vấn đề này tự tan. Nếu vì lý do gì vẫn cần REST:

```java
CompletableFuture<UserProfileResponse> profileF = CompletableFuture.supplyAsync(
        () -> userProfileAdapter.getProfile(session.getUserId()), executor);
List<String> excludeIds = sessionQuestionRepo.findBySession...(...);
UserProfileResponse profile = profileF.join();
```

**Tác động:** ~30–50ms nếu chưa làm §4.3.

### 4.8 Bật connection pool keep-alive cho Feign

**Vấn đề:** Feign mặc định dùng `HttpURLConnection`. Pooling hạn chế, không persistent connection. Mỗi REST call mất 1–5ms TCP setup + TLS handshake nếu HTTPS.

**Giải pháp:** add OkHttp hoặc Apache HC5.

**OkHttp (đơn giản nhất):**

```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-okhttp</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  cloud:
    openfeign:
      okhttp:
        enabled: true
```

Tự động enable connection pool (default 5 idle, keep-alive 5min). Có thể tune:

```java
@Bean
public okhttp3.OkHttpClient okHttpClient() {
    return new okhttp3.OkHttpClient.Builder()
            .connectionPool(new okhttp3.ConnectionPool(50, 5, java.util.concurrent.TimeUnit.MINUTES))
            .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build();
}
```

**Tác động:** -5 đến -20ms × N REST call (7–10 call mỗi answer ⇒ tích 35–200ms). Đây là **~80% lợi ích gRPC** với 1 dependency.

---

## 5. Đề xuất phase 2 — gRPC migration có chọn lọc

### 5.1 Vì sao gRPC giúp (và không giúp) ở đây

**Giúp:**

- HTTP/2 multiplex + persistent stream → eliminate per-call connection setup.
- Protobuf binary frame nhỏ hơn JSON ~30–50% → giảm CPU serialize/deserialize.
- Strongly-typed contract → compile-time check thay vì DTO drift.
- Hỗ trợ streaming nếu sau này cần (e.g. live transcript stream).

**Không giúp ở scope hiện tại:**

- Service đang chạy cùng host (Contabo VPS) → network latency vốn đã <1ms. Saving 5–20ms/call so với cost migration → ROI vừa phải.
- AI service là Python với LLM call lớn (4–12s) → 5ms transport saving không cảm nhận được.
- Outbox/Kafka không bị ảnh hưởng — vẫn là async event.

### 5.2 Phạm vi migrate đề nghị

**Nên migrate (high frequency, low payload, hot path):**

| Cặp | Lý do |
|---|---|
| interview → storage  `GetObject`, `CreateVideoDownloadUrl` | RTT/lượt cao, payload nhỏ |
| interview → user-profile `GetProfile` | Sẽ giảm khi áp §4.3, nhưng vẫn cần fresh fetch ở /start |
| interview → question-bank `Filter`, `GetSnapshot`, `GetFollowUps`, `MarkAsked` | Trên hot path, 3–4 call/answer |
| interview → tts-stt `GetTranscript` | Bỏ luôn nếu áp §4.2 |

**KHÔNG migrate:**

- interview → AI service. LLM nuốt hết latency; Python team không sẵn sàng gRPC. REST + JSON đủ.
- Public endpoint (FE → interview-service). Browser/JS không thân thiện gRPC; giữ REST hoặc gRPC-Web.
- Outbox/Kafka. Đã là async, không liên quan.

### 5.3 Mapping REST → gRPC (proto skeleton)

Phác thảo `.proto` cho 3 service. Đặt trong module `services/proto/` (mới).

```protobuf
// proto/storage/v1/storage.proto
syntax = "proto3";
package mockwise.storage.v1;

option java_multiple_files = true;
option java_package = "com.mockwise.storage.grpc.v1";

import "google/protobuf/timestamp.proto";

service StorageService {
  rpc GetObject(GetObjectRequest) returns (StorageObjectResponse);
  rpc CreateVideoDownloadUrl(CreateVideoDownloadUrlRequest) returns (PresignedUrlResponse);
  rpc CreateDownloadUrl(CreateDownloadUrlRequest) returns (PresignedUrlResponse);
}

message GetObjectRequest {
  string object_id = 1;   // UUID string
}

message StorageObjectResponse {
  string id = 1;
  string bucket = 2;
  string object_key = 3;
  string kind = 4;             // INTERVIEW_VIDEO | QUESTION_AUDIO | ...
  string status = 5;           // READY | PENDING | FAILED
  string owner_user_id = 6;
  string content_type = 7;
  int64 size_bytes = 8;
  google.protobuf.Timestamp created_at = 9;
}

message CreateVideoDownloadUrlRequest {
  string object_id = 1;
  string requester_user_id = 2;
  int32 ttl_seconds = 3;       // 0 = use server default
}

message PresignedUrlResponse {
  string url = 1;
  google.protobuf.Timestamp expires_at = 2;
}
```

```protobuf
// proto/userprofile/v1/userprofile.proto
syntax = "proto3";
package mockwise.userprofile.v1;

option java_package = "com.mockwise.userprofile.grpc.v1";
option java_multiple_files = true;

service UserProfileService {
  rpc GetProfile(GetProfileRequest) returns (UserProfileResponse);
}

message GetProfileRequest { string user_id = 1; }

message UserProfileResponse {
  string user_id = 1;
  string preferred_language = 2;
  PositionResponse position = 3;
  int32 experience = 4;
  int32 years_in_current_role = 5;
  repeated string tech_stack = 6;
  repeated string industries = 7;
}

message PositionResponse {
  string track_name = 1;
  string level_name = 2;
}
```

```protobuf
// proto/questionbank/v1/questionbank.proto
syntax = "proto3";
package mockwise.questionbank.v1;

option java_package = "com.mockwise.questionbank.grpc.v1";
option java_multiple_files = true;

service QuestionBankService {
  rpc Filter(FilterRequest) returns (FilterResponse);
  rpc GetSnapshot(GetSnapshotRequest) returns (QuestionSnapshotResponse);
  rpc GetFollowUps(GetFollowUpsRequest) returns (GetFollowUpsResponse);
  rpc MarkAsked(MarkAskedRequest) returns (MarkAskedResponse);
}

message FilterRequest {
  string type = 1;
  string competency = 2;
  string domain = 3;
  string target_role = 4;
  string difficulty = 5;
  repeated string tag_bias = 6;
  repeated string exclude_ids = 7;
  bool require_opener = 8;
  int32 limit = 9;
  bool include_rich_snapshot = 10;
  int32 rich_snapshot_top_k = 11;
}

message FilterResponse {
  repeated QuestionCandidate candidates = 1;
  map<string, QuestionSnapshotResponse> rich_snapshots = 2;
}

message QuestionCandidate {
  string id = 1;
  string type = 2;
  string difficulty = 3;
  string text = 4;
  string audio_key = 5;
  repeated string tags = 6;
  string competency = 7;
  string domain = 8;
  int32 ask_count = 9;
  bool is_opener = 10;
}

message QuestionSnapshotResponse {
  string id = 1;
  string type = 2;
  string difficulty = 3;
  string text = 4;
  string audio_key = 5;
  repeated string tags = 6;
  string competency = 7;
  repeated string expected_signals = 8;
  string domain = 9;
  repeated string key_concepts = 10;
  string depth_expected = 11;
  repeated string target_roles = 12;
  string title = 13;
  string description = 14;
  int32 time_limit_minutes = 15;
  // ... live-coding fields
  google.protobuf.Timestamp snapshot_at = 16;
}
```

### 5.4 Áp dụng phía client (interview-service)

Stack: `grpc-spring-boot-starter` (LogNet hoặc Yidongnan fork) hoặc raw `grpc-java`.

**Maven:**

```xml
<dependency>
  <groupId>net.devh</groupId>
  <artifactId>grpc-spring-boot-starter</artifactId>
  <version>3.1.0.RELEASE</version>
</dependency>
<dependency>
  <groupId>io.grpc</groupId>
  <artifactId>grpc-netty-shaded</artifactId>
</dependency>
```

**Build proto:** dùng `protobuf-maven-plugin` để gen Java code (đặt module `services/proto` rồi sub-modules import).

**Code skeleton:**

```java
// new: client/storage/StorageGrpcClient.java
@Service
public class StorageGrpcAdapter implements StorageAdapter {

    @GrpcClient("storage-service")
    private StorageServiceGrpc.StorageServiceBlockingStub stub;

    @Override
    public StorageObjectResponse getObject(UUID objectId) {
        try {
            var resp = stub.getObject(GetObjectRequest.newBuilder()
                    .setObjectId(objectId.toString()).build());
            return StorageMapper.toDomain(resp);
        } catch (StatusRuntimeException ex) {
            throw GrpcErrorMapper.toBusinessException(ex, StatusCode.STORAGE_UNAVAILABLE);
        }
    }
    // ... other methods
}
```

`application.yml`:

```yaml
grpc:
  client:
    storage-service:
      address: 'static://storage-service:9090'
      negotiation-type: plaintext   # nội bộ, không TLS
      keep-alive-time: 30s
      keep-alive-timeout: 10s
    user-profile-service:
      address: 'static://user-profile-service:9090'
      negotiation-type: plaintext
    question-bank-service:
      address: 'static://question-bank-service:9090'
      negotiation-type: plaintext
```

### 5.5 Áp dụng phía server

Mỗi service đích cần expose gRPC port song song với REST.

```java
// storage-service: src/main/java/.../grpc/StorageGrpcService.java
@GrpcService
public class StorageGrpcService extends StorageServiceGrpc.StorageServiceImplBase {

    private final StorageObjectRepository repo;

    @Override
    public void getObject(GetObjectRequest request, StreamObserver<StorageObjectResponse> response) {
        var obj = repo.findById(UUID.fromString(request.getObjectId()))
                .orElseThrow(() -> Status.NOT_FOUND.withDescription("object not found").asRuntimeException());
        response.onNext(StorageMapper.toProto(obj));
        response.onCompleted();
    }
    // ...
}
```

`application.yml`:

```yaml
grpc:
  server:
    port: 9090
    enable-keep-alive: true
    keep-alive-time: 30s
```

### 5.6 Auth + error mapping

**Auth:**

REST hiện inject header `X-Internal-Auth`. gRPC tương đương dùng `Metadata`:

```java
// ClientInterceptor (interview-service)
@Component
public class InternalAuthClientInterceptor implements ClientInterceptor {
    @Value("${internal.auth.api-key}") String apiKey;

    static final Metadata.Key<String> AUTH_KEY = Metadata.Key.of("x-internal-auth", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(AUTH_KEY, apiKey);
                super.start(responseListener, headers);
            }
        };
    }
}
```

Phía server: `ServerInterceptor` validate header, throw `Status.UNAUTHENTICATED` nếu sai.

**Error mapping:**

| REST `ApiResponse` | gRPC `Status` |
|---|---|
| code 404, success=false | `Status.NOT_FOUND` |
| code 400, success=false | `Status.INVALID_ARGUMENT` |
| code 401 | `Status.UNAUTHENTICATED` |
| code 403 | `Status.PERMISSION_DENIED` |
| code 409 | `Status.ALREADY_EXISTS` / `FAILED_PRECONDITION` |
| code 5xx | `Status.INTERNAL` / `UNAVAILABLE` |

Helper:

```java
// common/grpc/GrpcErrorMapper.java
public class GrpcErrorMapper {
    public static BusinessException toBusinessException(StatusRuntimeException ex, StatusCode fallback) {
        var status = ex.getStatus().getCode();
        return switch (status) {
            case NOT_FOUND -> new BusinessException(fallback, "not found", 404);
            case INVALID_ARGUMENT -> new BusinessException(StatusCode.VALIDATION_ERROR);
            case UNAUTHENTICATED -> new BusinessException(StatusCode.UNAUTHORIZED);
            case PERMISSION_DENIED -> new BusinessException(StatusCode.FORBIDDEN);
            default -> new BusinessException(fallback);
        };
    }
}
```

### 5.7 Rollout strategy — chạy song song REST/gRPC

**Quan trọng:** không big-bang. Migrate từng cặp, có feature flag.

1. Triển khai gRPC server bên service đích, REST giữ nguyên.
2. Trong interview-service tạo `StorageGrpcAdapter` implements `StorageAdapter`.
3. Feature flag `interview.transport.storage` = `rest` | `grpc`. `@ConditionalOnProperty` chọn bean.
4. Deploy mặc định `rest`. Bật `grpc` cho 1 môi trường staging, đo metric.
5. Switch prod. Giữ rollback 1 tuần.
6. Sau ổn → xoá REST adapter (hoặc giữ nếu là contract public).

Thứ tự khuyến nghị (rủi ro tăng dần):

1. **user-profile** — surface nhỏ nhất (1 RPC).
2. **storage** — 2 RPC, dễ.
3. **question-bank** — 4 RPC, hot path; làm cuối.

---

## 6. Đề xuất phase 3 — chuyển polling sang push (SSE/WebSocket)

**Vấn đề:** sau phase 1+2, latency E2E giảm ~2–3s. Phần còn lại "không cứu được trong server" là **polling cadence của FE** (~1–2s).

**Giải pháp:** SSE endpoint `GET /api/v1/interviews/{sid}/events` (stream). Khi:

- Answer status đổi (PROCESSING → EVALUATING → SCORED → ...) → push `AnswerStatusEvent`.
- Câu mới pin → push `NewQuestionEvent` với `PinnedQuestionView`.
- Session terminal → push `SessionScoredEvent`.

**Triển khai gợi ý:**

- Spring WebFlux SSE `Flux<ServerSentEvent<?>>` hoặc Servlet SseEmitter.
- In-memory `Sinks.Many<...>` per-session. Listener pattern: khi `answerRepo.save(...)` hoặc `sessionQuestionRepo.save(...)` thành công, publish vào sink. (Có thể dùng Spring `@EventListener` + `TransactionalEventListener(phase = AFTER_COMMIT)`.)
- Cluster: nếu chạy nhiều replica, push qua Redis pub/sub hoặc Kafka internal topic để mỗi instance route đúng SSE connection.

**Trade-off:** thêm complexity (state per-connection); FE phải xử lý reconnect; cluster routing. Chỉ làm nếu phase 1+2 chưa đủ.

---

## 7. Observability — đo đạc trước/sau

**Trước khi sửa:** confirm con số dự đoán bằng metric thực tế.

1. **Bật Micrometer + OpenTelemetry tracing.** Mỗi REST call qua Feign tự động instrument; thêm tag `peer.service`.
2. **Custom timer trong stage hot path:**
   ```java
   Timer.Sample sample = Timer.start(meterRegistry);
   // ... work
   sample.stop(meterRegistry.timer("interview.answer.submit", "phase", "storage_check"));
   ```
3. **Outbox lag gauge:** `select MAX(NOW() - created_at) from outbox_event where published = false` exposed qua `/actuator/metrics`.
4. **Histogram tổng E2E:** `answer.e2e.latency` = (scoredAt - submittedAt). Đo từ DB query định kỳ.

**Sau khi sửa:** compare p50 / p95 / p99 trên Grafana board. Tiêu chí accept:

- Outbox lag p99 < 200ms.
- `/submit` response p99 < 100ms (sau §4.6).
- `applyTranscriptReady → publish evaluation-requested` p99 < 50ms ngoài DB Tx.
- E2E p99 giảm ≥ 1.5s.

---

## 8. Test plan

### Unit / integration

- `AnswerService.applyTranscriptReady` với event mang `transcriptText` — không gọi `ttsSttAdapter`.
- `AnswerService.applyTranscriptReady` với event không có `transcriptText` (backward-compat) — gọi adapter.
- `SessionService.start` — sau khi gọi → `session.metadata.profileSnapshot != null`.
- `loadProfile(session)` — đọc từ metadata khi có; fallback REST khi thiếu.
- `QuestionPicker.pickForTopic` — khi response `filter` có `richSnapshots[chosen.id]` → không gọi `getSnapshot`.
- `markAskedAsync` — `@Async` thread, không block caller; exception swallow.

### E2E

- 1 user, 5 câu hỏi liên tiếp, đo latency mỗi câu. So sánh trước/sau.
- Chaos: tắt question-bank 3s giữa flow → check answer FAILED không xảy ra, follow-up AI fallback chạy.
- Chaos: tắt user-profile 3s sau khi snapshot → check next answer dùng snapshot, không fail.

### Load

- 50 user nộp song song. Đo outbox lag, Kafka consumer lag, DB connection pool.
- Sau phase 1: confirm không vỡ DB connection pool do `poll-interval-ms = 100`.

---

## 9. Rủi ro & rollback

| Risk | Mức | Mitigation |
|---|---|---|
| `poll-interval-ms = 100` tăng DB load | Thấp | Partial index đã có; rollback bằng env var ngay |
| Transcript text trong event vượt Kafka max | Thấp | Confirm < 10KB typical; `max.message.bytes` 1MB |
| Profile snapshot stale → language sai | Thấp | UX có thể chấp nhận; thêm "refresh profile" button nếu phàn nàn |
| QB `/filter` thêm field `includeRichSnapshot` break backward-compat | Trung | Optional field, default false. Old client không bị ảnh hưởng |
| `@Async` markAsked tràn queue khi QB chậm | Thấp | Bounded `queueCapacity = 100`; `CallerRunsPolicy` để fallback |
| Bỏ storage check → answer trỏ object xấu | Trung | Tts-stt phát hiện ở download; `transcript-failed` flip answer FAILED. FE thấy lỗi sau ~3s thay vì 200ms |
| gRPC channel rò rỉ resource | Trung | Dùng grpc-spring-boot-starter quản lý lifecycle; health check qua `grpc.health.v1.Health` |
| gRPC + REST song song → drift contract | Trung | Sinh DTO Java từ proto + REST DTO map qua mapper; CI assert mapper round-trip |

Rollback:

- Mọi config (§4.1, §4.6) qua env var → 1 lệnh deploy revert.
- Code (§4.2, §4.3, §4.4) qua feature flag `interview.optimization.*.enabled` để A/B.
- gRPC qua flag `interview.transport.<svc>` = `rest` | `grpc`.

---

## 10. Phụ lục — file references

Mọi đường dẫn relative tới `services/`.

**Hot path classes:**

- `interview-service/src/main/java/com/mockwise/interview/controller/AnswerController.java`
- `interview-service/src/main/java/com/mockwise/interview/service/AnswerService.java`
  - `submit` — line 104
  - `applyTranscriptReady` — line 291
  - `applyEvaluationCompleted` — line 460
  - `handleFollowUp` — line 546
  - `handleMoveNext` — line 583
- `interview-service/src/main/java/com/mockwise/interview/service/QuestionPicker.java`
  - `pickForTopic` — line 80
  - `pickFollowUpFromBank` — line 213
  - `pickFollowUpFromAi` — line 253
- `interview-service/src/main/java/com/mockwise/interview/message/publisher/OutboxPoller.java`
- `interview-service/src/main/java/com/mockwise/interview/message/consumer/TranscriptConsumer.java`
- `interview-service/src/main/java/com/mockwise/interview/message/consumer/EvaluationConsumer.java`

**Adapters (REST hiện tại):**

- `interview-service/src/main/java/com/mockwise/interview/client/storage/StorageAdapter.java`
- `interview-service/src/main/java/com/mockwise/interview/client/storage/StorageClient.java` (Feign)
- `interview-service/src/main/java/com/mockwise/interview/client/userprofile/UserProfileAdapter.java`
- `interview-service/src/main/java/com/mockwise/interview/client/userprofile/UserProfileClient.java`
- `interview-service/src/main/java/com/mockwise/interview/client/questionbank/QuestionBankAdapter.java`
- `interview-service/src/main/java/com/mockwise/interview/client/questionbank/QuestionBankClient.java`
- `interview-service/src/main/java/com/mockwise/interview/client/ttsstt/TtsSttAdapter.java`
- `interview-service/src/main/java/com/mockwise/interview/client/ttsstt/TtsSttClient.java`

**Feign config:**

- `interview-service/src/main/java/com/mockwise/interview/client/questionbank/config/QuestionBankFeignConfig.java` (template cho các service khác)

**Event schemas (consumer side):**

- `interview-service/src/main/java/com/mockwise/interview/message/event/TranscriptReadyEvent.java`
- `interview-service/src/main/java/com/mockwise/interview/message/event/EvaluationCompletedEvent.java`

**Event schemas (producer side, tts-stt):**

- `tts-stt-service/src/main/java/com/mockwise/ttsstt/stt/kafka/event/TranscriptReadyEvent.java`
- `tts-stt-service/src/main/java/com/mockwise/ttsstt/stt/service/SttOrchestrator.java`

**Config files:**

- `interview-service/src/main/resources/application.yml`
  - `interview.outbox.poll-interval-ms` — line 61

**Tài liệu liên quan (đã có sẵn):**

- `interview-service/docs/interview-service-design.md` — tổng quan service.
- `interview-service/docs/question-selection-design.md` — luồng pick câu hỏi.
- `interview-service/docs/start-session-first-question-flow.md` — chi tiết /start.
