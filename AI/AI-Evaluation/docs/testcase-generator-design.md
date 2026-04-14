# Testcase Generator Agent — Design Document

> **Status:** Draft — awaiting review  
> **Service:** AI-Evaluation  
> **Feature:** `POST /generate-testcases`

---

## 1. Existing Service Overview

| Item | Detail |
|---|---|
| Framework | FastAPI + LangGraph |
| LLM | Gemini 2.0 Flash via `google-genai` + `instructor` (structured output) |
| Pattern | LangGraph `StateGraph` — router → evaluator → output_validator → (retry or END) |
| Auth | `X-API-Key` header |
| Current endpoints | `GET /health`, `POST /evaluate` |

### Existing graph flow
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

### Mode B — `leetcode`: Chỉ cần số thứ tự + tên bài

Admin chỉ truyền `leetcode_number` + `leetcode_title`. AI tự recall toàn bộ thông tin bài từ knowledge base (Gemini đã được train trên LeetCode problems) và sinh ra **tất cả fields** cho CodingQuestion.

**Caveat:** Các bài mới hơn training cutoff (Aug 2025) sẽ không được nhận diện → dùng `mode: custom` thay thế.

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
  "leetcode_number": 1,
  "leetcode_title": "Two Sum",
  "num_testcases": 10,
  "num_hidden": 3
}
```

> Với `mode: leetcode`, tất cả các field còn lại (description, difficulty, tags, complexity...) đều do AI tự sinh — Admin không cần cung cấp thêm gì.

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
    "generated_at": "2026-04-04T10:00:00Z",
    "model_version": "evaluator-v1.0",
    "generation_duration_ms": 1842,
    "total_testcases": 10,
    "visible_testcases": 7,
    "hidden_testcases": 3
  }
}
```

**Flow:** Admin nhận response → review/chỉnh sửa trên UI → nhấn "Apply" → FE POST thẳng vào `question-bank-service`.

---

## 5. Pydantic Models

### Input — `models/generator_inputs.py`

```python
class GenerateTestcasesRequest(BaseModel):
    mode: Literal["custom", "leetcode"]

    # Mode: leetcode — chỉ cần 2 fields này
    leetcode_number: Optional[int] = None       # e.g. 1
    leetcode_title: Optional[str] = None        # e.g. "Two Sum"

    # Mode: custom — bắt buộc title + description
    title: Optional[str] = None
    description: Optional[str] = None
    difficulty: Optional[str] = None            # EASY | MEDIUM | HARD (AI tự suy nếu thiếu)
    tags: List[str] = []
    optimal_time_complexity: Optional[str] = None   # AI tự suy nếu thiếu
    optimal_space_complexity: Optional[str] = None  # AI tự suy nếu thiếu

    # Chung cho cả hai mode
    num_testcases: int = 10
    num_hidden: int = 3

    @model_validator(mode="after")
    def validate_by_mode(self):
        if self.mode == "leetcode":
            if not self.leetcode_number or not self.leetcode_title:
                raise ValueError("leetcode_number and leetcode_title are required for mode=leetcode")
        if self.mode == "custom":
            if not self.title or not self.description:
                raise ValueError("title and description are required for mode=custom")
        return self
```

### Output — `models/generator_outputs.py`

```python
class ParamMeta(BaseModel):
    name: str
    type: str

class FunctionMeta(BaseModel):
    fn: str
    params: List[ParamMeta]
    return_type: str = Field(alias="return")
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
    leetcode_number: Optional[int]
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
    │   AI phân tích/recall đề bài và sinh ra:
    │   - description (leetcode mode: AI recall; custom mode: từ input)
    │   - difficulty, tags, time_limit_minutes
    │   - optimal_time_complexity, optimal_space_complexity
    │   - functionMeta (fn, params, return, orderMatters, inPlace)
    │   - starter_code (Python)
    │   - constraints & edge_case_hints (dùng nội bộ cho bước tiếp theo)
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
    problem_analysis: Optional[dict]    # structured output của problem_analyzer
    raw_output: Optional[GenerateTestcasesResponse]
    final_output: Optional[dict]
    retry_count: int
    needs_retry: bool
    generation_error: Optional[str]
    generation_start_ms: Optional[int]
```

---

## 7. Prompt Strategy

### `problem_analyzer` prompt (cho cả hai mode)

**Mode leetcode:** Prompt yêu cầu AI recall bài LeetCode theo số thứ tự + tên, trả về full structured output.

**Mode custom:** Prompt nhận `title + description` đã có, chỉ yêu cầu AI suy ra `functionMeta`, `complexity`, `constraints`, `edge_case_hints`.

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
├── api.py                              ← MODIFY: thêm POST /generate-testcases
├── config.py                           ← không đổi
├── main.py                             ← không đổi
│
├── generator/                          ← NEW package
│   ├── __init__.py
│   ├── graph.py                        ← build_generator_graph()
│   ├── state.py                        ← GeneratorState
│   ├── nodes/
│   │   ├── __init__.py
│   │   ├── generator_router.py         ← validate & normalize input
│   │   ├── problem_analyzer.py         ← AI recall/phân tích đề bài
│   │   ├── testcase_generator.py       ← AI sinh testcases
│   │   └── testcase_validator.py       ← validate output, trigger retry
│   └── prompts/
│       ├── __init__.py
│       ├── problem_analysis.py         ← prompt cho problem_analyzer
│       └── testcase_generation.py      ← prompt cho testcase_generator
│
└── models/
    ├── common.py                       ← không đổi
    ├── inputs.py                       ← không đổi
    ├── outputs.py                      ← không đổi
    ├── generator_inputs.py             ← NEW: GenerateTestcasesRequest
    └── generator_outputs.py            ← NEW: GenerateTestcasesResponse
```

---

## 9. Integration Flow

```
Admin UI
  │
  ├─ [1] POST /generate-testcases  →  AI-Evaluation Service
  │       Mode leetcode: { mode, leetcode_number, leetcode_title, num_testcases, num_hidden }
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
| `len(testcases) < num_testcases` | retry |
| `sum(is_hidden) != num_hidden` | retry |
| Hai testcase có `inputData` giống nhau | retry |
| Bất kỳ `expectedOutput` nào rỗng `{}` | retry |
| Vượt `MAX_RETRIES` | trả partial result + `"warning": "max_retries_exceeded"` |

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
