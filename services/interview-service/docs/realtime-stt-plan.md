# Lever 2 — Real-time STT + tách video upload khỏi critical path

> Trạng thái: PR1 (backend) + PR2 (frontend) ĐÃ CODE 2026-06-17 (flag mặc định
> OFF). **Nguồn STT realtime đã chốt = Phương án C (ElevenLabs Scribe v2
> Realtime, client-side) — xem §5; Phương án A (Web Speech) + spike §8.bis đã
> BỎ.** PR3 (reconcile/admin UI) chưa làm.
> Tác giả: đo đạc 2026-06-16.
> Liên quan: [interview-service-design.md](./interview-service-design.md),
> tts-stt-service, AI/evaluator, frontend `VideoRecorder.tsx`.
>
> **Provider C (ElevenLabs realtime) — vòng 3, 2026-06-17:** tts-stt
> `RealtimeSttTokenClient` mint single-use token (`POST
> /v1/single-use-token/realtime_scribe`) + internal `POST
> /internal/realtime-stt-token` (config `elevenlabs.realtime-stt-model:
> scribe_v2_realtime`); interview `TtsSttAdapter.mintRealtimeToken` + relay
> `POST /api/v1/interviews/realtime-stt/token` (auth, flag-gated →
> `REALTIME_STT_DISABLED` khi OFF); FE `realtimeTranscriber.ts` viết lại sang
> ElevenLabs WS + AudioContext PCM16/16k tee từ stream record (bỏ Web Speech),
> `getRealtimeSttToken` API, test FE 7 case (helper thuần: PCM, base64,
> appendCommitted, gate). Flag FE `VITE_REALTIME_STT_ENABLED` + (tùy chọn)
> `VITE_ELEVENLABS_STT_WS_URL`.
>
> **Đã triển khai (PR1 backend):** migration `V7__answer_realtime_transcript.sql`;
> `Answer` + `SubmitAnswerInput` thêm cột/field transcript; `AnswerService.submit`
> nhánh fast-path (`submitWithRealtimeTranscript` → EVALUATING thẳng) + refactor
> `stageSpokenEvaluation` dùng chung; `attachVideo` + endpoint
> `POST /{sid}/answers/{aid}/attach-video` (bỏ guard time-budget/IN_PROGRESS,
> idempotent); `applyTranscriptReady` switch-by-status (EVALUATING|SCORED →
> `attachAuthoritativeTranscript`, không re-score); `extractTranscript` đọc cột
> `realtime_transcript`/`authoritative_transcript`. Flag
> `interview.realtime-stt.enabled` (env `INTERVIEW_REALTIME_STT_ENABLED`).
> Test: `AnswerServiceRealtimeSttTest` (6 case race/fast-path, xanh).
>
> **Đã triển khai (PR2 frontend):** `src/lib/realtimeTranscriber.ts` (Web Speech,
> chỉ gom `isFinal`, auto-restart, flush window 800ms); `VideoRecorder` chạy
> transcriber song song MediaRecorder + đổi `onSubmit` sang `RecorderSubmission`
> (không await upload); `PracticeSessionPage` submit transcript NGAY rồi
> `attachVideoInBackground`; `api/interviews.attachVideo`. Flag FE
> `VITE_REALTIME_STT_ENABLED` (phải bật song song với flag backend).
>
> **Đã làm thêm (vòng 2, 2026-06-17):**
> - §8.1#4 — `AnswerView.transcript` (realtime||authoritative, chỉ lộ khi SCORED) +
>   render trên report (`PracticeQuestionsPage` AnswerDetail, card "Nội dung câu
>   trả lời (đã dùng để chấm)").
> - §8.4#11 — fast-path submit IDEMPOTENT: retry trả về `answerId` cũ (không 409,
>   không tạo row 2) để FE attach đúng answer.
> - §8.3#8 — thêm guardrail chống prompt-injection quanh transcript trong
>   `AI/evaluator/prompts/behavioral.py` + `conceptual.py` (transcript = untrusted
>   data; LLM gọi single `user` message, KHÔNG có system role tách riêng).
> - §8.4#10 — storage-service `PendingUploadReaper` (@Scheduled) dọn orphan
>   PENDING_UPLOAD quá hạn + part MinIO (`MinioStorageGateway.listObjectKeys`,
>   `findByStatusAndCreatedAtBefore`, `@EnableScheduling`); config `storage.reaper.*`.
> - §11 — test FE: thêm vitest+jsdom, `realtimeTranscriber.test.ts` (8 case);
>   test BE lên 7 case (thêm idempotent retry).
>
> ⚠️ **Chưa làm / cần trước khi bật prod (đã giảm sau khi chốt Provider C):**
> ~~Spike §8.bis mic dùng chung~~ — KHÔNG còn cần (ElevenLabs tee từ 1 stream).
> ~~Đo chất lượng VN §12.2~~ — hết là blocker (cùng Scribe đã dùng cho batch).
> CÒN LẠI: **smoke-test thực tế** (bật 2 flag ở staging, nói thử 1 câu Chrome,
> kiểm transcript ra + audio video ok); **chi phí** realtime theo phút (quyết
> định business); ngôn ngữ transcriber hardcode `vi` (prop `language` mặc định,
> chưa nối profile); PR3 màn admin xem mismatch (cờ `transcript_mismatch` đã
> tính, UI chưa có); re-score qua flag (§8.1#3, optional); test FE mức component
> (VideoRecorder/submit-split) còn manual (mới test helper transcriber thuần).

## 1. Bối cảnh — số đo thực tế trên VPS (2026-06-16)

Đo timing 3 câu behavioral liên tiếp (xem các log `TIMING …`). Tỷ trọng
thời gian user chờ **rất ổn định**:

| Chặng | Câu 1 (83 MB) | Câu 2 (44 MB) | Câu 3 (48 MB) | Tỷ trọng |
|---|---|---|---|---|
| **Upload video** (FE wall-clock sau "stop") | 124.6 s | 81.1 s | 81.7 s | **~75%** |
| └ compose trên MinIO (server) | 1.6 s | 0.6 s | 0.6 s | — |
| **STT total** (download+ffmpeg+Scribe) | 31.4 s | 15.5 s | 13.9 s | **~15%** |
| **AI chấm (Gemini)** | 9.9 s | 8.5 s | 9.3 s | **~7%** |
| **e2e submit→scored** | 43.5 s | 24.6 s | 23.6 s | — |
| **TỔNG user chờ** | ~168 s | ~106 s | ~105 s | — |

Kết luận: **upload là thủ phạm chính, AI là chặng nhỏ nhất.**

- **Lever 1 (ĐÃ LÀM, commit `ca61739`)**: cap bitrate recorder 1.2 Mbps + 24 fps
  → file ~6× nhỏ hơn → upload + download + ffmpeg đều giảm theo. Đây là quick
  win nhưng **upload vẫn nằm trên critical path** — user vẫn phải chờ upload
  xong mới được chấm.
- **Lever 2 (tài liệu này)**: đưa **upload RA KHỎI** đường chờ và thay STT
  batch bằng STT real-time. Mục tiêu: user chờ ≈ chỉ còn AI (~9 s).

## 2. Mục tiêu & non-goals

**Mục tiêu**
- Sau khi user bấm "Nộp", thời gian tới khi hiện câu kế tiến về **~AI eval (~9 s)**,
  không còn phụ thuộc upload (~75%) lẫn STT batch (~15%).
- Transcript dùng để chấm có sẵn **ngay tại thời điểm submit** (dựng trong lúc nói).
- Video vẫn được lưu đầy đủ để user xem lại — nhưng upload chạy **nền**, không chặn.

**Non-goals**
- Không tối ưu thời gian AI chấm (đã là sàn ~9 s, chấp nhận).
- Không đổi thuật toán planner/adaptive.
- Không bỏ video hẳn — vẫn cần cho phần "xem lại kết quả".

## 3. Pipeline hiện tại (tuần tự — đây là vấn đề)

```
record ──(stream parts during recording)──┐
  │ stop                                   │
  ▼                                        ▼
finish() ── compose parts ── objectId READY   ← user CHỜ upload (75%)
  │
  ▼ POST /answers {storageObjectId}            ← submit yêu cầu object READY
interview: Answer(PROCESSING) → outbox answer-submitted
  │ Kafka
  ▼
tts-stt: download video → ffmpeg → ElevenLabs Scribe (batch)  ← STT (15%)
  │ transcript-ready (Kafka)
  ▼
interview.applyTranscriptReady → outbox evaluation-requested
  │ Kafka
  ▼
AI: Gemini evaluate                                            ← AI (7%)
  │ evaluation-completed (Kafka)
  ▼
interview.applyEvaluationCompleted → planner → (follow-up?) → pin câu kế
```

State machine answer: `SUBMITTED → PROCESSING → READY → EVALUATING → SCORED`.
`storageObjectId` bắt buộc READY tại submit (`verifyStorageObject`).

## 4. Pipeline đích (fast path + background)

```
record ──(real-time STT: partial transcript tích luỹ trong lúc nói)──┐
  │            └──(stream video parts, KHÔNG chờ)                     │
  ▼ stop                                                              ▼
POST /answers { transcriptText, language, durationMs,        transcriptText
                videoUploadId? }  ← KHÔNG cần video READY      sẵn-sàng-NGAY
  │
  ▼ interview: Answer(EVALUATING) → outbox evaluation-requested  ← bỏ qua chặng chờ STT
  │ Kafka
  ▼
AI: Gemini evaluate (~9 s) → evaluation-completed → planner → pin câu kế
                                                       ▲
                                                       │ user chờ ≈ tới đây

— song song, hoàn toàn nền (không ai chờ): —
finish() video upload → compose → objectId READY
  │ Kafka answer-submitted (như cũ)
  ▼
tts-stt: batch STT (Scribe) → transcript-ready
  ▼
interview.attachAuthoritativeTranscript(answerId, batchTranscript)
  → lưu làm bản chính thức để hiển thị + đối chiếu (KHÔNG re-score mặc định)
```

## 5. Quyết định thiết kế — nguồn STT real-time

> ✅ **ĐÃ CHỐT (2026-06-17): Phương án C — ElevenLabs Scribe v2 Realtime.**
> Verify thực tế: ElevenLabs **có** realtime STT (WebSocket, ~150ms, 99+ ngôn
> ngữ gồm VN, client-side streaming với single-use token). Vì mình **đã dùng
> Scribe cho batch** (chất lượng VN đã biết tốt), đi thẳng C **xoá luôn 2 thứ
> từng gate prod**: (1) không còn rủi ro mic dùng chung (§8.bis) vì C nhận PCM
> chunk thủ công → mình **tee chính stream đang record** (một mic, không mở
> capture thứ 2); (2) chất lượng VN hết là ẩn số. Bỏ qua Phương án A (Web Speech)
> và spike §8.bis.

Ba lựa chọn đã cân nhắc:

| Phương án | Độ chính xác VN | Tin cậy server | Chi phí/hạ tầng | Ghi chú |
|---|---|---|---|---|
| **A. Web Speech API (browser)** | Trung bình, phụ thuộc Chrome (gửi audio cho Google) | ❌ client kiểm soát | 0đ, 0 hạ tầng | ~~v1~~ BỎ — rủi ro mic dùng chung + VN chưa chắc |
| **B. WS → provider streaming** (Deepgram / AssemblyAI / Google STT) qua tts-stt | Tốt | ✅ | $ + cần WS proxy server | Dự phòng nếu rời ElevenLabs |
| **C. ElevenLabs realtime (Scribe v2)** ✅ ĐÃ CHỌN | Như Scribe batch (tốt) | ✅ token server-mint | $ theo phút, hạ tầng nhẹ (client-side) | Cùng provider batch; audio tee từ stream record |

**Mô hình triển khai: client-side streaming.**
- Backend (tts-stt) giữ API key, mint **single-use token** (`POST
  /v1/single-use-token/realtime_scribe`, hết hạn ~15’); interview-service relay
  token cho browser qua `POST /api/v1/interviews/realtime-stt/token` (auth +
  flag-gated).
- Browser mở WS `wss://api.elevenlabs.io/v1/speech-to-text/realtime` (token ở
  query), đẩy PCM16/16kHz lấy từ **chính MediaStream đang record** (AudioContext
  tee), gom các message `committed_transcript`.
- Seam `RealtimeTranscriber` (FE) giữ nguyên → đổi provider sau (B) không đụng
  business logic; backend submit/attach/switch **không phụ thuộc nguồn transcript**.

> ⚠️ Quan trọng: real-time transcript có thể rỗng/cụt khi mất mạng / token lỗi /
> WS lỗi → **bắt buộc** fallback về batch STT từ video (xem §8). Init transcriber
> lỗi → transcript rỗng → FE rơi về flow cũ; KHÔNG ném lỗi vào recorder. Đừng lặp
> lại class lỗi "kẹt evaluation" (commit `1a3e79b`, `d5d1d06`).
>
> 💰 **Chi phí**: realtime tính phí theo phút audio (Web Speech miễn phí) — đánh
> đổi đã chấp nhận để có chất lượng VN tin cậy + bỏ spike. Kill-switch tắt phí
> tức thì: flag OFF → không mint token → không có WS.

## 6. Thay đổi theo từng layer

### 6.1 Frontend (`VideoRecorder.tsx`, `api/storage.ts`, `api/interview.ts`)

1. **Bắt transcript real-time** trong lúc recording:
   - Tạo abstraction `createRealtimeTranscriber(language)` trả về `{ push?/auto, stop(): {text, durationMs} }`.
   - v1: bọc `webkitSpeechRecognition` (continuous = true, interimResults = true),
     tích luỹ các `final` segment vào buffer. Ngôn ngữ lấy từ profile (`vi-VN`/`en-US`).
   - Chạy song song với MediaRecorder; cả hai cùng start/stop.
2. **Tách submit khỏi upload**:
   - Hiện tại `finalise()` chờ `uploader.finish()` rồi mới `onSubmit(..., objectId)`.
   - Mới: `onSubmit` được gọi **ngay** với `{ transcriptText, language, durationMs }`
     và `videoUploadId` = objectId *đang upload* (init đã có objectId từ
     `/stream/init`, không cần chờ finish). Upload `finish()` tiếp tục chạy nền;
     khi xong gọi `/answers/{id}/attach-video` (hoặc tái dùng cơ chế hiện có).
   - Nếu real-time transcriber không khả dụng (Safari…) → giữ nguyên flow cũ
     (chờ upload + batch STT). FE gửi cờ `transcriptSource`.
3. Giữ log `[TIMING] upload_finish` để tiếp tục đo (giờ nó nằm ngoài critical path).

### 6.2 interview-service

1. **`SubmitAnswerInput`** thêm field cho VIDEO:
   ```java
   String transcriptText;     // null → đi flow cũ (chờ batch STT)
   String transcriptLanguage; // "vi"/"en"
   Integer transcriptDurationMs;
   String transcriptSource;   // "REALTIME_WEBSPEECH" | null
   // storageObjectId trở thành OPTIONAL cho VIDEO khi có transcriptText
   ```
2. **`AnswerService.submit`** — nhánh mới khi `type==VIDEO && transcriptText != null`:
   - Bỏ ràng buộc `verifyStorageObject` READY (object có thể đang upload). Chỉ verify
     ownership nếu `storageObjectId` được gửi; cho phép null/PENDING.
   - Tạo `Answer(status = EVALUATING)` luôn (thay vì PROCESSING), set
     `transcriptText`/`transcriptSource`.
   - Stage **thẳng** `evaluation-requested` (tái dùng đúng payload builder trong
     `applyTranscriptReady` — refactor phần build payload ra `buildEvaluationRequested(...)`
     để dùng chung, tránh trùng code).
   - **Không** stage `answer-submitted` cho mục đích STT-để-chấm nữa; nhưng vẫn cần
     batch STT chạy nền cho bản chính thức → stage `answer-submitted` **sau khi**
     video READY (qua attach-video), hoặc gắn cờ để tts-stt biết đây là "background
     authoritative only".
3. **Endpoint mới** `POST /answers/{answerId}/attach-video`:
   - FE gọi khi upload `finish()` xong, truyền `storageObjectId`.
   - interview verify object READY + ownership, set `answer.storageObjectId`,
     rồi stage `answer-submitted` → tts-stt chạy batch STT nền.
4. **`applyTranscriptReady`** đổi nghĩa khi answer đã qua EVALUATING/SCORED:
   - ⚠️ **KHÔNG dùng lại early-return `if status != PROCESSING return`** (dòng 442
     hiện tại) — nó sẽ vứt bản authoritative ở fast path (xem §8.2 #5). Phải
     `switch` theo status:
     - `PROCESSING` → legacy: trigger chấm như cũ.
     - `EVALUATING | SCORED` → `attachAuthoritativeTranscript`: lưu `transcript_id` +
       `authoritative_transcript`, phục vụ hiển thị/đối chiếu. **Không re-score**
       mặc định (§8.1 #3 để bật có điều kiện), **không** stage evaluation.
     - `FAILED` → cân nhắc cứu bằng batch (hoặc để operator replay).
5. **State machine** bổ sung đường tắt:
   ```
   (fast path) SUBMITTED → EVALUATING → SCORED
   (legacy)    SUBMITTED → PROCESSING → READY → EVALUATING → SCORED
   ```

### 6.3 tts-stt-service

- Hầu như **không đổi logic** — vẫn nhận `answer-submitted`, batch STT, phát
  `transcript-ready`. Chỉ là consumer phía interview xử lý event đó như "bản chính
  thức nền" thay vì "trigger chấm".
- Tùy chọn: thêm field `purpose: SCORING|AUTHORITATIVE` vào `AnswerSubmittedEvent`
  để rõ ràng (không bắt buộc cho v1).

### 6.4 AI service

- **Không đổi.** Vẫn nhận `evaluation-requested` với cùng schema
  (`BehavioralInput`/`ConceptualInput` có `answer.transcript`). Nguồn transcript
  (real-time vs batch) trong suốt với AI.

## 7. Thay đổi data model

`answers` (interview-service) — migration mới (Flyway V_next):
```sql
ALTER TABLE answers
  ADD COLUMN transcript_source        VARCHAR(32)  NULL,  -- REALTIME_WEBSPEECH | BATCH_SCRIBE
  ADD COLUMN realtime_transcript      TEXT         NULL,  -- bản dùng để chấm (fast path)
  ADD COLUMN authoritative_transcript TEXT         NULL,  -- bản batch từ video (đối chiếu/hiển thị)
  ADD COLUMN transcript_mismatch      BOOLEAN      NULL;  -- set khi reconcile thấy lệch lớn
-- storage_object_id: cho phép NULL khi nộp bằng transcript real-time, attach sau.
```
> Nhớ gotcha trong memory: grep file Flyway trước khi deploy (tránh leak `</content>`
> làm crash migration → prod down).

## 8. Xử lý lỗi & reconciliation (phần dễ sai nhất)

### 8.1 Transcript & điểm số

1. **Real-time transcript rỗng/quá ngắn tại submit**
   - FE tự kiểm: nếu `transcriptText.trim().length < N` (vd < 10 từ) → **không** đi
     fast path; rơi về flow cũ (chờ upload + batch STT). Gửi `transcriptText = null`.
   - Backend cũng phòng thủ: `submit` thấy transcript rỗng dù client gắn cờ realtime
     → coi như legacy (chờ batch).
2. **Upload video thất bại ở nền** (sau khi đã chấm bằng real-time)
   - Answer vẫn SCORED (điểm đã có từ real-time). `storage_object_id` để null,
     phần "xem lại video" hiện "video không khả dụng" — không phá điểm.
3. **Batch STT về sau khác real-time nhiều**
   - Tính chênh lệch đơn giản (độ dài / similarity). Nếu lệch lớn → set
     `transcript_mismatch = true` (để admin xem), **mặc định KHÔNG re-score** (tránh
     điểm "nhảy" sau khi user đã thấy). Có thể bật re-score qua feature flag nếu cần.
4. **Source of truth khi hiển thị**: màn kết quả hiển thị transcript **đã dùng để
   chấm** (`realtime_transcript`), kèm video. `authoritative_transcript` chỉ để đối
   chiếu nội bộ. Tránh cảnh "AI chấm một đằng, transcript hiện một nẻo".

### 8.2 🔴 Race condition giữa fast path và nhánh nền (nguy hiểm nhất)

5. **Transcript-ready nền về khi answer đã EVALUATING/SCORED — bug regression của
   code hiện tại.** `applyTranscriptReady` đang có guard
   `if (answer.getStatus() != PROCESSING) { ...; return; }`
   (`AnswerService.java:442`). Ở fast path answer KHÔNG bao giờ ở PROCESSING khi
   batch transcript nền về → guard này **âm thầm vứt** bản authoritative.
   → **Phải tách logic, KHÔNG dùng chung early-return:**
   - `PROCESSING` → đây là legacy/no-realtime: trigger chấm như cũ.
   - `EVALUATING | SCORED` → `attachAuthoritativeTranscript`: chỉ lưu
     `authoritative_transcript` + `transcript_id`, **không** stage evaluation,
     **không** đổi status/điểm.
   - `FAILED` → tùy: nếu answer FAILED (real-time hỏng) mà batch về OK → có thể
     "cứu" bằng cách chấm từ batch (cân nhắc, hoặc để operator replay).
6. **Session finalize trước khi nhánh nền xong.** Fast path → SCORED nhanh →
   `maybeRequestOverallReview` có thể chạy + session COMPLETED *trước khi* batch
   transcript/video về. Yêu cầu: transcript-ready muộn phải **idempotent tuyệt đối**
   — không re-trigger planner, không pin câu mới, không gọi finalize lần hai. Chỉ
   ghi authoritative rồi dừng.
7. **`attach-video` gọi sau khi session đã COMPLETED.** Upload nền có thể `finish()`
   sau khi phiên kết thúc. attach-video **KHÔNG được** tái dùng guard
   `ensureWithinTimeBudget` / `IN_PROGRESS` của `submit` — nếu không sẽ reject
   (`SESSION_TIME_UP`/`SESSION_NOT_IN_PROGRESS`) và bỏ rơi video. Phải bỏ qua các
   guard này, chỉ giữ ownership check.

### 8.3 Bảo mật / validation (transcript giờ do client gửi)

8. **`transcriptText` đến thẳng từ client** (không qua STT server) →
   - Cap size cứng (vd `@Size(max = 20000)`), từ chối payload khổng lồ.
   - **Bề mặt prompt-injection tăng**: text này đi thẳng vào prompt AI. Trước đây
     cũng có (qua STT) nhưng giờ client kiểm soát hoàn toàn. Cân nhắc: AI prompt đã
     tách rõ role/user-content chưa; không nội suy transcript vào system prompt.
9. **`attach-video` authorization**: verify `storageObject.ownerUserId` == chủ
   session của answer **và** answer thuộc về caller; idempotent (gọi 2 lần không
   stage answer-submitted 2 lần → tránh batch STT chạy đôi).

### 8.4 Vận hành

10. **User đóng tab ngay sau submit.** Đã chấm xong (real-time) nhưng upload chưa
    `finish()`/attach → answer SCORED không video + không authoritative; các part dở
    nằm lại MinIO. Cần purger dọn orphan (kiểm `ephemeral` purger hiện có cover
    object PENDING quá hạn không — xem [[project_coding_validate_ephemeral]]).
11. **Double-submit / retry.** Guard `existsBySessionQuestionId` đã chặn answer
    trùng, nhưng FE retry phải tái dùng đúng `answerId` (từ response 200 đầu hoặc từ
    lỗi `ANSWER_ALREADY_SUBMITTED`) cho lời gọi `attach-video`, kẻo attach vào nhầm.
12. **Idempotency tổng quát**: `attach-video` và `transcript-ready` nền phải
    idempotent (answer đã SCORED → chỉ cập nhật transcript chính thức, không đổi
    status/điểm; gọi lại không nhân event).

## 8.bis 🔬 Spike BẮT BUỘC trước PR2 — mic dùng chung

**Câu hỏi sống-còn của Phương án A**: MediaRecorder (đang giữ mic qua `getUserMedia`)
**và** Web Speech API (`webkitSpeechRecognition`, tự mở capture audio riêng) chạy
**đồng thời trên cùng một mic**. Trên Chrome desktop thường OK, nhưng có thể xung
đột/giảm chất lượng trên một số máy, OS, hoặc Chrome Android.

- Spike: dựng PoC nhỏ — vừa record vừa nhận diện, test trên Chrome desktop/Android,
  Edge. Kiểm: cả hai có cùng chạy không, transcript có ra không, audio ghi có bị
  rè/ngắt không.
- Nếu xung đột → chuyển hướng: hoặc Web Speech chạy trên một `getUserMedia` stream
  **chia sẻ** (cùng MediaStreamTrack), hoặc bỏ qua A, đi thẳng **Phương án B** (WS →
  provider, audio tách từ chính stream đang record). **Quyết định này gate cả hướng
  v1.**

## 9. Tương thích ngược & feature flag

- Flag `interview.realtime-stt.enabled` (per-env). Tắt → toàn bộ chạy flow cũ.
- FE tự phát hiện hỗ trợ (`'webkitSpeechRecognition' in window`); không hỗ trợ →
  flow cũ. Nghĩa là Safari/Firefox vẫn hoạt động (chậm như cũ) — không regress.
- Backend chấp nhận cả 2 dạng payload (có/không transcript) vô thời hạn → rollout an toàn.

## 10. Các bước triển khai (PR tách nhỏ)

0. **Spike (§8.bis)** — PoC mic dùng chung Web Speech + MediaRecorder. **Gate**
   hướng v1; làm TRƯỚC khi viết FE.
1. **PR1 — DB + backend dual-path (chưa bật)**: migration cột mới; `submit` nhận
   transcript optional + nhánh EVALUATING thẳng; refactor `buildEvaluationRequested`
   dùng chung; endpoint `attach-video` (bỏ guard time-budget/IN_PROGRESS, §8.2 #7);
   `applyTranscriptReady` chuyển sang `switch`-theo-status (§6.2.4, §8.2 #5); cap
   size transcript (§8.3 #8). Flag mặc định OFF. Backend xanh, flow cũ nguyên vẹn.
2. **PR2 — FE real-time transcriber (Web Speech) + tách submit/upload**: bật khi
   browser hỗ trợ + flag ON ở staging. (Chỉ làm sau khi spike #0 xanh.)
3. **PR3 — reconciliation + mismatch flag + màn admin xem lệch** (tùy chọn).
4. **PR4 (sau, nếu cần) — Phương án B**: `RealtimeSttProvider` qua WS tới
   provider streaming, thay Web Speech.

## 11. Test

- Unit (interview): `submit` với transcript → tạo Answer EVALUATING + stage
  evaluation-requested, KHÔNG yêu cầu object READY. `attach-video` set objectId +
  stage answer-submitted. `applyTranscriptReady` trên answer SCORED → chỉ lưu
  authoritative, không đổi điểm.
- **Unit race (§8.2)** — bắt buộc: transcript-ready nền về khi answer EVALUATING →
  lưu authoritative, không stage eval; về khi SCORED → không đổi status/điểm/planner;
  `attach-video` sau khi session COMPLETED → vẫn thành công (không bị guard chặn);
  attach-video gọi 2 lần → answer-submitted chỉ stage 1 lần.
- Unit (FE): transcriber rỗng → fallback legacy; submit không chờ upload; auto-stop
  hết giờ → câu cuối vẫn vào transcript.
- E2E staging: đo lại `TIMING e2e` — kỳ vọng submit→scored ≈ AI (~9–12 s).
  Đo `upload_finish` (giờ ngoài critical path). Kiểm video xem lại vẫn ok.
- Trường hợp lỗi: tắt mạng giữa chừng; mất quyền mic; Safari (no Web Speech);
  upload nền fail; đóng tab ngay sau submit; double-submit/retry attach đúng answerId.

## 12. Rủi ro & câu hỏi mở

### 12.1 Phụ thuộc ẩn trong code hiện có (verify trước PR1)

- **Follow-up AI đọc parent transcript** qua `extractTranscript(parentAnswer)` —
  lấy từ `parentAnswer.rawEvaluation.answer.transcript` (`AnswerService.java:1085`).
  Fast path phải đảm bảo transcript của câu cha vẫn truy được khi sinh follow-up,
  nếu không follow-up lệch ngữ cảnh / rỗng. **Verify**: rawEvaluation (output AI) có
  thật sự echo `answer.transcript` không, hay phải đọc thẳng `realtime_transcript`
  cột mới. Nếu là cái sau → sửa `extractTranscript` đọc từ cột.
- **`responseLanguage` derivation** (`applyTranscriptReady`: profile →
  STT-detected → "vi"). Fast path không có lang do STT phát hiện → dùng
  `transcriptLanguage` client gửi, nhưng giữ thứ tự ưu tiên profile-first y hệt để
  feedback ra đúng ngôn ngữ.

### 12.2 Web Speech (Phương án A)

- **Chất lượng tiếng Việt**: chưa chắc bằng Scribe. Đo thực tế ở PR2; kém → Phương án B.
- **Chỉ gom `isFinal`**, bỏ qua `interimResults` khi cộng dồn, nếu không text nhân đôi.
- **Tự ngắt khi im lặng** (`onend`): auto-restart recognition khi chưa stop; lưu ý
  gap lúc restart có thể rớt vài từ.
- **Auto-stop khi hết giờ** (`maxSeconds`): có thể còn interim chưa finalize → mất
  câu cuối. Khi stop, đợi ngắn (vd ~300–500ms) cho `final` cuối hoặc flush buffer.
- **Tab nền**: Web Speech bị pause khi tab không active → transcript thủng. Cảnh báo
  user giữ tab foreground, hoặc dựa vào batch STT vá lại.
- **Teardown giữa các câu**: `recognition.stop()` + bỏ listener sạch khi chuyển câu,
  tránh leak/segment lẫn câu trước.
- **Quyền riêng tư**: Web Speech gửi audio tới Google. Nêu rõ trong điều khoản nếu cần.
- (Xem thêm spike mic dùng chung ở §8.bis.)

### 12.3 Khác

- **followup_rest chưa đo**: khi nhánh AI follow-up chạy, nó thêm ~1 lần gọi AI
  (~9 s) lên critical path trước khi pin câu kế — độc lập với Lever 2, cân nhắc
  cache/pre-author thêm follow-up nếu thành vấn đề.

## 13. Ước lượng

- Spike #0 (mic dùng chung): ~0.5 ngày — **làm trước, gate cả hướng**.
- PR1 (backend dual-path + migration): ~1.5–2 ngày (đã tính thêm xử lý race §8.2).
- PR2 (FE Web Speech + tách upload): ~1–1.5 ngày.
- PR3 (reconcile/admin): ~0.5–1 ngày (có thể hoãn).
- Tổng v1 (Web Speech) chạy được: **~3.5–4 ngày**. Phương án B là việc về sau.
