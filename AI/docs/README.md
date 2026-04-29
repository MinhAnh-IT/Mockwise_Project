# AI Service — Docs

Toàn bộ tài liệu chi tiết cho AI Service. Quick start và cấu trúc dự án xem ở [`../README.md`](../README.md).

| Tài liệu | Phạm vi |
|---|---|
| [`data_models.md`](./data_models.md) | Schema input/output của Evaluation Agent (live coding / behavioral / core conceptual) — dùng để hiểu `previous_evaluation` payload |
| [`testcase-generator-design.md`](./testcase-generator-design.md) | Thiết kế chi tiết của Testcase Generator (`POST /generate-testcases`) |
| [`selector-usage.md`](./selector-usage.md) | Cách dùng & vận hành Question Selector (`POST /selector/next-question`, `/selector/admin/reindex`) — setup pgvector, Kafka events, retrieval pipeline, troubleshooting |
