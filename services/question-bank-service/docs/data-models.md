# Question Bank Service — Data Models

Tài liệu tra cứu nhanh các schema JSON cho request/response của từng loại câu hỏi.

---

## Table of Contents

1. [Behavioral Question](#1-behavioral-question)
2. [Core Conceptual Question](#2-core-conceptual-question)
3. [Live Coding Question](#3-live-coding-question)
4. [Downstream Payloads](#4-downstream-payloads)
   - 4.1 [for-ai payload](#41-for-ai-payload)
   - 4.2 [for-judge payload](#42-for-judge-payload)
5. [Common Enums](#5-common-enums)

---

## 1. Behavioral Question

### Create / Update Request

```json
{
  "difficulty": "EASY | MEDIUM | HARD",
  "tags": ["string"],
  "text": "string — câu hỏi, cũng là text để TTS",
  "competency": "CONFLICT_RESOLUTION | LEADERSHIP | OWNERSHIP | TEAMWORK | FAILURE | GROWTH | COMMUNICATION | PRIORITIZATION",
  "expected_signals": ["string"]
}
```

### Full Response Object

```json
{
  "id": "uuid",
  "type": "BEHAVIORAL",
  "difficulty": "MEDIUM",
  "status": "DRAFT | ACTIVE | INACTIVE",
  "tags": ["leadership", "conflict"],
  "text": "Tell me about a time you disagreed with your manager.",
  "competency": "CONFLICT_RESOLUTION",
  "expected_signals": [
    "shows_autonomy",
    "respectful_disagreement",
    "data_driven_approach",
    "outcome_oriented"
  ],
  "audio_key": "audio/questions/uuid.mp3",
  "created_by": "uuid",
  "created_at": "2026-04-04T10:00:00Z",
  "updated_at": "2026-04-04T10:00:00Z"
}
```

### Field Notes

| Field | Note |
|-------|------|
| `text` | Nguồn để TTS tạo audio. Khi update `text`, `audio_key` tự reset về `null` |
| `competency` | Một câu hỏi chỉ có một competency |
| `expected_signals` | Danh sách các tín hiệu AI phải kiểm tra — càng cụ thể càng tốt |
| `audio_key` | `null` cho đến khi Storage Service callback |

---

## 2. Core Conceptual Question

### Create / Update Request

```json
{
  "difficulty": "EASY | MEDIUM | HARD",
  "tags": ["string"],
  "text": "string — câu hỏi, cũng là text để TTS",
  "target_roles": ["BACKEND | FRONTEND | FULLSTACK | AI | DEVOPS | MOBILE"],
  "domain": "OS | NETWORKING | DATABASE | SYSTEM_DESIGN | LANGUAGE_SPECIFIC | FRAMEWORK | SECURITY",
  "key_concepts": ["string"],
  "depth_expected": "string — mô tả mức độ kỳ vọng"
}
```

### Full Response Object

```json
{
  "id": "uuid",
  "type": "CORE_CONCEPTUAL",
  "difficulty": "HARD",
  "status": "DRAFT | ACTIVE | INACTIVE",
  "tags": ["os", "concurrency"],
  "text": "Explain the difference between process and thread. When would you use one over the other?",
  "target_roles": ["BACKEND", "AI"],
  "domain": "OS",
  "key_concepts": [
    "memory_isolation",
    "context_switching",
    "GIL",
    "concurrency_vs_parallelism",
    "fork_vs_spawn"
  ],
  "depth_expected": "trade-offs, real-world use cases, language-specific implications (e.g. Python GIL)",
  "audio_key": null,
  "created_by": "uuid",
  "created_at": "2026-04-04T10:00:00Z",
  "updated_at": "2026-04-04T10:00:00Z"
}
```

### Field Notes

| Field | Note |
|-------|------|
| `text` | Nguồn để TTS tạo audio. Khi update `text`, `audio_key` tự reset về `null` |
| `target_roles` | Array — 1 câu hỏi có thể dùng cho nhiều role. Dùng để **chọn câu hỏi phù hợp** với ứng viên, không gửi tới AI Service |
| `domain` | Một câu hỏi chỉ có một domain — phân nhóm chủ đề kỹ thuật |
| `key_concepts` | AI generate `concept_coverage` map từ danh sách này — mỗi concept sẽ có `mentioned`, `correct`, `candidate_statement` trong output |
| `depth_expected` | AI dùng để calibrate `level_calibration` trong output. Nên mô tả rõ kỳ vọng |
| `audio_key` | `null` cho đến khi Storage Service callback |

### Ví dụ phân loại theo `target_roles` + `domain`

| Câu hỏi | `target_roles` | `domain` |
|---------|----------------|----------|
| "Explain B-tree index structure" | `["BACKEND", "AI"]` | `DATABASE` |
| "How does the browser render a webpage?" | `["FRONTEND"]` | `NETWORKING` |
| "What is backpropagation?" | `["AI"]` | `LANGUAGE_SPECIFIC` |
| "Explain TCP vs UDP" | `["BACKEND", "DEVOPS"]` | `NETWORKING` |
| "What is a React hook?" | `["FRONTEND", "FULLSTACK"]` | `FRAMEWORK` |
| "Explain process vs thread" | `["BACKEND", "AI", "DEVOPS"]` | `OS` |

---

## 3. Live Coding Question

### Create / Update Request

```json
{
  "difficulty": "EASY | MEDIUM | HARD",
  "tags": ["string"],
  "title": "string",
  "description": "string — markdown supported",
  "time_limit_minutes": 30,
  "optimal_time_complexity": "O(n)",
  "optimal_space_complexity": "O(1)",
  "function_meta": {
    "fn": "string — tên method trong class Solution",
    "params": [
      {"name": "string", "type": "string — xem Judge Service Guide"}
    ],
    "return": "string — return type",
    "orderMatters": false,
    "inPlace": false
  },
  "starter_code": {
    "java": "string",
    "python": "string",
    "cpp": "string",
    "javascript": "string"
  },
  "test_cases": [
    {
      "id": "string",
      "inputData": {},
      "expectedOutput": {"result": "..."},
      "is_hidden": false
    }
  ]
}
```

### Full Response Object

```json
{
  "id": "uuid",
  "type": "LIVE_CODING",
  "difficulty": "EASY",
  "status": "DRAFT | ACTIVE | INACTIVE",
  "tags": ["array", "hash-table"],
  "title": "Two Sum",
  "description": "Given an array of integers `nums` and an integer `target`, return indices of the two numbers such that they add up to `target`.\n\nYou may assume that each input would have **exactly one solution**, and you may not use the same element twice.",
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
  "starter_code": {
    "java": "public class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // write your solution here\n    }\n}",
    "python": "class Solution:\n    def twoSum(self, nums, target):\n        pass",
    "cpp": "class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n    }\n};",
    "javascript": "var twoSum = function(nums, target) {\n};"
  },
  "test_cases": [
    {
      "id": "tc-001",
      "inputData": {"nums": [2, 7, 11, 15], "target": 9},
      "expectedOutput": {"result": [0, 1]},
      "is_hidden": false
    },
    {
      "id": "tc-002",
      "inputData": {"nums": [3, 2, 4], "target": 6},
      "expectedOutput": {"result": [1, 2]},
      "is_hidden": false
    },
    {
      "id": "tc-003",
      "inputData": {"nums": [3, 3], "target": 6},
      "expectedOutput": {"result": [0, 1]},
      "is_hidden": true
    }
  ],
  "created_by": "uuid",
  "created_at": "2026-04-04T10:00:00Z",
  "updated_at": "2026-04-04T10:00:00Z"
}
```

### Field Notes

| Field | Note |
|-------|------|
| `function_meta.fn` | Phải match chính xác tên method trong `class Solution` (case-sensitive) |
| `function_meta.params[].type` | Xem bảng supported types trong [Judge Service Guide](../../judge-service/docs/judge-service-guide.md#4-naming-conventions) |
| `function_meta.inPlace` | `true` cho void methods mutate first param — set `return: "void"` khi đó |
| `test_cases[].is_hidden` | `false` = ứng viên thấy kết quả; `true` = ẩn, chỉ dùng khi chấm |
| `test_cases[].expectedOutput` | Phải là JSON object với đúng 1 field (thường dùng key `"result"`) |

---

## 4. Downstream Payloads

### 4.1 for-ai payload

`GET /api/v1/questions/{id}/for-ai` — payload sẵn sàng gửi vào AI Service.

**Behavioral:**
```json
{
  "interview_type": "behavioral",
  "question": {
    "id": "uuid",
    "text": "Tell me about a time you disagreed with your manager.",
    "competency": "conflict_resolution",
    "expected_signals": ["shows_autonomy", "respectful_disagreement", "data_driven_approach"]
  }
}
```

**Core Conceptual:**
```json
{
  "interview_type": "core_conceptual",
  "question": {
    "id": "uuid",
    "text": "Explain the difference between process and thread...",
    "domain": "os",
    "key_concepts": ["memory_isolation", "context_switching", "GIL", "concurrency_vs_parallelism"],
    "depth_expected": "trade-offs, real-world use cases, language-specific implications"
  }
}
```

**Live Coding:**
```json
{
  "interview_type": "live_coding",
  "question": {
    "id": "uuid",
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

> **Caller phải bổ sung trước khi gửi AI Service:**
> - `session_id`
> - `submission` (live_coding) hoặc `answer` (behavioral/core)
> - `response_language` (optional, default `"vi"`)

---

### 4.2 for-judge payload

`GET /api/v1/questions/{id}/for-judge` — chỉ dùng cho LIVE_CODING.

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
      "inputData": {"nums": [3, 2, 4], "target": 6},
      "expectedOutput": {"result": [1, 2]}
    },
    {
      "id": "tc-003",
      "inputData": {"nums": [3, 3], "target": 6},
      "expectedOutput": {"result": [0, 1]}
    }
  ]
}
```

> `is_hidden` bị strip ra — Judge Service nhận toàn bộ test cases (cả hidden lẫn visible).

> **Caller phải bổ sung trước khi gửi Judge Service:**
> - `submissionId`
> - `code` (code của ứng viên)

---

## 5. Common Enums

```
QuestionType  = BEHAVIORAL | CORE_CONCEPTUAL | LIVE_CODING

Difficulty    = EASY | MEDIUM | HARD

Status        = DRAFT       -- vừa tạo, chưa dùng
              | ACTIVE      -- đang hoạt động
              | INACTIVE    -- đã ẩn (soft delete)

Competency    = CONFLICT_RESOLUTION | LEADERSHIP | OWNERSHIP | TEAMWORK
              | FAILURE | GROWTH | COMMUNICATION | PRIORITIZATION

TargetRole    = BACKEND | FRONTEND | FULLSTACK | AI | DEVOPS | MOBILE
              -- dùng cho CORE_CONCEPTUAL, xác định câu hỏi dành cho role nào

Domain        = OS | NETWORKING | DATABASE | SYSTEM_DESIGN
              | LANGUAGE_SPECIFIC | FRAMEWORK | SECURITY
              -- dùng cho CORE_CONCEPTUAL, xác định chủ đề kỹ thuật
```

### Grade mapping (từ AI Service — để tham chiếu)

| Score | Grade | Hire Signal |
|-------|-------|-------------|
| 90–100 | A | strong_yes |
| 75–89  | B | yes |
| 60–74  | C | weak_yes |
| 45–59  | D | no |
| 0–44   | F | strong_no |
