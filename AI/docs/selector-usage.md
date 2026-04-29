# Question Selector — Usage Guide

> **Status:** Implemented
> **Service:** AI Service (module `selector/`)
> **Feature:** `POST /selector/next-question`, `POST /selector/admin/reindex`

Tài liệu này mô tả cách dùng và vận hành phần **RAG question selector** — module chọn câu hỏi phỏng vấn tiếp theo dựa trên điểm yếu của ứng viên đã được evaluator chấm.

---

## 1. Tổng quan kiến trúc

```
                    ┌──────────────────────────┐
                    │  question-bank-service   │
                    │  (Java, Postgres "qb")   │
                    └─────────────┬────────────┘
                                  │ Kafka topic
                                  │ "question-bank-events"
                                  │ ACTIVATED / UPDATED / DEACTIVATED
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│  AI Service · selector/                                     │
│                                                             │
│   ┌───────────────────────┐    ┌─────────────────────────┐  │
│   │ messaging/consumer.py │    │ retrieval/selector.py   │  │
│   │ aiokafka loop         │    │ pipeline:               │  │
│   │ idempotent on event_id│    │  evaluation             │  │
│   │ embed → upsert        │    │  → weakness_profile     │  │
│   └───────────┬───────────┘    │  → embed                │  │
│               │                │  → hard SQL filter      │  │
│               ▼                │  → pgvector cosine      │  │
│   ┌──────────────────────────┐ │  → top-1 + rationale    │  │
│   │  pgvector index          │◄┘─────────────────────────┘  │
│   │  question_index(...)     │                              │
│   │  embedding vector(768)   │                              │
│   │  active boolean          │                              │
│   └──────────────────────────┘                              │
└─────────────────────────────────────────────────────────────┘
                                  ▲
                                  │ POST /selector/next-question
                                  │ { previous_evaluation, asked_ids, ... }
                                  │
                          ┌───────┴───────────┐
                          │ interview-service │
                          └───────────────────┘
```

**Stateless API**: AI Service không lưu session — interview-service truyền `asked_question_ids` và `previous_evaluation` mỗi lần gọi.

**Index lifecycle**: question-bank publish event sau mỗi commit (`@TransactionalEventListener(AFTER_COMMIT)`); consumer dedup bằng `event_id`, embed lại bằng Gemini `text-embedding-004`, upsert vào pgvector.

---

## 2. Setup lần đầu

### 2.1 Postgres + pgvector

Database `ai_question_selector` cần được tạo trên cùng Postgres host của hệ thống. Image phải có extension `vector` — nếu đang dùng `postgres:16-alpine` (không có), đổi sang `pgvector/pgvector:pg16` (drop-in, đọc volume cũ ok).

```bash
# Trên Postgres host
psql -U mockwise -d postgres -c "CREATE DATABASE ai_question_selector;"

# Cài extension + tạo schema
psql "postgresql://mockwise:$POSTGRES_PASSWORD@$POSTGRES_HOST:5432/ai_question_selector" \
     -f AI/selector/migrations/V1__init_schema.sql
```

Migration tạo:
- `CREATE EXTENSION vector` (idempotent)
- Bảng `question_index` (vector(768) + cosine HNSW + GIN trên `target_roles`/`key_concepts`)
- Bảng `processed_events` (idempotency log keyed bằng `event_id`)

Đổi `EMBEDDING_DIM` → cũng phải đổi `vector(N)` trong migration tương ứng.

### 2.2 Kafka topic

Topic `question-bank-events` được auto-create lần đầu producer publish. Nếu Kafka tắt auto-create, tạo thủ công:

```bash
kafka-topics.sh --bootstrap-server $SPRING_KAFKA_BOOTSTRAP_SERVERS \
                --create --topic question-bank-events \
                --partitions 3 --replication-factor 1
```

Partition theo `questionId` đảm bảo cùng question xử lý in-order.

### 2.3 Env vars

Bổ sung vào `.env` của AI service (xem `AI/README.md` để biết toàn bộ):

```bash
# Selector enable on/off bằng việc set DATABASE_URL
DATABASE_URL=postgresql://mockwise:PASSWORD@HOST:5432/ai_question_selector
KAFKA_BOOTSTRAP_SERVERS=HOST:9092
KAFKA_TOPIC_QUESTION_BANK=question-bank-events
KAFKA_CONSUMER_GROUP=ai-service-selector

# Cho bootstrap reindex
QUESTION_BANK_BASE_URL=http://question-bank-service:8084
INTERNAL_API_KEY=...

# Embedding (default ok)
EMBEDDING_MODEL=text-embedding-004
EMBEDDING_DIM=768

# Tuning retrieval
HARD_FILTER_LIMIT=50
TOP_K=5

SELECTOR_VERSION=selector-v1.0
```

Nếu KHÔNG set `DATABASE_URL` → các endpoint `/selector/*` trả 503, phần evaluator vẫn chạy bình thường (cờ `_selector_enabled=False` trong `api.py`).

### 2.4 Bootstrap index

Sau khi schema sẵn sàng nhưng index vẫn trống, gọi `/selector/admin/reindex` để pull mọi câu hỏi `ACTIVE` từ question-bank và embed:

```bash
curl -X POST -H "X-API-Key: $AI_SERVICE_API_KEY" \
     http://localhost:8888/api/ai/selector/admin/reindex
```

Response:

```json
{
  "behavioral_indexed": 24,
  "core_indexed": 36,
  "failed": 0,
  "duration_ms": 18437
}
```

Sau bước này, mọi thay đổi `question-bank` về sau (PUBLISH/UPDATE/DEACTIVATE) sẽ tự đồng bộ qua Kafka.

---

## 3. API reference

### 3.1 `POST /selector/next-question`

Public path qua gateway: `POST /api/ai/selector/next-question`
Header: `X-API-Key: $AI_SERVICE_API_KEY`

**Request:**

```json
{
  "session_id": "sess_abc123",
  "interview_type": "BEHAVIORAL",
  "previous_evaluation": {
    "session_id": "sess_abc123",
    "interview_type": "behavioral",
    "overall_score": 58,
    "scores": {
      "star_structure":  { "score": 70, "note": "..." },
      "relevance":       { "score": 80, "note": "..." },
      "specificity":     { "score": 40, "note": "..." },
      "impact_result":   { "score": 30, "note": "..." },
      "self_awareness":  { "score": 60, "note": "..." }
    },
    "signal_coverage": [
      { "signal_name": "took_initiative", "detected": false, "evidence": null },
      { "signal_name": "drove_decision",  "detected": false, "evidence": null },
      { "signal_name": "measured_impact", "detected": true,  "evidence": "..." }
    ],
    "red_flags": [
      { "type": "missing_result", "severity": "high", "detail": "..." }
    ]
  },
  "asked_question_ids": ["q-001", "q-014"],
  "constraints": {
    "target_role": "BACKEND",
    "difficulty_hint": null,
    "competency": "OWNERSHIP",
    "domain": null
  }
}
```

| Field | Bắt buộc | Ghi chú |
|---|---|---|
| `session_id` | ✓ | Echo back trong response, dùng để trace |
| `interview_type` | ✓ | `BEHAVIORAL` hoặc `CORE_CONCEPTUAL` |
| `previous_evaluation` | optional | Output của AI Evaluation lần trước. **`null` ở turn đầu** → strategy `first_turn` |
| `asked_question_ids` | optional | List ID đã hỏi trong session — exclude khỏi result. Bắt buộc nếu không muốn hỏi trùng. |
| `constraints.target_role` | optional | `BACKEND` / `FRONTEND` / `FULLSTACK` / `AI` / `DEVOPS` / `MOBILE` |
| `constraints.difficulty_hint` | optional | `EASY` / `MEDIUM` / `HARD`. Nếu null, selector tự suy từ `overall_score` (xem §4.4) |
| `constraints.competency` | optional | Chỉ áp dụng cho behavioral. Hard filter theo competency |
| `constraints.domain` | optional | Chỉ áp dụng cho core_conceptual |

**Response 200:**

```json
{
  "session_id": "sess_abc123",
  "question_id": "q-042",
  "question_snapshot": {
    "id": "q-042",
    "type": "BEHAVIORAL",
    "text": "Hãy kể về một lần bạn chủ động nhận trách nhiệm cho một dự án không phải của mình.",
    "difficulty": "MEDIUM",
    "tags": ["ownership", "initiative"],
    "competency": "OWNERSHIP",
    "expected_signals": [
      "took_initiative",
      "drove_decision",
      "measured_impact"
    ],
    "domain": null,
    "target_roles": [],
    "key_concepts": [],
    "depth_expected": null
  },
  "rationale": "Câu kế tiếp được chọn để đào sâu tín hiệu yếu: took_initiative, drove_decision; giữ trong năng lực OWNERSHIP.",
  "retrieval_meta": {
    "strategy": "exploit_weakness",
    "candidates_considered": 5,
    "top_candidates": [
      { "question_id": "q-042", "similarity": 0.864, "competency": "OWNERSHIP", "domain": null, "difficulty": "MEDIUM" },
      { "question_id": "q-019", "similarity": 0.832, "competency": "OWNERSHIP", "domain": null, "difficulty": "MEDIUM" },
      { "question_id": "q-007", "similarity": 0.798, "competency": "OWNERSHIP", "domain": null, "difficulty": "EASY" }
    ]
  }
}
```

`question_snapshot` đầy đủ để interview-service không cần gọi lại question-bank.

**Response 404** — không còn câu hỏi nào hợp lệ sau toàn bộ fallback chain (xem §4.5):

```json
{ "detail": "No candidate question found after all fallbacks. Check that the index is populated and asked_question_ids is not exhaustive." }
```

**Response 503** — selector bị disabled (chưa set `DATABASE_URL`):

```json
{ "detail": "Selector is disabled (DATABASE_URL not configured)" }
```

### 3.2 `POST /selector/admin/reindex`

Public path: `POST /api/ai/selector/admin/reindex`
Header: `X-API-Key: $AI_SERVICE_API_KEY`

Pull mọi câu hỏi `ACTIVE` (BEHAVIORAL + CORE_CONCEPTUAL) từ question-bank và (re)embed. Dùng cho:

- Bootstrap lần đầu
- Recovery khi nghi mất Kafka event
- Sau khi đổi `EMBEDDING_MODEL` (mọi vector cũ phải re-embed)

Không có body. Idempotent — chạy lại lần nữa chỉ overwrite `embedding` và `indexed_at`. Mất ~250ms/câu hỏi (Gemini embed call đồng bộ).

---

## 4. Cách selector chọn câu hỏi

### 4.1 Pipeline tổng

```
request
  │
  ▼
[build_profile]   ── từ previous_evaluation → text profile + structured hints
  │
  ▼
[embed_text]      ── Gemini text-embedding-004 → vector 768
  │
  ▼
[hard_filter]     ── SQL: type, active, NOT IN asked_ids,
  │                       optional competency/domain/role/difficulty
  ▼
[cosine_rerank]   ── ORDER BY embedding <=> query LIMIT TOP_K
  │
  ▼
[fallback_chain]  ── nếu rỗng: bỏ difficulty → bỏ competency/domain
  │
  ▼
top-1 + rationale + retrieval_meta
```

### 4.2 Xây weakness profile

`selector/retrieval/weakness_profile.py` rule-based, không thêm LLM call. Hai nhánh:

**Behavioral** — extract:
- `signal_coverage[*]` có `detected=false` → tín hiệu yếu
- 2 dimension điểm thấp nhất từ `scores`
- `red_flags[*]` có `severity="high"`
- `overall_score` (cho difficulty ladder)

Profile text (tiếng Việt, để cùng embedding space với question):

```
Mục tiêu: chọn câu hỏi hành vi tiếp theo nhằm khai thác điểm yếu của ứng viên.
Năng lực cần đào sâu: OWNERSHIP.
Tín hiệu hành vi chưa thể hiện: took_initiative; drove_decision.
Khía cạnh điểm thấp nhất: impact_result, specificity.
Cờ đỏ nghiêm trọng: missing_result.
Điểm tổng hiện tại: 58/100.
```

**Core Conceptual** — extract:
- `concept_coverage[*]` có `mentioned=false` hoặc `correct=false` → khái niệm thiếu/sai
- `misconceptions[*].claim` (top 3) → trích nguyên văn
- 2 dimension điểm thấp nhất
- `level_calibration.gap`
- `overall_score`

### 4.3 Hard filter SQL

`selector/repository/question_index.py:hybrid_search` xây `WHERE`:

```sql
type = $1
AND active = true
AND id <> ALL($asked_ids::text[])    -- nếu có
AND competency = $...                -- nếu hint cho behavioral
AND domain = $...                    -- nếu hint cho core
AND $... = ANY(target_roles)         -- nếu có target_role
AND difficulty = $...                -- nếu có difficulty (xem §4.4)
ORDER BY embedding <=> $query_embedding
LIMIT $top_k
```

`<=>` là cosine distance của pgvector (0 = identical, 2 = đối ngược).

### 4.4 Difficulty ladder

Từ `previous_evaluation.overall_score`:

| Score | Difficulty áp dụng |
|---|---|
| ≥ 90 | `HARD` |
| 75 – 89 | `MEDIUM` (giữ ở zone tốt) |
| 50 – 74 | `MEDIUM` |
| < 50 | `EASY` (giảm xuống cho bớt áp lực) |

`constraints.difficulty_hint` từ caller luôn override. Turn đầu (no `previous_evaluation`) → không filter difficulty.

### 4.5 Fallback chain

Nếu `hybrid_search` trả 0 row:

1. **Lần 2**: bỏ filter `difficulty` (giữ competency/domain/role/asked_ids).
2. **Lần 3**: bỏ luôn `competency`/`domain` hint, chỉ giữ `type` + `target_role` + `asked_ids`.
3. Vẫn rỗng → `RuntimeError` → 404.

Lý do: thà chấp nhận lệch difficulty/competency hơn là không có câu hỏi nào. `retrieval_meta.strategy` không phản ánh fallback — nó luôn theo profile.strategy gốc.

### 4.6 Rationale

Template tiếng Việt, sinh từ profile + question đã chọn. Không tốn LLM call. Mẫu:

- **First turn**: `"Câu hỏi mở đầu hành vi (năng lực: OWNERSHIP)."`
- **Exploit**: `"Câu kế tiếp được chọn để đào sâu tín hiệu yếu: X, Y; giữ trong năng lực Z."`

Nếu cần rationale chất lượng cao hơn (1-2 câu Gemini-generated), dễ thay vào `_rationale()` ở `selector/retrieval/selector.py:79` — thêm 1 `call_structured` là đủ.

---

## 5. Vòng đời index (Kafka events)

### 5.1 Producer (question-bank-service)

`@TransactionalEventListener(phase = AFTER_COMMIT)` → emit sau khi DB commit thành công. Trigger:

| Trigger ở question-bank | Event |
|---|---|
| `updateStatus()` đổi → `ACTIVE` | `QUESTION_ACTIVATED` (kèm full snapshot) |
| `updateBehavioral()` / `updateCore()` khi đang `ACTIVE` | `QUESTION_UPDATED` (kèm full snapshot) |
| `updateStatus()` đổi `ACTIVE` → khác | `QUESTION_DEACTIVATED` (chỉ id) |
| `delete()` (soft delete) khi đang `ACTIVE` | `QUESTION_DEACTIVATED` |
| Tạo DRAFT, đổi audio_key, hoặc thao tác trên LIVE_CODING | **không emit** |

### 5.2 Consumer (AI service)

`messaging/consumer.py` — aiokafka loop, manual offset commit theo partition.

Mỗi event:
1. `is_event_processed(eventId)` → nếu đã xử lý, skip (idempotency log).
2. Nếu type `BEHAVIORAL`/`CORE_CONCEPTUAL` (LIVE_CODING ignore):
   - `ACTIVATED`/`UPDATED`: build snapshot → embed → `upsert_question`.
   - `DEACTIVATED`: `deactivate_question` (set `active=false`).
3. `mark_event_processed(eventId)` ghi vào `processed_events`.
4. Commit offset cho partition.

Failure ở bước embed/DB → log + skip event nhưng **vẫn commit offset** (tránh poison pill). Lossy by design — recovery qua `/admin/reindex`.

### 5.3 Recovery

Khi nghi mất event (vd Kafka down lúc producer commit, hoặc consumer crash giữa upsert):

```bash
curl -X POST -H "X-API-Key: $AI_SERVICE_API_KEY" \
     http://localhost:8888/api/ai/selector/admin/reindex
```

Reindex idempotent, overwrite mọi row hiện có. An toàn để chạy bất kỳ lúc nào.

---

## 6. Vận hành

### 6.1 Health check

```bash
curl http://localhost:8888/api/ai/health | jq
```

Trường quan trọng:

```json
{
  "status": "healthy",
  "checks": {
    "google_api_key": "configured",
    "evaluation_graph": "ok",
    "generator_graph": "ok",
    "selector_index_active_count": 60   // nếu disabled: "selector": "disabled (no DATABASE_URL)"
  }
}
```

`selector_index_active_count` = số row `active=true` trong `question_index`. Nếu = 0 nhưng question-bank có ACTIVE → cần reindex.

### 6.2 Quan sát Kafka consumer

Log từ `selector/messaging/consumer.py` — search `Indexed question` / `Deactivated question` / `Event ... already processed`:

```bash
docker logs mockwise-ai-service-1 2>&1 | grep -E "(Indexed|Deactivated|Kafka)"
```

Lag consumer:

```bash
docker exec mockwise-infra-kafka-1 /opt/kafka/bin/kafka-consumer-groups.sh \
       --bootstrap-server localhost:9092 \
       --describe --group ai-service-selector
```

### 6.3 Quan sát index trực tiếp

```sql
-- Số question theo type & status
SELECT type, active, count(*) FROM question_index GROUP BY 1, 2;

-- Câu hỏi mới index gần đây nhất
SELECT id, type, competency, domain, indexed_at
FROM question_index
ORDER BY indexed_at DESC
LIMIT 10;

-- Idempotency log
SELECT count(*) FROM processed_events;
```

### 6.4 Troubleshooting

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `/next-question` luôn 503 | `DATABASE_URL` chưa set | Set env, restart |
| `/next-question` luôn 404 | Index trống hoặc `asked_ids` đã exhaust toàn bộ | Reindex; kiểm `count_active`; reset session asked_ids |
| Câu hỏi không cập nhật sau khi admin sửa | Kafka consumer không chạy / partition lag | Check log; check `kafka-consumer-groups.sh`; reindex để bắt lại |
| `expected 768 dims, got X` | Đổi `EMBEDDING_MODEL` mà không re-embed | Reindex toàn bộ; nếu đổi dim → migrate `vector(N)` |
| `relation question_index does not exist` | Migration chưa chạy | `psql -f selector/migrations/V1__init_schema.sql` |
| `extension vector is not available` | Postgres image không có pgvector | Đổi image → `pgvector/pgvector:pg16` |
| Gemini timeout / rate limit | Quota / network | Check Google Cloud Console; tạm dùng cached embedding cho dev |

---

## 7. Tham chiếu

- Schema input/output của evaluator (dùng làm `previous_evaluation`): [`data_models.md`](./data_models.md)
- Code chính:
  - `AI/api.py` — endpoint definitions
  - `AI/selector/retrieval/selector.py` — pipeline chính
  - `AI/selector/retrieval/weakness_profile.py` — rule-based profile builder
  - `AI/selector/retrieval/embedding.py` — text builder cho embedding
  - `AI/selector/repository/question_index.py` — SQL + hybrid_search
  - `AI/selector/messaging/consumer.py` — Kafka loop
  - `AI/selector/migrations/V1__init_schema.sql` — schema
- Phía Java (event producer):
  - `services/question-bank-service/src/main/java/com/mockwise/questionbank/message/event/QuestionBankEvent.java`
  - `services/question-bank-service/src/main/java/com/mockwise/questionbank/message/publisher/QuestionBankEventPublisher.java`
  - `services/question-bank-service/src/main/java/com/mockwise/questionbank/message/publisher/QuestionBankEventEmitter.java`
