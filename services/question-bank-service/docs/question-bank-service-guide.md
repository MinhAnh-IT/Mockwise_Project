# Question Bank Service — Design Guide

Tài liệu này mô tả thiết kế tổng thể của Question Bank Service: mục đích, kiến trúc, database schema, API, và các integration points với các service khác trong hệ thống.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Tech Stack](#3-tech-stack)
4. [Domain Model](#4-domain-model)
   - 4.1 [Base Question](#41-base-question)
   - 4.2 [Behavioral Question](#42-behavioral-question)
   - 4.3 [Core Conceptual Question](#43-core-conceptual-question)
   - 4.4 [Live Coding Question](#44-live-coding-question)
5. [Database Schema](#5-database-schema)
6. [API Reference](#6-api-reference)
   - 6.1 [Create Questions](#61-create-questions)
   - 6.2 [Read & Filter Questions](#62-read--filter-questions)
   - 6.3 [Update & Delete](#63-update--delete)
   - 6.4 [Downstream Payload Endpoints](#64-downstream-payload-endpoints)
   - 6.5 [Audio Management](#65-audio-management)
7. [Integration Points](#7-integration-points)
8. [Enums Reference](#8-enums-reference)
9. [Design Decisions](#9-design-decisions)

---

## 1. Overview

Question Bank Service là nơi lưu trữ và quản lý toàn bộ câu hỏi cho hệ thống mock interview. Service này phục vụ 3 loại phỏng vấn:

| Loại | Mô tả | Có audio |
|------|-------|----------|
| `BEHAVIORAL` | Câu hỏi hành vi theo STAR framework | Có |
| `CORE_CONCEPTUAL` | Câu hỏi kiến thức kỹ thuật conceptual | Có |
| `LIVE_CODING` | Bài tập lập trình, chấm qua Judge Service | Không |

**Vai trò của service trong hệ thống:**
- Là single source of truth cho tất cả câu hỏi
- Cung cấp payload đúng format cho AI Service và Judge Service (không cần consumer tự map)
- Quản lý lifecycle của audio (liên kết với Storage Service)

---

## 2. Architecture

```
                        ┌─────────────────────────────┐
                        │    Question Bank Service     │
                        │                             │
  Admin / Creator ──────►  CRUD Questions             │
                        │  Filter / Search            │
  Interview Service ────►  GET /for-ai                ├──► AI Service (POST /evaluate)
                        │  GET /for-judge             ├──► Judge Service (submit code)
  Storage Service  ────►  PATCH /audio (callback)    │
                        │                             │
                        │  PostgreSQL (main store)    │
                        └─────────────────────────────┘
```

**Flow tạo câu hỏi có audio (Behavioral / Core):**
```
Admin tạo câu hỏi (POST)
  → QuestionBank lưu DB (audio_key = null)
  → [Sau này] Admin trigger TTS → Storage Service convert text → upload MinIO
  → Storage Service callback PATCH /questions/{id}/audio với audio_key
  → QuestionBank lưu audio_key
  → Interview session đọc audio qua GET /questions/{id}/audio-url
```

---

## 3. Tech Stack

| Layer | Công nghệ |
|-------|-----------|
| Framework | Spring Boot 3 |
| Database | PostgreSQL (JSONB cho các trường phức tạp) |
| ORM | Spring Data JPA + Hibernate |
| Migration | Flyway |
| Build | Maven (kế thừa parent pom.xml) |
| Auth | JWT — validate qua API Gateway + IAM Service |

---

## 4. Domain Model

### 4.1 Base Question

Các field chung cho tất cả 3 loại câu hỏi.

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `id` | UUID | yes | Primary key, auto-generated |
| `type` | Enum | yes | `BEHAVIORAL` \| `CORE_CONCEPTUAL` \| `LIVE_CODING` |
| `difficulty` | Enum | yes | `EASY` \| `MEDIUM` \| `HARD` |
| `status` | Enum | yes | `DRAFT` \| `ACTIVE` \| `INACTIVE` |
| `tags` | `String[]` | yes | Nhãn tự do, dùng để filter. Ví dụ: `["leadership", "conflict"]` |
| `created_by` | UUID | yes | ID của user tạo (từ IAM Service) |
| `created_at` | Timestamp | auto | |
| `updated_at` | Timestamp | auto | |

---

### 4.2 Behavioral Question

> **Mục đích:** Câu hỏi hành vi kiểu "Tell me about a time...". AI sẽ đánh giá theo STAR framework và kiểm tra các signal kỳ vọng.

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `text` | TEXT | yes | Nội dung câu hỏi. Đây cũng là text để TTS tạo audio |
| `competency` | Enum | yes | Năng lực cần đánh giá — xem [Enums](#8-enums-reference) |
| `expected_signals` | `String[]` | yes | Các tín hiệu AI cần kiểm tra trong câu trả lời. **Trường quan trọng nhất** — AI populate `signal_coverage` map dựa vào đây |
| `audio_key` | VARCHAR | no | Storage key trong MinIO. Null khi mới tạo, được set sau khi TTS xong |

**Ví dụ `expected_signals`:**
```json
["shows_autonomy", "respectful_disagreement", "data_driven_approach", "outcome_oriented"]
```

**Mapping tới AI Service input:**
```
behavioral_question.text            → input.question.text
behavioral_question.competency      → input.question.competency
behavioral_question.expected_signals → input.question.expected_signals
```

---

### 4.3 Core Conceptual Question

> **Mục đích:** Câu hỏi kiến thức kỹ thuật. AI kiểm tra mức độ coverage các concept và phát hiện misconceptions.

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `text` | TEXT | yes | Nội dung câu hỏi. Đây cũng là text để TTS tạo audio |
| `target_roles` | `Enum[]` | yes | Role kỹ thuật mà câu hỏi hướng tới — xem [Enums](#8-enums-reference). Array vì 1 câu hỏi có thể dùng cho nhiều role |
| `domain` | Enum | yes | Chủ đề kỹ thuật cụ thể — xem [Enums](#8-enums-reference) |
| `key_concepts` | `String[]` | yes | Danh sách concept AI cần kiểm tra coverage. AI generate `concept_coverage` map từ đây |
| `depth_expected` | TEXT | yes | Mô tả mức độ kỳ vọng về độ sâu câu trả lời. Ví dụ: `"trade-offs and real-world use cases"` |
| `audio_key` | VARCHAR | no | Storage key trong MinIO. Null khi mới tạo |

**Phân biệt `target_roles` vs `domain`:**

| | `target_roles` | `domain` |
|---|---|---|
| **Ý nghĩa** | Câu hỏi này dành cho **ai** | Câu hỏi này hỏi về **chủ đề gì** |
| **Ví dụ** | `["BACKEND", "AI"]` | `DATABASE` |
| **Dùng để** | Filter câu hỏi phù hợp với ứng viên | Phân nhóm nội dung kỹ thuật |

**Ví dụ `target_roles`:**
```json
["BACKEND", "AI"]          // câu hỏi database apply cho cả hai
["FRONTEND"]               // câu hỏi về browser rendering chỉ dành cho FE
["BACKEND", "DEVOPS"]      // câu hỏi về networking
["AI"]                     // câu hỏi về gradient descent chỉ dành cho AI
```

**Ví dụ `key_concepts`:**
```json
["memory_isolation", "context_switching", "GIL", "concurrency_vs_parallelism", "fork_vs_spawn"]
```

**Mapping tới AI Service input:**
```
core_question.text           → input.question.text
core_question.domain         → input.question.domain
core_question.key_concepts   → input.question.key_concepts
core_question.depth_expected → input.question.depth_expected
```

> `target_roles` không được gửi tới AI Service — đây là metadata để hệ thống **chọn câu hỏi phù hợp** với ứng viên, không phải để AI đánh giá.

---

### 4.4 Live Coding Question

> **Mục đích:** Bài tập lập trình. Chứa đủ thông tin để gửi tới Judge Service (chấm code) và AI Service (đánh giá chất lượng).

| Field | Type | Required | Mô tả |
|-------|------|----------|-------|
| `title` | VARCHAR | yes | Tên bài toán, ví dụ `"Two Sum"` |
| `description` | TEXT | yes | Đề bài đầy đủ, hỗ trợ Markdown |
| `time_limit_minutes` | INT | yes | Thời gian làm bài. Default: `30` |
| `optimal_time_complexity` | VARCHAR | yes | Độ phức tạp thời gian tối ưu. Ví dụ: `"O(n)"` |
| `optimal_space_complexity` | VARCHAR | yes | Độ phức tạp không gian tối ưu. Ví dụ: `"O(1)"` |
| `function_meta` | JSONB | yes | Mô tả hàm cần implement — xem chi tiết bên dưới |
| `starter_code` | JSONB | no | Template code cho 4 ngôn ngữ — xem chi tiết bên dưới |
| `test_cases` | JSONB | yes | Mảng test case — xem chi tiết bên dưới |

#### `function_meta` schema

```json
{
  "fn": "twoSum",
  "params": [
    {"name": "nums",   "type": "int[]"},
    {"name": "target", "type": "int"}
  ],
  "return": "int[]",
  "orderMatters": false,
  "inPlace": false
}
```

> Xem [Judge Service Guide](../../judge-service/docs/judge-service-guide.md) để biết đầy đủ các type hợp lệ.

#### `starter_code` schema

```json
{
  "java": "public class Solution {\n    public int[] twoSum(int[] nums, int target) {\n    }\n}",
  "python": "class Solution:\n    def twoSum(self, nums, target):\n        pass",
  "cpp": "class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n    }\n};",
  "javascript": "var twoSum = function(nums, target) {\n};"
}
```

Mỗi field tương ứng với một ngôn ngữ FE hỗ trợ. Khi candidate đổi ngôn ngữ trong UI, FE đọc đúng key tương ứng để hiển thị template.

#### `test_cases` schema

```json
[
  {
    "id": "UUID",
    "inputData": {"nums": [2, 7, 11, 15], "target": 9},
    "expectedOutput": {"result": [0, 1]},
    "is_hidden": false
  },
  {
    "id": "UUID",
    "inputData": {"nums": [3, 3], "target": 6},
    "expectedOutput": {"result": [0, 1]},
    "is_hidden": true
  }
]
```

| Field | Mô tả |
|-------|-------|
| `is_hidden: false` | Test case hiển thị cho ứng viên thấy kết quả |
| `is_hidden: true` | Test case ẩn — chỉ dùng khi chấm điểm, ứng viên không thấy |

**Mapping tới AI Service input:**
```
coding_question.title                    → input.question.title
coding_question.description              → input.question.description
base.difficulty (lowercase)              → input.question.difficulty
base.tags                                → input.question.tags
coding_question.time_limit_minutes       → input.question.time_limit_minutes
coding_question.optimal_time_complexity  → input.question.optimal_complexity.time
coding_question.optimal_space_complexity → input.question.optimal_complexity.space
```

**Mapping tới Judge Service input:**
```
coding_question.function_meta   → submission.functionMeta
coding_question.test_cases[]    → submission.testCases[]  (lọc cả hidden và visible)
```

---

## 5. Database Schema

Sử dụng **Table-per-Type** — mỗi loại câu hỏi có bảng riêng, join về bảng `questions` qua FK.

```sql
-- Base table
CREATE TABLE questions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type        VARCHAR(20)  NOT NULL,   -- BEHAVIORAL | CORE_CONCEPTUAL | LIVE_CODING
    difficulty  VARCHAR(10)  NOT NULL,   -- EASY | MEDIUM | HARD
    status      VARCHAR(10)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT | ACTIVE | INACTIVE
    tags        TEXT[]       NOT NULL DEFAULT '{}',
    created_by  UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Behavioral
CREATE TABLE behavioral_questions (
    id               UUID PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
    text             TEXT         NOT NULL,
    competency       VARCHAR(30)  NOT NULL,
    expected_signals TEXT[]       NOT NULL DEFAULT '{}',
    audio_key        VARCHAR(500)
);

-- Core Conceptual
CREATE TABLE core_questions (
    id              UUID PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
    text            TEXT         NOT NULL,
    target_roles    TEXT[]       NOT NULL DEFAULT '{}',  -- BACKEND | FRONTEND | FULLSTACK | AI | DEVOPS | MOBILE
    domain          VARCHAR(30)  NOT NULL,
    key_concepts    TEXT[]       NOT NULL DEFAULT '{}',
    depth_expected  TEXT         NOT NULL,
    audio_key       VARCHAR(500)
);

-- Live Coding
CREATE TABLE coding_questions (
    id                         UUID PRIMARY KEY REFERENCES questions(id) ON DELETE CASCADE,
    title                      VARCHAR(255) NOT NULL,
    description                TEXT         NOT NULL,
    time_limit_minutes         INT          NOT NULL DEFAULT 30,
    optimal_time_complexity    VARCHAR(50)  NOT NULL,
    optimal_space_complexity   VARCHAR(50)  NOT NULL,
    function_meta              JSONB        NOT NULL,
    starter_code               JSONB,
    test_cases                 JSONB        NOT NULL DEFAULT '[]'
);

-- Indexes
CREATE INDEX idx_questions_type       ON questions(type);
CREATE INDEX idx_questions_difficulty ON questions(difficulty);
CREATE INDEX idx_questions_status     ON questions(status);
CREATE INDEX idx_questions_tags       ON questions USING GIN(tags);

CREATE INDEX idx_behavioral_competency ON behavioral_questions(competency);
CREATE INDEX idx_core_domain           ON core_questions(domain);
CREATE INDEX idx_core_target_roles     ON core_questions USING GIN(target_roles);
CREATE INDEX idx_coding_title          ON coding_questions(title);
```

---

## 6. API Reference

Base path: `/api/v1/questions`

Authentication: Bearer JWT (tất cả endpoint đều yêu cầu, trừ health check)

---

### 6.1 Create Questions

#### `POST /api/v1/questions/behavioral`

**Request body:**
```json
{
  "difficulty": "MEDIUM",
  "tags": ["leadership", "conflict"],
  "text": "Tell me about a time you disagreed with your manager.",
  "competency": "CONFLICT_RESOLUTION",
  "expected_signals": ["shows_autonomy", "respectful_disagreement", "data_driven_approach"]
}
```

**Response `201 Created`:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "type": "BEHAVIORAL",
  "difficulty": "MEDIUM",
  "status": "DRAFT",
  "tags": ["leadership", "conflict"],
  "text": "Tell me about a time you disagreed with your manager.",
  "competency": "CONFLICT_RESOLUTION",
  "expected_signals": ["shows_autonomy", "respectful_disagreement", "data_driven_approach"],
  "audio_key": null,
  "created_at": "2026-04-04T10:00:00Z",
  "updated_at": "2026-04-04T10:00:00Z"
}
```

---

#### `POST /api/v1/questions/core`

**Request body:**
```json
{
  "difficulty": "HARD",
  "tags": ["os", "concurrency"],
  "text": "Explain the difference between process and thread. When would you use one over the other?",
  "domain": "OS",
  "key_concepts": ["memory_isolation", "context_switching", "GIL", "concurrency_vs_parallelism"],
  "depth_expected": "trade-offs, real-world use cases, language-specific implications"
}
```

**Response `201 Created`:** tương tự behavioral, có thêm các field `domain`, `key_concepts`, `depth_expected`.

---

#### `POST /api/v1/questions/coding`

**Request body:**
```json
{
  "difficulty": "EASY",
  "tags": ["array", "hash-table"],
  "title": "Two Sum",
  "description": "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to target...",
  "time_limit_minutes": 20,
  "optimal_time_complexity": "O(n)",
  "optimal_space_complexity": "O(n)",
  "function_meta": {
    "fn": "twoSum",
    "params": [
      {"name": "nums",   "type": "int[]"},
      {"name": "target", "type": "int"}
    ],
    "return": "int[]",
    "orderMatters": false,
    "inPlace": false
  },
  "starter_code": "public class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // write your solution here\n    }\n}",
  "test_cases": [
    {
      "id": "tc-001",
      "inputData": {"nums": [2, 7, 11, 15], "target": 9},
      "expectedOutput": {"result": [0, 1]},
      "is_hidden": false
    },
    {
      "id": "tc-002",
      "inputData": {"nums": [3, 3], "target": 6},
      "expectedOutput": {"result": [0, 1]},
      "is_hidden": true
    }
  ]
}
```

---

### 6.2 Read & Filter Questions

#### `GET /api/v1/questions/{id}`

Trả về câu hỏi theo ID, bao gồm tất cả fields của loại tương ứng.

**Response `200 OK`:** object câu hỏi đầy đủ.

---

#### `GET /api/v1/questions`

Filter danh sách câu hỏi.

**Query parameters:**

| Param | Ví dụ | Mô tả |
|-------|-------|-------|
| `type` | `BEHAVIORAL` | Lọc theo loại |
| `difficulty` | `MEDIUM` | Lọc theo độ khó |
| `status` | `ACTIVE` | Lọc theo trạng thái (default: `ACTIVE`) |
| `tags` | `array,dp` | Lọc theo tag (OR — có ít nhất 1 trong các tag) |
| `competency` | `LEADERSHIP` | Chỉ cho BEHAVIORAL |
| `target_roles` | `BACKEND,AI` | Chỉ cho CORE_CONCEPTUAL — OR logic, trả về câu hỏi có ít nhất 1 role khớp |
| `domain` | `DATABASE` | Chỉ cho CORE_CONCEPTUAL |
| `page` | `0` | Số trang (0-indexed) |
| `size` | `20` | Số item mỗi trang |

**Ví dụ:**
```
GET /api/v1/questions?type=BEHAVIORAL&competency=LEADERSHIP&difficulty=MEDIUM
GET /api/v1/questions?type=CORE_CONCEPTUAL&domain=DATABASE
GET /api/v1/questions?type=LIVE_CODING&tags=array,dp&difficulty=EASY
```

**Response `200 OK`:**
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "total_elements": 45,
  "total_pages": 3
}
```

---

### 6.3 Update & Delete

#### `PUT /api/v1/questions/{id}`

Cập nhật toàn bộ câu hỏi. Body schema giống POST tương ứng.

> Khi cập nhật `text` của behavioral/core question: `audio_key` tự động bị reset về `null` vì audio cũ đã lỗi thời.

**Response `200 OK`:** object câu hỏi đã cập nhật.

---

#### `PATCH /api/v1/questions/{id}/status`

Thay đổi trạng thái câu hỏi.

**Request body:**
```json
{"status": "ACTIVE"}
```

---

#### `DELETE /api/v1/questions/{id}`

Soft delete — set `status = INACTIVE`. Không xóa vật lý.

**Response `204 No Content`**

---

### 6.4 Downstream Payload Endpoints

Các endpoint này trả về payload đã được map sẵn, đúng format mà downstream service cần. Consumer không cần tự transform.

---

#### `GET /api/v1/questions/{id}/for-ai`

Trả về payload sẵn sàng gửi tới `POST /evaluate` của AI Service.

**Response cho BEHAVIORAL:**
```json
{
  "interview_type": "behavioral",
  "question": {
    "id": "550e8400-...",
    "text": "Tell me about a time you disagreed with your manager.",
    "competency": "conflict_resolution",
    "expected_signals": ["shows_autonomy", "respectful_disagreement", "data_driven_approach"]
  }
}
```

**Response cho CORE_CONCEPTUAL:**
```json
{
  "interview_type": "core_conceptual",
  "question": {
    "id": "550e8400-...",
    "text": "Explain the difference between process and thread...",
    "domain": "os",
    "key_concepts": ["memory_isolation", "context_switching", "GIL"],
    "depth_expected": "trade-offs, real-world use cases"
  }
}
```

**Response cho LIVE_CODING:**
```json
{
  "interview_type": "live_coding",
  "question": {
    "id": "550e8400-...",
    "title": "Two Sum",
    "description": "...",
    "difficulty": "easy",
    "tags": ["array", "hash-table"],
    "time_limit_minutes": 20,
    "optimal_complexity": {
      "time": "O(n)",
      "space": "O(n)"
    }
  }
}
```

> `session_id`, `submission`, `response_language` sẽ được Interview Service thêm vào trước khi gửi tới AI Service.

---

#### `GET /api/v1/questions/{id}/for-judge`

Chỉ dùng cho LIVE_CODING. Trả về payload để gửi tới Judge Service.

**Response:**
```json
{
  "language": "java",
  "functionMeta": {
    "fn": "twoSum",
    "params": [
      {"name": "nums",   "type": "int[]"},
      {"name": "target", "type": "int"}
    ],
    "return": "int[]",
    "orderMatters": false,
    "inPlace": false
  },
  "testCases": [
    {
      "id": "tc-001",
      "inputData": {"nums": [2, 7, 11, 15], "target": 9},
      "expectedOutput": {"result": [0, 1]}
    },
    {
      "id": "tc-002",
      "inputData": {"nums": [3, 3], "target": 6},
      "expectedOutput": {"result": [0, 1]}
    }
  ]
}
```

> Endpoint này trả về **tất cả** test cases (cả hidden và visible) vì Judge Service cần chấm toàn bộ. `is_hidden` field bị strip ra khỏi response.

> `submissionId` và `code` sẽ được Interview Service thêm vào trước khi gửi tới Judge Service.

---

### 6.5 Audio Management

#### `PATCH /api/v1/questions/{id}/audio`

Callback từ Storage Service để cập nhật `audio_key` sau khi TTS và upload MinIO hoàn tất.

Chỉ áp dụng cho `BEHAVIORAL` và `CORE_CONCEPTUAL`. Trả về `400` nếu gọi trên `LIVE_CODING`.

**Request body:**
```json
{"audio_key": "audio/questions/550e8400-e29b-41d4-a716-446655440000.mp3"}
```

**Response `200 OK`:**
```json
{"id": "550e8400-...", "audio_key": "audio/questions/550e8400-....mp3"}
```

---

#### `GET /api/v1/questions/{id}/audio-url`

Trả về presigned URL để play audio trực tiếp. Service này gọi Storage Service để lấy URL.

Trả về `404` nếu `audio_key` chưa được set.

**Response `200 OK`:**
```json
{
  "url": "https://minio.example.com/questions/550e8400-....mp3?X-Amz-Signature=...",
  "expires_in_seconds": 3600
}
```

---

## 7. Integration Points

### IAM Service
- Mọi request đều cần JWT hợp lệ
- Role-based: chỉ `ADMIN` hoặc `CONTENT_EDITOR` được tạo/sửa/xóa câu hỏi
- `INTERVIEWER` và `CANDIDATE` chỉ được đọc câu hỏi ở trạng thái `ACTIVE`

### Storage Service *(thiết kế sau)*
- Question Bank gọi Storage Service để trigger TTS khi cần tạo audio
- Storage Service callback về `PATCH /questions/{id}/audio` sau khi hoàn tất
- `GET /questions/{id}/audio-url` proxy qua Storage Service để lấy presigned URL

### AI Service
- Interview Service lấy payload qua `GET /questions/{id}/for-ai`
- Bổ sung `session_id`, `submission` (hoặc `answer`), `response_language`
- Gửi tới `POST /evaluate` của AI Service

### Judge Service
- Interview Service lấy payload qua `GET /questions/{id}/for-judge`
- Bổ sung `submissionId` và `code` của ứng viên
- Gửi tới Judge Service để chấm

---

## 8. Enums Reference

### QuestionType
```
BEHAVIORAL | CORE_CONCEPTUAL | LIVE_CODING
```

### Difficulty
```
EASY | MEDIUM | HARD
```

### Status
```
DRAFT    — vừa tạo, chưa sẵn sàng dùng
ACTIVE   — đang dùng trong hệ thống
INACTIVE — đã ẩn / ngừng dùng (soft delete)
```

### Competency (Behavioral)
```
CONFLICT_RESOLUTION | LEADERSHIP | OWNERSHIP | TEAMWORK
FAILURE             | GROWTH     | COMMUNICATION | PRIORITIZATION
```

### TargetRole (Core Conceptual)
```
BACKEND | FRONTEND | FULLSTACK | AI | DEVOPS | MOBILE
```

> Một câu hỏi có thể thuộc nhiều role. Ví dụ: câu hỏi về database indexing → `["BACKEND", "AI"]`.

### Domain (Core Conceptual)
```
OS | NETWORKING | DATABASE | SYSTEM_DESIGN
LANGUAGE_SPECIFIC | FRAMEWORK | SECURITY
```

> **Hai tầng phân loại của Core Conceptual:**
> - `target_roles` — câu hỏi dành cho **ai** (dùng để chọn câu hỏi phù hợp ứng viên)
> - `domain` — câu hỏi hỏi về **chủ đề gì** (dùng để phân nhóm nội dung)

---

## 9. Design Decisions

### Tại sao Table-per-Type thay vì Single Table Inheritance?

- Schema rõ ràng, từng bảng có ý nghĩa độc lập
- Không có cột `null` hàng loạt (STI sẽ có nhiều nullable column vô nghĩa cho từng type)
- Query và index hiệu quả hơn khi filter theo type-specific fields (`competency`, `domain`)
- Dễ audit và maintain

**Trade-off:** join thêm 1 bảng khi query. Chấp nhận được vì question bank không có traffic cao.

### Tại sao JSONB cho `function_meta` và `test_cases`?

- `function_meta` là cấu trúc lồng nhau (nested array of params), dùng JSONB linh hoạt hơn nhiều cột riêng lẻ
- `test_cases` có kích thước thay đổi (1 đến N test cases), JSONB phù hợp hơn bảng riêng
- Vẫn có thể query bên trong JSONB với PostgreSQL operators nếu cần sau này
- Judge Service đã dùng JSON cho toàn bộ test case payload — map 1-1 không cần transform

### Tại sao `DELETE` là soft delete?

- Câu hỏi có thể đã được dùng trong các session phỏng vấn lịch sử
- Hard delete sẽ làm mất tính toàn vẹn của lịch sử kết quả phỏng vấn
- Dùng `status = INACTIVE` để ẩn khỏi hệ thống nhưng vẫn giữ được historical data

### Tại sao `audio_key` tự reset khi update `text`?

- Audio được TTS từ `text` — nếu `text` thay đổi thì audio cũ sẽ không còn đúng
- Reset về `null` để buộc phải trigger TTS lại, tránh audio lỗi thời phát cho ứng viên
