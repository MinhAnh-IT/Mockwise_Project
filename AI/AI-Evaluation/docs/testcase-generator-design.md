# Testcase Generator Agent — Design Document

> **Status:** Implemented  
> **Service:** AI-Evaluation  
> **Feature:** `POST /generate-testcases`

---

## 1. Service Overview

| Item | Detail |
|---|---|
| Framework | FastAPI + LangGraph |
| LLM | `gemini-3-flash-preview` via `google-genai` + `instructor` (`GENAI_STRUCTURED_OUTPUTS` mode) |
| Thinking | `thinking_level=low` (configurable via `THINKING_LEVEL` env) |
| Pattern | LangGraph `StateGraph` — router → analyzer → generator → validator → (retry or END) |
| Auth | `X-API-Key` header (only enforced if `SERVICE_API_KEY` env is set) |
| Endpoints | `GET /health`, `POST /evaluate`, `POST /generate-testcases`, `GET /leetcode/fetch` |

### Evaluator graph (existing, for reference)
```
raw_input
    │
  [router]  ── validates input, detects interview_type
    │
    ├─ live_coding   → [live_coding_evaluator]   ─┐
    ├─ behavioral    → [behavioral_evaluator]     ─┼─ [output_validator] ─► END / retry
    └─ core_concept. → [conceptual_evaluator]    ─┘
```

---

## 2. New Feature — Testcase Generator Agent

### 2.1 Goal

Admin truyền vào đề bài (LeetCode hoặc tự định nghĩa), Agent tự động sinh ra **toàn bộ thông tin** đủ để tạo 1 `CodingQuestion` hoàn chỉnh, bao gồm:

- Thông tin câu hỏi (title, description, difficulty, tags, complexity)
- Function signature (functionMeta)
- Starter code (Python)
- Danh sách testcase (visible + hidden)

Admin **review & confirm** trên UI trước khi lưu vào `question-bank-service`.

---

## 3. Hai chế độ đầu vào

### Mode A — `custom`: Đề tự định nghĩa

Admin cung cấp đầy đủ thông tin. AI chỉ cần sinh `functionMeta`, `starterCode`, và `testCases`.

**Fields bắt buộc:** `title`, `description`, `difficulty`  
**Fields tùy chọn:** `tags`, `optimal_time_complexity`, `optimal_space_complexity` (AI tự suy nếu thiếu)

### Mode B — `leetcode`: Chỉ cần URL hoặc slug

Admin truyền `leetcode_url` (full URL hoặc bare slug). Service **fetch dữ liệu gốc trực tiếp** từ LeetCode GraphQL endpoint — không phụ thuộc vào knowledge cutoff của LLM.

Luồng thực tế:
1. `problem_analyzer` gọi `fetch_leetcode_problem()` → lấy title, description, difficulty, tags, constraints, examples.
2. LLM chỉ được dùng để **suy ra metadata kỹ thuật** (`functionMeta`, `complexity`, `starter_code`, `edge_case_hints`).
3. Các field có thể lấy trực tiếp từ LeetCode (title, description, difficulty, tags, constraints) sẽ **override output của LLM** để đảm bảo đúng ground truth.

**Premium problems:** Nếu bài là premium và không có `session_cookie`, service trả `leetcode_premium` error. Có thể override qua endpoint `GET /leetcode/fetch?url=...&session_cookie=...`.

---

## 4. API Contract

### Endpoint
```
POST /generate-testcases
Header: X-API-Key: <service_api_key>
```

### Request

#### Mode A — custom
```json
{
  "mode": "custom",
  "title": "Two Sum",
  "description": "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target. You may assume that each input would have exactly one solution, and you may not use the same element twice.",
  "difficulty": "EASY",
  "tags": ["array", "hash-table"],
  "optimal_time_complexity": "O(n)",
  "optimal_space_complexity": "O(n)",
  "num_testcases": 10,
  "num_hidden": 3
}
```

#### Mode B — leetcode
```json
{
  "mode": "leetcode",
  "leetcode_url": "https://leetcode.com/problems/two-sum/",
  "num_testcases": 10,
  "num_hidden": 3
}
```

`leetcode_url` chấp nhận full URL (`https://leetcode.com/problems/two-sum/`) hoặc bare slug (`two-sum`).

> Với `mode: leetcode`, description / difficulty / tags / constraints lấy trực tiếp từ LeetCode. AI chỉ suy ra `functionMeta`, `starter_code`, `optimal_*_complexity`, `edge_case_hints`.

---

### Response (giống nhau cho cả hai mode)

Output là **toàn bộ data** sẵn sàng để POST vào `question-bank-service` sau khi Admin review.

```json
{
  "title": "Two Sum",
  "description": "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target. You may assume that each input would have exactly one solution, and you may not use the same element twice.",
  "difficulty": "EASY",
  "tags": ["array", "hash-table"],
  "time_limit_minutes": 20,
  "optimal_time_complexity": "O(n)",
  "optimal_space_complexity": "O(n)",
  "function_meta": {
    "fn": "twoSum",
    "params": [
      { "name": "nums", "type": "int[]" },
      { "name": "target", "type": "int" }
    ],
    "return": "int[]",
    "orderMatters": false,
    "inPlace": false
  },
  "starter_code": "def twoSum(nums: list[int], target: int) -> list[int]:\n    pass",
  "testcases": [
    {
      "id": "tc-1",
      "label": "Sample — basic example",
      "inputData": { "nums": [2, 7, 11, 15], "target": 9 },
      "expectedOutput": { "result": [0, 1] },
      "is_hidden": false,
      "note": "Classic example from problem statement"
    },
    {
      "id": "tc-2",
      "label": "Edge — two elements only",
      "inputData": { "nums": [3, 3], "target": 6 },
      "expectedOutput": { "result": [0, 1] },
      "is_hidden": false,
      "note": "Minimum valid input size"
    },
    {
      "id": "tc-3",
      "label": "Edge — negative numbers",
      "inputData": { "nums": [-3, 4, 3, 90], "target": 0 },
      "expectedOutput": { "result": [0, 2] },
      "is_hidden": false,
      "note": "Tests handling of negative values"
    },
    {
      "id": "tc-8",
      "label": "Hidden — large input performance",
      "inputData": { "nums": [1, 2, 3, 4, 9996, 9997, 9998, 9999, 10000], "target": 19999 },
      "expectedOutput": { "result": [7, 8] },
      "is_hidden": true,
      "note": "Tests O(n) vs O(n²) — brute force will TLE"
    }
  ],
  "meta": {
    "mode": "leetcode",
    "leetcode_number": 1,
    "generated_at": "2026-04-23T10:00:00Z",
    "model_version": "evaluator-v1.0",
    "generation_duration_ms": 30636,
    "total_testcases": 10,
    "visible_testcases": 7,
    "hidden_testcases": 3
  },
  "warning": null
}
```

- `meta.leetcode_number` được điền từ `questionFrontendId` của LeetCode (chỉ khi `mode=leetcode`), không phải từ request.
- `warning` là `null` khi thành công; có giá trị `"max_retries_exceeded (...)"` khi validator exhausted và trả partial result.

**Error responses:**
| Status | `error` code | Khi nào |
|---|---|---|
| 400 | `input_validation_failed` | Pydantic validation fail (thiếu field, `num_hidden >= num_testcases`...) |
| 403 | `leetcode_premium` | Bài premium, cần `session_cookie` |
| 404 | `leetcode_not_found` | URL/slug không tồn tại |
| 422 | `leetcode_fetch_failed` / `problem_analysis_failed` / `generation_failed` | Lỗi trong pipeline |
| 500 | — | Unhandled exception |

**Flow:** Admin nhận response → review/chỉnh sửa trên UI → nhấn "Apply" → FE POST thẳng vào `question-bank-service`.

---

## 5. Pydantic Models

### Input — `models/generator_inputs.py`

```python
class GenerateTestcasesRequest(BaseModel):
    mode: Literal["custom", "leetcode"]

    # Mode: leetcode — chỉ cần URL hoặc slug
    leetcode_url: Optional[str] = None          # "https://leetcode.com/problems/two-sum/" hoặc "two-sum"

    # Mode: custom — bắt buộc title + description
    title: Optional[str] = None
    description: Optional[str] = None
    difficulty: Optional[str] = None                  # EASY | MEDIUM | HARD (AI infer nếu thiếu)
    tags: List[str] = []
    optimal_time_complexity: Optional[str] = None     # AI infer nếu thiếu
    optimal_space_complexity: Optional[str] = None    # AI infer nếu thiếu

    # Chung cho cả hai mode
    num_testcases: int = 10
    num_hidden: int = 3

    @model_validator(mode="after")
    def validate_by_mode(self):
        if self.mode == "leetcode" and not self.leetcode_url:
            raise ValueError("leetcode_url is required for mode=leetcode")
        if self.mode == "custom" and (not self.title or not self.description):
            raise ValueError("title and description are required for mode=custom")
        if self.num_hidden >= self.num_testcases:
            raise ValueError("num_hidden must be less than num_testcases")
        return self
```

### Output — `models/generator_outputs.py`

```python
class ParamMeta(BaseModel):
    name: str
    type: str

class FunctionMeta(BaseModel):
    """Serialize with .model_dump(by_alias=True) so `return_type` → `"return"`."""
    model_config = ConfigDict(populate_by_name=True)

    fn: str
    params: List[ParamMeta]
    return_type: str = Field(serialization_alias="return")
    orderMatters: bool
    inPlace: bool

class GeneratedTestCase(BaseModel):
    id: str
    label: str                       # "Edge — empty array", "Sample — basic"
    inputData: Dict[str, Any]
    expectedOutput: Dict[str, Any]
    is_hidden: bool
    note: str                        # lý do testcase này quan trọng

class GeneratedMeta(BaseModel):
    mode: str
    leetcode_number: Optional[int] = None      # từ LeetCode questionFrontendId
    generated_at: str
    model_version: str
    generation_duration_ms: int
    total_testcases: int
    visible_testcases: int
    hidden_testcases: int

class GenerateTestcasesResponse(BaseModel):
    title: str
    description: str
    difficulty: str                  # EASY | MEDIUM | HARD
    tags: List[str]
    time_limit_minutes: int
    optimal_time_complexity: str
    optimal_space_complexity: str
    function_meta: FunctionMeta
    starter_code: str                # Python
    testcases: List[GeneratedTestCase]
    meta: GeneratedMeta
    warning: Optional[str] = None    # set khi validator exhausted retries
```

---

## 6. Agent Graph

### Graph: `generator/graph.py` (tách riêng, không ảnh hưởng evaluator hiện tại)

```
raw_input
    │
[generator_router]
    │   Validate input theo mode
    │   mode=leetcode → enriched_input chỉ có number + title
    │   mode=custom   → enriched_input có đủ fields
    │   Lỗi validation → trả về error ngay (END)
    │
[problem_analyzer]
    │   mode=leetcode: fetch real problem via LeetCode GraphQL (fetch_leetcode_problem)
    │                  → lưu vào state["leetcode_problem"]
    │                  → lỗi fetch trả final_output ngay (leetcode_not_found / leetcode_premium)
    │   Sau đó LLM sinh ra:
    │   - functionMeta (fn, params, return, orderMatters, inPlace)
    │   - starter_code (Python)
    │   - optimal_time_complexity, optimal_space_complexity (hoặc dùng input nếu custom có)
    │   - time_limit_minutes
    │   - constraints & edge_case_hints (dùng nội bộ cho bước tiếp theo)
    │   Override: mode=leetcode — title/description/difficulty/tags/constraints lấy từ LeetCode
    │             mode=custom  — difficulty/tags/complexity lấy từ input nếu có
    │
[testcase_generator]
    │   AI sinh testcases đủ num_testcases với phân bổ:
    │   ┌─ 1–2 Sample cases  (visible, lấy từ ví dụ đề bài)
    │   ├─ 2–3 Edge cases    (visible, empty/single/negative/overflow...)
    │   ├─ N   Normal cases  (visible, random valid inputs)
    │   └─ num_hidden Hidden cases (is_hidden=true, large/complex inputs)
    │
[testcase_validator]
    │   Kiểm tra output:
    │   - Đủ num_testcases?
    │   - hidden_count == num_hidden?
    │   - Không trùng inputData?
    │   - expectedOutput không rỗng?
    │   → needs_retry=true nếu vi phạm
    │   → vượt MAX_RETRIES: trả partial result + warning
    │
   END
```

### State: `generator/state.py`

```python
class GeneratorState(TypedDict):
    raw_input: dict
    validated_input: Optional[GenerateTestcasesRequest]
    leetcode_problem: Optional[dict]    # fetched LeetCode data (mode=leetcode only)
    problem_analysis: Optional[dict]    # structured output của problem_analyzer
    raw_testcases: Optional[list]       # structured output của testcase_generator
    final_output: Optional[dict]
    retry_count: int
    needs_retry: bool
    generation_error: Optional[str]
    generation_start_ms: Optional[int]
```

---

## 7. Prompt Strategy

### `problem_analyzer` prompt (cho cả hai mode)

**Mode leetcode:** Prompt chứa dữ liệu LeetCode đã fetch (title, description, difficulty, tags, constraints, examples). LLM chỉ suy ra metadata kỹ thuật (`functionMeta`, `starter_code`, `complexity`, `edge_case_hints`). Sau khi LLM trả output, code ghi đè `title / description / difficulty / tags / constraints` bằng dữ liệu LeetCode gốc (xem `problem_analyzer.py` §3).

**Mode custom:** Prompt nhận `title + description` từ input, LLM suy ra toàn bộ metadata kỹ thuật. Các field có trong request (`difficulty`, `tags`, `complexity`) override output của LLM sau đó.

Output structured (Pydantic):
```json
{
  "title": "Two Sum",
  "description": "...",
  "difficulty": "EASY",
  "tags": ["array", "hash-table"],
  "time_limit_minutes": 20,
  "optimal_time_complexity": "O(n)",
  "optimal_space_complexity": "O(n)",
  "fn": "twoSum",
  "params": [{"name": "nums", "type": "int[]"}, {"name": "target", "type": "int"}],
  "return_type": "int[]",
  "order_matters": false,
  "in_place": false,
  "starter_code": "def twoSum(nums: list[int], target: int) -> list[int]:\n    pass",
  "constraints": ["2 <= nums.length <= 10^4", "-10^9 <= nums[i] <= 10^9"],
  "edge_case_hints": ["negative numbers", "duplicate values", "minimal length array"]
}
```

### `testcase_generator` prompt

Nhận output của `problem_analyzer` + `num_testcases` + `num_hidden`, yêu cầu AI sinh testcases với phân bổ rõ ràng:

```
Sinh đúng {num_testcases} testcases với phân bổ bắt buộc:
- Visible ({num_testcases - num_hidden} cases):
  + 1–2 Sample: lấy thẳng từ ví dụ trong đề
  + 2–3 Edge: empty input, single element, negatives, duplicates, max constraints
  + Còn lại: normal random valid inputs
- Hidden ({num_hidden} cases, is_hidden=true):
  + Large inputs (gần constraint tối đa)
  + Complex cases phân biệt brute force vs optimal

Yêu cầu:
- Không trùng inputData giữa các testcase
- expectedOutput phải đúng về mặt logic (tự tính)
- Mỗi testcase có label mô tả ngắn và note giải thích
```

---

## 8. File Structure

```
AI-Evaluation/
├── api.py                              ← POST /generate-testcases, GET /leetcode/fetch
├── config.py                           ← env: MODEL_NAME, THINKING_LEVEL, GENERATOR_MAX_RETRIES...
├── main.py                             ← entry points (evaluate, generate_testcases)
├── llm.py                              ← shared Gemini helper (call_structured)
│
├── generator/
│   ├── __init__.py
│   ├── graph.py                        ← build_generator_graph()
│   ├── state.py                        ← GeneratorState
│   ├── leetcode_fetcher.py             ← fetch_leetcode_problem() via GraphQL
│   ├── nodes/
│   │   ├── __init__.py
│   │   ├── generator_router.py         ← validate & init state
│   │   ├── problem_analyzer.py         ← fetch LeetCode (nếu cần) + LLM suy metadata
│   │   ├── testcase_generator.py       ← LLM sinh testcases
│   │   └── testcase_validator.py       ← validate output, retry, assemble response
│   └── prompts/
│       ├── __init__.py
│       ├── problem_analysis.py
│       └── testcase_generation.py
│
├── agent/                              ← Evaluator (LiveCoding/Behavioral/Conceptual)
│   └── ...
│
└── models/
    ├── common.py
    ├── inputs.py                       ← evaluator inputs
    ├── outputs.py                      ← evaluator outputs
    ├── generator_inputs.py             ← GenerateTestcasesRequest
    └── generator_outputs.py            ← GenerateTestcasesResponse
```

---

## 9. Integration Flow

```
Admin UI
  │
  ├─ [1] POST /generate-testcases  →  AI-Evaluation Service
  │       Mode leetcode: { mode, leetcode_url, num_testcases, num_hidden }
  │       Mode custom:   { mode, title, description, difficulty, ..., num_testcases, num_hidden }
  │
  │       Response: full CodingQuestion data + testcases[]
  │
  ├─ [2] Admin review trên UI
  │       - Xem từng testcase (label, inputData, expectedOutput, note)
  │       - Sửa / xóa / thêm testcase thủ công nếu cần
  │       - Điều chỉnh visibility (is_hidden toggle)
  │
  └─ [3] Admin nhấn "Apply"
          FE gọi POST /admin/questions/coding  →  question-bank-service
          Body = CodingQuestionRequest (map từ response bước 1 + edits của admin)
```

---

## 10. AI-Generated Fields — Format Constraints

Tất cả các fields dưới đây đều do AI sinh ra trong cả hai mode (`leetcode` và `custom`). Format phải khớp **chính xác** với schema của `question-bank-service`.

### `functionMeta`

Khớp với entity `FunctionMeta.java`:

```json
{
  "fn": "twoSum",
  "params": [
    { "name": "nums", "type": "int[]" },
    { "name": "target", "type": "int" }
  ],
  "return": "int[]",
  "orderMatters": false,
  "inPlace": false
}
```

| Field | Quy tắc |
|---|---|
| `fn` | camelCase, đúng tên hàm theo convention ngôn ngữ (Python: snake_case) |
| `params[].name` | tên tham số đúng theo đề bài |
| `params[].type` | dùng type string chuẩn: `int`, `int[]`, `int[][]`, `string`, `string[]`, `boolean`, `ListNode`, `TreeNode`, `float`... |
| `return` | **key là `"return"`** (JSON alias, không phải `return_type`) |
| `orderMatters` | `true` nếu output là mảng mà thứ tự phần tử quan trọng |
| `inPlace` | `true` nếu hàm modify input array/list thay vì return giá trị mới |

### `starterCode`

- Ngôn ngữ: **Python**
- Chỉ gồm function signature + `pass`, không có import, không có docstring
- Dùng type hints Python chuẩn

```python
# Ví dụ đúng:
def twoSum(nums: list[int], target: int) -> list[int]:
    pass

# Ví dụ đúng (in-place):
def sortColors(nums: list[int]) -> None:
    pass

# Ví dụ đúng (nested):
def merge(intervals: list[list[int]]) -> list[list[int]]:
    pass
```

### `optimalTimeComplexity` / `optimalSpaceComplexity`

- Format: **Big-O notation chuẩn**, string
- Dùng `n` là kích thước input chính, `k` cho constraint phụ nếu cần
- Ví dụ hợp lệ: `"O(1)"`, `"O(n)"`, `"O(n log n)"`, `"O(n²)"`, `"O(n + k)"`, `"O(2^n)"`
- Không dùng: `"O(N)"`, `"linear"`, `"constant"`, `"O(n^2)"` (dùng `²` thay `^2`)

### `difficulty`

Phải là một trong: `"EASY"`, `"MEDIUM"`, `"HARD"` (uppercase, khớp enum `Difficulty` trong hệ thống)

### `timeLimitMinutes`

AI tự estimate theo difficulty:
| Difficulty | Time limit |
|---|---|
| EASY | 20 phút |
| MEDIUM | 30 phút |
| HARD | 45 phút |

### `tags`

- Lowercase, dùng hyphen: `"hash-table"`, `"two-pointers"`, `"dynamic-programming"`
- Tối đa 5 tags
- Ưu tiên tags chuẩn LeetCode: `array`, `string`, `hash-table`, `tree`, `graph`, `dynamic-programming`, `binary-search`, `two-pointers`, `sliding-window`, `stack`, `queue`, `heap`, `linked-list`, `math`, `bit-manipulation`, `backtracking`, `greedy`, `sorting`

---

## 11. Validation Rules — `testcase_validator`

| Rule | Hành động |
|---|---|
| `len(testcases) != num_testcases` | retry |
| `sum(is_hidden) != num_hidden` | retry |
| Hai testcase có `inputData` giống nhau (so sánh canonicalized JSON) | retry |
| Bất kỳ `expectedOutput` nào rỗng `{}` | retry |
| Vượt `GENERATOR_MAX_RETRIES` (default `1`) | trả partial result + `warning: "max_retries_exceeded (...)"` |
| `raw_testcases` = None (generator fail) + vượt retry | trả `error: generation_failed` |

Duplicate check dùng `json.dumps(inputData, sort_keys=True)` để tránh false positive khi LLM đổi thứ tự key.

---

## 12. Fields mapping — Response → CodingQuestionRequest

| Response field | CodingQuestionRequest field |
|---|---|
| `title` | `title` |
| `description` | `description` |
| `difficulty` | `difficulty` |
| `tags` | `tags` |
| `time_limit_minutes` | `timeLimitMinutes` |
| `optimal_time_complexity` | `optimalTimeComplexity` |
| `optimal_space_complexity` | `optimalSpaceComplexity` |
| `function_meta` | `functionMeta` |
| `starter_code` | `starterCode` |
| `testcases[].inputData` | `testCases[].inputData` |
| `testcases[].expectedOutput` | `testCases[].expectedOutput` |
| `testcases[].is_hidden` | `testCases[].is_hidden` |
| `testcases[].label` + `note` | chỉ dùng cho Admin UI review, không lưu vào DB |

---

## 13. Configuration — Environment Variables

| Env | Default | Dùng cho |
|---|---|---|
| `GOOGLE_API_KEY` | — | Gemini API key (bắt buộc) |
| `MODEL_NAME` | `gemini-3-flash-preview` | Model gọi cả analyzer + generator |
| `THINKING_LEVEL` | `low` | `minimal` / `low` / `medium` / `high` (Gemini 3) — auto-map sang `thinking_budget` nếu dùng Gemini 2.x |
| `GENERATOR_MAX_RETRIES` | `1` | Số lần retry tối đa của `testcase_validator` |
| `MAX_RETRIES` | `2` | Retry cho evaluator agent (`/evaluate`) |
| `MODEL_VERSION` | `evaluator-v1.0` | Ghi vào `meta.model_version` của response |
| `SERVICE_API_KEY` | — | Nếu set, `/generate-testcases` & `/evaluate` yêu cầu `X-API-Key` header |

Tất cả LLM call đi qua `llm.call_structured(prompt, response_model)` — thay model / thinking level chỉ cần sửa `.env`, không cần sửa code.

---

## 14. Performance

Đo trên `leetcode/group-anagrams` với `num_testcases=20, num_hidden=3`:

| Config | Duration | Note |
|---|---|---|
| `gemini-2.5-flash` + thinking mặc định + `GENAI_TOOLS` mode | ~700s | Setup ban đầu — 4 lần retry do duplicate false-positive |
| `gemini-3-flash-preview` + `thinking_level=low` + `GENAI_STRUCTURED_OUTPUTS` | ~30s | Setup hiện tại |

Các cải thiện chính khiến giảm ~23×:
1. **`GENAI_STRUCTURED_OUTPUTS` mode** — schema-bound JSON, không còn tool-calling round-trip.
2. **`thinking_level=low`** — tránh chi phí reasoning mặc định khá nặng của Gemini 3/2.5.
3. **Canonicalized duplicate check** — hết false positive → giảm retry từ 3 → 0.
4. **`GENERATOR_MAX_RETRIES=1`** — structured output đáng tin hơn, không cần 3 retry.

Nếu muốn trade-off:
- Tăng chất lượng → `MODEL_NAME=gemini-3-pro-preview` hoặc `THINKING_LEVEL=medium` (chậm hơn).
- Nhanh hơn nữa → `THINKING_LEVEL=minimal`.
