# Bộ câu hỏi Behavioral mới — wipe & seed qua API (có TTS)

Bộ này **thay thế** ngân hàng câu hỏi cũ bằng 56 câu behavioral tiếng Việt đã
chuẩn hoá, gắn tag theo **7 Amazon Leadership Principle** mà blueprint hệ thống
đang dùng. Seed đi qua **Admin API** của question-bank (không phải SQL) để mỗi
câu được **sinh audio TTS tiếng Việt + lưu storage**, và được **AI-Question-
Selector tạo embedding** khi chuyển ACTIVE.

## Nội dung

| File | Vai trò |
|---|---|
| `behavioral-questions.ndjson` | 56 câu, mỗi dòng = 1 body POST sẵn sàng. `text` tiếng Việt + 5 `expectedSignals` (snake_case English, để AI chấm khách quan) + 2–3 `tags` lowercase. |
| `wipe-questionbank.sh` | **DESTRUCTIVE** — xoá toàn bộ question bank + embeddings trên prod. |
| `seed-behavioral.sh` | Tạo 56 câu qua API (create → TTS → activate → embed). |
| `seed-inner.sh` | Vòng lặp chạy bên trong container curl (do `seed-behavioral.sh` gọi). |

## Độ phủ (đã validate)

7 LP × (3 EASY · 3 MEDIUM · 2 HARD) = **56 câu**. Đạt mức **KHUYẾN NGHỊ** của
`interview-service/docs/blueprint-catalog-and-question-bank-spec.md` (EASY≥3 ·
MEDIUM≥3 · HARD≥2 mỗi LP). LP: `OWNERSHIP, CUSTOMER_OBSESSION,
LEARN_AND_BE_CURIOUS, DELIVER_RESULTS, ARE_RIGHT_A_LOT,
HAVE_BACKBONE_DISAGREE_AND_COMMIT, HIRE_AND_DEVELOP_THE_BEST`.

## Cơ chế (đã kiểm chứng trên prod, read-only)

- `POST /api/v1/question-bank/admin/questions/behavioral` → `QuestionService.createBehavioral`
  gọi `ttsClient.synthesize(id, text)` **đồng bộ** (TTS `vi`) → lưu storage → set `audioKey`.
  Câu tạo ra ở trạng thái **DRAFT**.
- `PATCH /admin/questions/{id}/status {"status":"ACTIVE"}` → emit `QUESTION_ACTIVATED`
  lên Kafka `question-bank-events` → `mockwise-ai-service-1` tạo embedding vào
  `ai_question_selector.question_index`.
- Script gọi **thẳng** container `question-bank-service:8084` trên network
  `mockwise_internal-net` qua một container `curlimages/curl` tạm → bỏ qua
  gateway nên **không cần JWT admin**; controller chỉ đọc header `X-User-Id`
  (ở đây để `admin-seed` làm provenance). Đã probe thử: trả `http=200`.

## Cách chạy (trên máy có `ssh contabo`)

```bash
cd question-docs/behavioral

# 1) Xoá bank cũ + embeddings (gõ 'WIPE' để xác nhận)
./wipe-questionbank.sh

# 2) Seed bộ mới (create + TTS + activate). In OK/FAIL từng câu + tổng kết.
./seed-behavioral.sh
```

Override nếu cần: `REMOTE_HOST=...`, `NET=...`, `QB_BASE=...`, `UID_HDR=...`.

## Xác minh sau khi seed

```bash
# Tổng + phân bố theo LP × độ khó + có audio chưa
ssh contabo 'docker exec mockwise-infra-postgres-1 psql -U mockwise -d question_bank -c "
  SELECT bq.competency, q.difficulty, count(*) n,
         count(*) FILTER (WHERE bq.audio_key IS NOT NULL) AS with_audio
  FROM behavioral_questions bq JOIN questions q USING(id)
  WHERE q.status='\''ACTIVE'\''
  GROUP BY 1,2 ORDER BY 1,2;"'

# Embeddings đã rebuild
ssh contabo 'docker exec mockwise-infra-postgres-1 psql -U mockwise -d ai_question_selector -tAc "SELECT count(*) FROM question_index;"'
```

Kỳ vọng: 56 câu ACTIVE, `with_audio = n` ở mọi nhóm, `question_index = 56`
(behavioral được index; core/coding hiện đã xoá nên chỉ còn behavioral).

Smoke test luồng `/start` BEHAVIORAL cho một (role, level) bất kỳ → không
`QUESTION_BANK_UNAVAILABLE`, pin được câu 1.

## Lưu ý

- **Audio mồ côi (orphan):** wipe **không** xoá file audio TTS cũ trong MinIO/
  storage — chúng thành rác vô hại (không câu nào tham chiếu). Dọn riêng nếu muốn.
- **CORE/CODING đã bị xoá** theo yêu cầu "wipe toàn bộ". → Phỏng vấn CORE/CODING
  sẽ `QUESTION_BANK_UNAVAILABLE` cho tới khi bạn seed lại 2 loại đó. Chỉ BEHAVIORAL
  có dữ liệu sau bước này.
- **Re-run an toàn:** seed không idempotent — chạy lại sẽ tạo **trùng** 56 câu nữa.
  Muốn làm lại sạch thì chạy `wipe` trước.
- **Nếu `no_audio > 0`** trong tổng kết: TTS (`mockwise-tts-stt-service-1`) hoặc
  storage đang lỗi. Sửa rồi sửa text/​tạo lại (sửa text re-trigger TTS), hoặc xoá
  câu thiếu audio và seed lại.
- **`is_opener`** không set được qua API (request không có field này) → bộ mới
  không có câu opener; picker tự bỏ yêu cầu opener ở Q1 (an toàn).
- Câu tạo ra dùng `createdBy = "admin-seed"` để dễ truy vết/dọn về sau:
  `... WHERE created_by='admin-seed'`.
