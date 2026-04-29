# AI Service

Single FastAPI service that hosts both AI-powered features used by Mockwise:

- **Evaluation graph** — scores live-coding / behavioral / core-conceptual
  interview answers via a LangGraph + Gemini pipeline.
- **Question selector (RAG)** — picks the next interview question from the
  question bank based on the candidate's evaluated weaknesses, using
  Gemini embeddings + pgvector for retrieval.

```
AI/
├── api.py            # FastAPI app
├── config.py         # all env vars
├── llm.py            # Gemini chat + embedding helpers
├── main.py           # evaluation + generator graph factories
├── evaluator/        # evaluation graph (langgraph)
├── generator/        # leetcode-aware testcase generator
├── selector/
│   ├── messaging/    # Kafka consumer for question-bank-events
│   ├── migrations/   # pgvector schema
│   ├── repository/   # asyncpg pool + question_index queries
│   └── retrieval/    # embedding + weakness profile + selector pipeline
├── models/
│   ├── (evaluation + generator pydantic models)
│   └── selector/     # snapshot, events, request/response
├── requirements.txt
└── Dockerfile
```

## Endpoints

| Method | Path                          | Purpose                                  |
|-------:|-------------------------------|------------------------------------------|
| GET    | `/health`                     | Config + index size                      |
| POST   | `/evaluate`                   | Score a single interview answer          |
| POST   | `/generate-testcases`         | Generate testcases for a coding question |
| GET    | `/leetcode/fetch`             | Fetch a LeetCode problem                 |
| POST   | `/selector/next-question`     | Pick the next interview question         |
| POST   | `/selector/admin/reindex`     | Bootstrap / recovery: rebuild the index  |

All non-health endpoints require header `X-API-Key: $SERVICE_API_KEY`.

## Running locally

```bash
cp .env.example .env  # fill in keys
pip install -r requirements.txt
uvicorn api:app --reload --port 8000
```

The selector half (DB + Kafka consumer) is **opt-in**: if `DATABASE_URL`
is not set the service still serves `/evaluate` and `/generate-testcases`,
and the selector endpoints return 503. This keeps local eval-only work
friction-free.

## Selector setup

Before serving `/selector/next-question`, populate the index:

1. Run the migration on the `ai_question_selector` Postgres database:
   ```bash
   psql "$DATABASE_URL" -f selector/migrations/V1__init_schema.sql
   ```
   The migration creates the `vector` extension, `question_index`, and the
   idempotency log `processed_events`.
2. Seed the index from question-bank:
   ```bash
   curl -X POST -H "X-API-Key: $SERVICE_API_KEY" \
        http://localhost:8000/selector/admin/reindex
   ```
3. From this point on, the Kafka consumer keeps the index in sync —
   `question-bank-service` publishes `QUESTION_ACTIVATED` /
   `QUESTION_UPDATED` / `QUESTION_DEACTIVATED` after each transactional
   commit.

## Required env vars

| Var                          | Used by               | Notes                                   |
|------------------------------|-----------------------|-----------------------------------------|
| `GOOGLE_API_KEY`             | both                  | Required                                |
| `MODEL_NAME`                 | both                  | Default `gemini-flash-latest`           |
| `EMBEDDING_MODEL`            | selector              | Default `text-embedding-004` (768 dim)  |
| `SERVICE_API_KEY`            | both                  | Inbound auth for non-health endpoints   |
| `DATABASE_URL`               | selector              | postgres://... (omit to disable RAG)    |
| `KAFKA_BOOTSTRAP_SERVERS`    | selector              | omit to disable live indexing           |
| `QUESTION_BANK_BASE_URL`     | selector reindex      | Default points at internal-net DNS      |
| `INTERNAL_API_KEY`           | selector reindex      | Sent as `X-Internal-API-Key` to qbank   |

## Docs

Chi tiết hơn ở [`docs/`](./docs/README.md):

- [`docs/data_models.md`](./docs/data_models.md) — schema input/output của evaluation graph
- [`docs/testcase-generator-design.md`](./docs/testcase-generator-design.md) — thiết kế testcase generator
- [`docs/selector-usage.md`](./docs/selector-usage.md) — cách dùng & vận hành question selector (setup, API, retrieval pipeline, recovery)
