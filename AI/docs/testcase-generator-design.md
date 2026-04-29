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
| **JSON convention** | **camelCase only** for `/generate-testcases` (snake_case payloads are rejected) |

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
- Function signature (`functionMeta`) — đúng theo schema judge-service
- Starter code cho **4 ngôn ngữ**: Python, Java, C++, JavaScript
- Danh sách testcase (visible + hidden)

Admin **review & confirm** trên UI trước khi lưu vào `question-bank-service`.

---

## 3. Hai chế độ đầu vào

### Mode A — `custom`: Đề tự định nghĩa

Admin cung cấp đầy đủ thông tin. AI chỉ cần sinh `functionMeta`, `starterCode`, và `testcases`.

**Fields bắt buộc:** `title`, `description`
**Fields tùy chọn:** `difficulty`, `tags`, `optimalTimeComplexity`, `optimalSpaceComplexity` (AI tự suy nếu thiếu)

### Mode B — `leetcode`: Chỉ cần URL hoặc slug

Admin truyền `leetcodeUrl` (full URL hoặc bare slug). Service **fetch dữ liệu gốc trực tiếp** từ LeetCode GraphQL endpoint — không phụ thuộc vào knowledge cutoff của LLM.

Luồng thực tế:
1. `problem_analyzer` gọi `fetch_leetcode_problem()` → lấy title, description, difficulty, tags, constraints, examples, **starter code chính thức cho cả 4 ngôn ngữ** (`python3`, `java`, `cpp`, `javascript`).
2. LLM chỉ được dùng để **suy ra metadata kỹ thuật** (`functionMeta`, `complexity`, `edgeCaseHints`) và để **chuẩn hoá starter code** (giữ verbatim phần LeetCode đã cung cấp).
3. Các field có thể lấy trực tiếp từ LeetCode (title, description, difficulty, tags, constraints) sẽ **override output của LLM** để đảm bảo đúng ground truth.

**Premium problems:** Nếu bài là premium và không có `session_cookie`, service trả `leetcode_premium` error. Có thể override qua endpoint `GET /leetcode/fetch?url=...&session_cookie=...`.

---

## 4. API Contract

### Endpoint
```
POST /generate-testcases
Header: X-API-Key: <SERVICE_API_KEY>
Content-Type: application/json
```

### Request — camelCase only

#### Mode A — custom
```json
{
  "mode": "custom",
  "title": "Two Sum",
  "description": "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target.",
  "difficulty": "EASY",
  "tags": ["array", "hash-table"],
  "optimalTimeComplexity": "O(n)",
  "optimalSpaceComplexity": "O(n)",
  "numTestcases": 10,
  "numVisible": 3
}
```

#### Mode B — leetcode
```json
{
  "mode": "leetcode",
  "leetcodeUrl": "https://leetcode.com/problems/two-sum/",
  "numTestcases": 10,
  "numVisible": 3
}
```

`leetcodeUrl` chấp nhận full URL (`https://leetcode.com/problems/two-sum/`) hoặc bare slug (`two-sum`).

> Với `mode: leetcode`, description / difficulty / tags / constraints lấy trực tiếp từ LeetCode. AI chỉ suy ra `functionMeta`, `optimalTimeComplexity`, `optimalSpaceComplexity`, `edgeCaseHints` và chuẩn hoá `starterCode`.

#### `numVisible` semantics

- `numVisible` = số testcase **hiển thị** cho candidate.
- `numHidden` được server tính tự động: `numTestcases - numVisible`.
- Validation: `1 ≤ numVisible < numTestcases`. Default `numTestcases=10`, `numVisible=3` → 7 hidden (ẩn nhiều hơn hiển thị, đúng tinh thần “hidden là phần chính để chấm”).

---

### Response (giống nhau cho cả hai mode)

Output là **toàn bộ data** sẵn sàng để POST vào `question-bank-service` sau khi Admin review. **Tất cả field name là camelCase**, riêng `functionMeta.return` giữ nguyên key `"return"` để khớp đúng schema của judge-service.

```json
{
  "title": "Two Sum",
  "description": "Given an array of integers nums and an integer target, return indices of the two numbers such that they add up to target.",
  "difficulty": "EASY",
  "tags": ["array", "hash-table"],
  "timeLimitMinutes": 20,
  "optimalTimeComplexity": "O(n)",
  "optimalSpaceComplexity": "O(n)",
  "functionMeta": {
    "fn": "twoSum",
    "params": [
      { "name": "nums", "type": "int[]" },
      { "name": "target", "type": "int" }
    ],
    "return": "int[]",
    "orderMatters": false,
    "inPlace": false
  },
  "starterCode": {
    "python": "class Solution:\n    def twoSum(self, nums: list[int], target: int) -> list[int]:\n        pass",
    "java": "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        // TODO\n        return new int[]{};\n    }\n}",
    "cpp": "class Solution {\npublic:\n    vector<int> twoSum(vector<int>& nums, int target) {\n        // TODO\n        return {};\n    }\n};",
    "javascript": "/**\n * @param {number[]} nums\n * @param {number} target\n * @return {number[]}\n */\nvar twoSum = function(nums, target) {\n    // TODO\n};"
  },
  "testcases": [
    {
      "id": "tc-1",
      "label": "Sample — basic example",
      "inputData": { "nums": [2, 7, 11, 15], "target": 9 },
      "expectedOutput": { "result": [0, 1] },
      "isHidden": false,
      "note": "Classic example from problem statement"
    },
    {
      "id": "tc-2",
      "label": "Edge — two elements only",
      "inputData": { "nums": [3, 3], "target": 6 },
      "expectedOutput": { "result": [0, 1] },
      "isHidden": false,
      "note": "Minimum valid input size"
    },
    {
      "id": "tc-8",
      "label": "Hidden — large input performance",
      "inputData": { "nums": [1, 2, 3, 4, 9996, 9997, 9998, 9999, 10000], "target": 19999 },
      "expectedOutput": { "result": [7, 8] },
      "isHidden": true,
      "note": "Distinguishes O(n) from O(n²) — brute force will TLE"
    }
  ],
  "meta": {
    "mode": "leetcode",
    "leetcodeNumber": 1,
    "generatedAt": "2026-04-23T10:00:00Z",
    "modelVersion": "evaluator-v1.0",
    "generationDurationMs": 30636,
    "totalTestcases": 10,
    "visibleTestcases": 3,
    "hiddenTestcases": 7
  },
  "warning": null
}
```

- `meta.leetcodeNumber` được điền từ `questionFrontendId` của LeetCode (chỉ khi `mode=leetcode`), không phải từ request.
- `warning` là `null` khi thành công; có giá trị `"max_retries_exceeded (...)"` khi validator exhausted và trả partial result.

**Error responses:**

| Status | `error` code | Khi nào |
|---|---|---|
| 400 | `input_validation_failed` | Pydantic validation fail (thiếu field, gửi snake_case, `numVisible >= numTestcases`...) |
| 403 | `leetcode_premium` | Bài premium, cần `session_cookie` |
| 404 | `leetcode_not_found` | URL/slug không tồn tại |
| 422 | `leetcode_fetch_failed` / `problem_analysis_failed` / `generation_failed` | Lỗi trong pipeline |
| 500 | — | Unhandled exception |

**Flow:** Admin nhận response → review/chỉnh sửa trên UI → nhấn "Apply" → FE POST thẳng vào `question-bank-service`.

---

## 5. Pydantic Models

### Input — `models/generator_inputs.py`

JSON request **chỉ chấp nhận camelCase**. Python field name vẫn snake_case để code server-side Pythonic; alias do `to_camel` generator sinh ra.

```python
from pydantic import BaseModel, ConfigDict, model_validator
from pydantic.alias_generators import to_camel

class GenerateTestcasesRequest(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=False,   # snake_case payloads are REJECTED
    )

    mode: Literal["custom", "leetcode"]

    # Mode: leetcode — chỉ cần URL hoặc slug
    leetcode_url: Optional[str] = None          # JSON: leetcodeUrl

    # Mode: custom — bắt buộc title + description
    title: Optional[str] = None
    description: Optional[str] = None
    difficulty: Optional[str] = None
    tags: List[str] = []
    optimal_time_complexity: Optional[str] = None    # JSON: optimalTimeComplexity
    optimal_space_complexity: Optional[str] = None   # JSON: optimalSpaceComplexity

    # Chung cho cả hai mode
    num_testcases: int = 10                          # JSON: numTestcases
    num_visible: int = 3                             # JSON: numVisible

    @model_validator(mode="after")
    def validate_by_mode(self):
        if self.mode == "leetcode" and not self.leetcode_url:
            raise ValueError("leetcodeUrl is required for mode=leetcode")
        if self.mode == "custom" and (not self.title or not self.description):
            raise ValueError("title and description are required for mode=custom")
        if self.num_visible < 1:
            raise ValueError("numVisible must be at least 1")
        if self.num_visible >= self.num_testcases:
            raise ValueError("numVisible must be less than numTestcases")
        return self

    @property
    def num_hidden(self) -> int:
        return self.num_testcases - self.num_visible
```

### Output — `models/generator_outputs.py`

Tất cả model output gắn `alias_generator=to_camel` + `populate_by_name=True`. Khi serialize phải dùng `model_dump(by_alias=True)` (đã làm sẵn trong `_build_final_response`). `FunctionMeta.return_type` dùng `Field(alias="return")` để override generator giữ key `"return"` đúng schema judge-service.

```python
class ParamMeta(BaseModel):
    name: str
    type: str

class FunctionMeta(BaseModel):
    """Serializes to: { fn, params, return, orderMatters, inPlace }"""
    model_config = ConfigDict(populate_by_name=True)
    fn: str
    params: List[ParamMeta]
    return_type: str = Field(alias="return")    # JSON: return  (NOT returnType)
    orderMatters: bool
    inPlace: bool

class StarterCode(BaseModel):
    python: str
    java: str
    cpp: str
    javascript: str

class GeneratedTestCase(BaseModel):
    model_config = _CAMEL_CONFIG
    id: str
    label: str                       # "Edge — empty array", "Sample — basic"
    inputData: Dict[str, Any]
    expectedOutput: Dict[str, Any]
    is_hidden: bool                  # JSON: isHidden
    note: str

class GeneratedMeta(BaseModel):
    model_config = _CAMEL_CONFIG
    mode: str
    leetcode_number: Optional[int] = None       # JSON: leetcodeNumber
    generated_at: str                           # JSON: generatedAt
    model_version: str                          # JSON: modelVersion
    generation_duration_ms: int                 # JSON: generationDurationMs
    total_testcases: int                        # JSON: totalTestcases
    visible_testcases: int                      # JSON: visibleTestcases
    hidden_testcases: int                       # JSON: hiddenTestcases

class GenerateTestcasesResponse(BaseModel):
    model_config = _CAMEL_CONFIG
    title: str
    description: str
    difficulty: str
    tags: List[str]
    time_limit_minutes: int                     # JSON: timeLimitMinutes
    optimal_time_complexity: str                # JSON: optimalTimeComplexity
    optimal_space_complexity: str               # JSON: optimalSpaceComplexity
    function_meta: FunctionMeta                 # JSON: functionMeta
    starter_code: StarterCode                   # JSON: starterCode
    testcases: List[GeneratedTestCase]
    meta: GeneratedMeta
    warning: Optional[str] = None
```

---

## 6. Agent Graph

### Graph: `generator/graph.py` (tách riêng, không ảnh hưởng evaluator hiện tại)

```
raw_input
    │
[generator_router]
    │   Validate input theo mode
    │   mode=leetcode → validated_input chỉ có leetcodeUrl + meta
    │   mode=custom   → validated_input có đủ fields
    │   Lỗi validation → trả về error ngay (END)
    │
[problem_analyzer]
    │   mode=leetcode: fetch real problem via LeetCode GraphQL (fetch_leetcode_problem)
    │                  → lấy title, description, examples, constraints,
    │                    starter code cho python3 / java / cpp / javascript
    │                  → lưu vào state["leetcode_problem"]
    │                  → lỗi fetch trả final_output ngay (leetcode_not_found / leetcode_premium)
    │   Sau đó LLM sinh ra:
    │   - functionMeta (fn, params[].name, params[].type, return_type, order_matters, in_place)
    │   - starter_code: { python, java, cpp, javascript }  ← 4 ngôn ngữ
    │   - optimal_time_complexity, optimal_space_complexity (hoặc dùng input nếu custom có)
    │   - time_limit_minutes
    │   - constraints & edge_case_hints (nội bộ cho bước tiếp theo)
    │   Override sau LLM:
    │     mode=leetcode — title/description/difficulty/tags/constraints lấy từ LeetCode
    │     mode=custom  — difficulty/tags/complexity lấy từ input nếu có
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

**Mode leetcode:** Prompt chứa dữ liệu LeetCode đã fetch — title, description, difficulty, tags, constraints, examples **và starter code chính thức cho cả 4 ngôn ngữ** (`python3`, `java`, `cpp`, `javascript`). LLM được hướng dẫn dùng VERBATIM phần starter code LeetCode đã cung cấp (chỉ chuẩn hoá whitespace + đảm bảo body là placeholder). Sau khi LLM trả output, code ghi đè `title / description / difficulty / tags / constraints` bằng dữ liệu LeetCode gốc.

**Mode custom:** Prompt nhận `title + description` từ input, LLM suy ra toàn bộ metadata kỹ thuật + sinh starter code 4 ngôn ngữ từ đầu theo template chuẩn. Các field có trong request (`difficulty`, `tags`, `complexity`) override output của LLM sau đó.

Output structured (Pydantic) — keys snake_case do dùng field name của `_StarterCode`/`_ParamMeta`:
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
  "starter_code": {
    "python": "class Solution:\n    def twoSum(self, nums: list[int], target: int) -> list[int]:\n        pass",
    "java":   "class Solution { public int[] twoSum(int[] nums, int target) { ... } }",
    "cpp":    "class Solution { public: vector<int> twoSum(...) { ... } };",
    "javascript": "/** ... */ var twoSum = function(nums, target) { ... };"
  },
  "constraints": ["2 <= nums.length <= 10^4", "-10^9 <= nums[i] <= 10^9"],
  "edge_case_hints": ["negative numbers", "duplicate values", "minimal length array"]
}
```

### `testcase_generator` prompt

Nhận output của `problem_analyzer` + `numTestcases` + `numVisible`, yêu cầu AI sinh testcases với phân bổ rõ ràng:

```
Sinh đúng {numTestcases} testcases với phân bổ bắt buộc:
- Visible ({numVisible} cases):
  + 1–2 Sample: lấy thẳng từ ví dụ trong đề
  + 2–3 Edge: empty input, single element, negatives, duplicates, max constraints
  + Còn lại: normal random valid inputs
- Hidden ({numTestcases - numVisible} cases, is_hidden=true):
  + Large inputs (gần constraint tối đa)
  + Complex cases phân biệt brute force vs optimal

Yêu cầu:
- Không trùng inputData giữa các testcase
- expectedOutput phải đúng về mặt logic (tự tính)
- Mỗi testcase có label mô tả ngắn và note giải thích
```

---

## 8. AI-Generated Fields — Format Constraints

Tất cả các fields dưới đây đều do AI sinh ra trong cả hai mode (`leetcode` và `custom`). Format phải khớp **chính xác** với schema của `judge-service` và `question-bank-service`.

### `functionMeta`

Khớp 1:1 với judge-service `FunctionMeta`:

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
| `fn` | camelCase, đúng tên hàm theo convention LeetCode (Java/JS style — không snake_case kể cả với Python target) |
| `params[].name` | tên tham số đúng theo đề bài; phải là key của `inputData` trong từng testcase |
| `params[].type` | type string của judge-service — xem bảng dưới |
| `return` | **key JSON là `"return"`** (alias từ `return_type`); dùng `"void"` khi `inPlace=true` |
| `orderMatters` | `true` nếu thứ tự phần tử output mảng quan trọng (e.g. `spiralOrder`); `false` cho twoSum / groupAnagrams / permutations |
| `inPlace` | `true` nếu hàm mutate `params[0]` thay vì return; khi `true` thì `return = "void"` |

#### Type strings hỗ trợ (chỉ được dùng các giá trị này)

| Category | Valid type strings |
|---|---|
| Primitive | `int`, `long`, `double`, `boolean` |
| Boxed (auto-normalized) | `Integer`, `Long`, `Double`, `Boolean` |
| Character | `char`, `Character` |
| String | `String`, `string` |
| 1-D array | `int[]`, `long[]`, `double[]`, `String[]`, `string[]` |
| 2-D array | `int[][]`, `char[][]`, `String[][]` |
| List | `List<Integer>`, `List<String>`, `List<List<Integer>>`, `List<List<String>>` |
| Tree | `TreeNode` (level-order BFS array) |
| Linked list | `ListNode` (flat int array) |
| Void only | `"void"` (chỉ valid cho `return` khi `inPlace=true`) |

**Cấm:** `float` (dùng `double`), `Map<K,V>` (chưa hỗ trợ), `list[int]` / `Array<number>` / `vector<int>` / các pseudo-type theo ngôn ngữ.

### `starterCode` — object 4 ngôn ngữ

`starterCode` là **object** với 4 field bắt buộc: `python`, `java`, `cpp`, `javascript`. Mỗi giá trị là một string mã nguồn hoàn chỉnh, đã có placeholder body, sẵn sàng paste vào editor.

#### Hard requirements (áp dụng cho cả 4 ngôn ngữ)

1. Body chỉ chứa **placeholder duy nhất** (`pass` / `// TODO` / `return ...`) — không được viết lời giải.
2. Tên method và tên tham số PHẢI khớp `fn` và `params[].name` từng kí tự.
3. Shape của class/function phải khớp đúng yêu cầu của judge driver — không được đổi.

#### Format chuẩn theo ngôn ngữ

**Python** — `UniversalPythonDriver` instantiate `Solution()` rồi dispatch qua `getattr`.

```python
class Solution:
    def twoSum(self, nums: list[int], target: int) -> list[int]:
        pass
```

Type-hint mapping: `int/long → int`, `double → float`, `boolean → bool`, `String/char → str`, `int[] → list[int]`, `String[] → list[str]`, `int[][] → list[list[int]]`, `char[][] → list[list[str]]`, `List<Integer> → list[int]`, `TreeNode → 'TreeNode'`, `ListNode → 'ListNode'`, `void → -> None`.

**Java** — `UniversalJavaDriver` tạo `new Solution()` và gọi qua reflection. **Không** đặt `public` trên class.

```java
class Solution {
    public int[] twoSum(int[] nums, int target) {
        // TODO
        return new int[]{};
    }
}
```

`List<Integer>` etc. dùng `java.util.List<Integer>` (fully-qualified, không cần import).

**C++** — LeetCode-compatible Solution class, dùng `using namespace std;` style.

```cpp
class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {
        // TODO
        return {};
    }
};
```

Mapping: `int[] → vector<int>`, `String → string`, `TreeNode → TreeNode*`, `ListNode → ListNode*`.

**JavaScript** — top-level function expression với JSDoc.

```javascript
/**
 * @param {number[]} nums
 * @param {number} target
 * @return {number[]}
 */
var twoSum = function(nums, target) {
    // TODO
};
```

JSDoc mapping: `int/long/double → number`, `boolean → boolean`, `String/char → string`, `int[] → number[]`, `char[][] → character[][]`, `List<Integer> → number[]`, `TreeNode → TreeNode`, `ListNode → ListNode`.

#### Data-structure prologue (BẮT BUỘC khi có ListNode hoặc TreeNode)

Khi `params` hoặc `return` chứa `ListNode` hoặc `TreeNode`, starter code của **cả 4 ngôn ngữ** phải prepend một block comment định nghĩa class/struct theo style LeetCode chuẩn — **chỉ là comment** (giúp candidate biết shape), không uncomment.

Ví dụ Python (cho problem có `head: ListNode`):
```python
# Definition for singly-linked list.
# class ListNode:
#     def __init__(self, val=0, next=None):
#         self.val = val
#         self.next = next
class Solution:
    def reverseList(self, head: 'ListNode') -> 'ListNode':
        pass
```

Ví dụ Java:
```java
/**
 * Definition for singly-linked list.
 * public class ListNode {
 *     int val;
 *     ListNode next;
 *     ListNode() {}
 *     ListNode(int val) { this.val = val; }
 *     ListNode(int val, ListNode next) { this.val = val; this.next = next; }
 * }
 */
class Solution {
    public ListNode reverseList(ListNode head) {
        // TODO
        return null;
    }
}
```

C++ và JavaScript làm tương tự (xem chi tiết trong `generator/prompts/problem_analysis.py` — section `STARTER_CODE → Prologues`). Khi LeetCode đã cung cấp starter code chính thức (mode=leetcode), phần prologue đã có sẵn → giữ nguyên VERBATIM.

### `optimalTimeComplexity` / `optimalSpaceComplexity`

- Format: **Big-O notation chuẩn**, string
- Dùng `n` là kích thước input chính, `k` cho constraint phụ nếu cần
- Hợp lệ: `"O(1)"`, `"O(n)"`, `"O(n log n)"`, `"O(n²)"`, `"O(n + k)"`, `"O(2^n)"`
- Không dùng: `"O(N)"`, `"linear"`, `"constant"`, `"O(n^2)"` (dùng `²` thay `^2`)

### `difficulty`

Phải là một trong: `"EASY"`, `"MEDIUM"`, `"HARD"` (uppercase, khớp enum `Difficulty` trong hệ thống).

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

## 9. Validation Rules — `testcase_validator`

| Rule | Hành động |
|---|---|
| `len(testcases) != numTestcases` | retry |
| `sum(isHidden) != numHidden` (= `numTestcases − numVisible`) | retry |
| Hai testcase có `inputData` giống nhau (so sánh canonicalized JSON) | retry |
| Bất kỳ `expectedOutput` nào rỗng `{}` | retry |
| Vượt `GENERATOR_MAX_RETRIES` (default `1`) | trả partial result + `warning: "max_retries_exceeded (...)"` |
| `raw_testcases` = None (generator fail) + vượt retry | trả `error: generation_failed` |

Duplicate check dùng `json.dumps(inputData, sort_keys=True)` để tránh false positive khi LLM đổi thứ tự key.

---

## 10. File Structure

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
│   ├── leetcode_fetcher.py             ← fetch_leetcode_problem() (4-language starter code)
│   ├── nodes/
│   │   ├── __init__.py
│   │   ├── generator_router.py         ← validate & init state
│   │   ├── problem_analyzer.py         ← fetch LeetCode (nếu cần) + LLM suy metadata + 4-lang starter
│   │   ├── testcase_generator.py       ← LLM sinh testcases
│   │   └── testcase_validator.py       ← validate output, retry, assemble camelCase response
│   └── prompts/
│       ├── __init__.py
│       ├── problem_analysis.py         ← prompt với judge-service type table + 4 language templates
│       └── testcase_generation.py
│
├── evaluator/                          ← Evaluator (LiveCoding/Behavioral/Conceptual)
│   └── ...
│
└── models/
    ├── common.py
    ├── inputs.py                       ← evaluator inputs
    ├── outputs.py                      ← evaluator outputs
    ├── generator_inputs.py             ← GenerateTestcasesRequest (camelCase only)
    └── generator_outputs.py            ← GenerateTestcasesResponse, StarterCode (4 langs)
```

---

## 11. Integration Flow

```
Admin UI
  │
  ├─ [1] POST /generate-testcases  →  AI-Evaluation Service
  │       Mode leetcode: { mode, leetcodeUrl, numTestcases, numVisible }
  │       Mode custom:   { mode, title, description, difficulty?, ..., numTestcases, numVisible }
  │
  │       Response: full CodingQuestion data + testcases[] (camelCase)
  │
  ├─ [2] Admin review trên UI
  │       - Xem từng testcase (label, inputData, expectedOutput, note)
  │       - Sửa / xóa / thêm testcase thủ công nếu cần
  │       - Điều chỉnh visibility (isHidden toggle)
  │       - Xem starterCode 4 ngôn ngữ trong tab editor
  │
  └─ [3] Admin nhấn "Apply"
          FE gọi POST /admin/questions/coding  →  question-bank-service
          Body = CodingQuestionRequest (map từ response bước 1 + edits của admin)
```

### Fields mapping — Response → CodingQuestionRequest

| Response field (camelCase) | CodingQuestionRequest field |
|---|---|
| `title` | `title` |
| `description` | `description` |
| `difficulty` | `difficulty` |
| `tags` | `tags` |
| `timeLimitMinutes` | `timeLimitMinutes` |
| `optimalTimeComplexity` | `optimalTimeComplexity` |
| `optimalSpaceComplexity` | `optimalSpaceComplexity` |
| `functionMeta` | `functionMeta` (giữ nguyên — đã đúng schema judge-service) |
| `starterCode.python` | `starterCode.python` |
| `starterCode.java` | `starterCode.java` |
| `starterCode.cpp` | `starterCode.cpp` |
| `starterCode.javascript` | `starterCode.javascript` |
| `testcases[].inputData` | `testCases[].inputData` |
| `testcases[].expectedOutput` | `testCases[].expectedOutput` |
| `testcases[].isHidden` | `testCases[].isHidden` |
| `testcases[].label` + `note` | chỉ dùng cho Admin UI review, không lưu vào DB |

> Judge-service hiện chỉ thực thi `java` và `python` (xem judge-service-guide §2.1). `cpp` và `javascript` được sinh để hiển thị ở editor / tham khảo cho candidate, chưa nối vào pipeline run code.

---

## 12. Configuration — Environment Variables

| Env | Default | Dùng cho |
|---|---|---|
| `GOOGLE_API_KEY` | — | Gemini API key (bắt buộc) |
| `MODEL_NAME` | `gemini-3-flash-preview` | Model gọi cả analyzer + generator |
| `THINKING_LEVEL` | `low` | `minimal` / `low` / `medium` / `high` (Gemini 3) — auto-map sang `thinking_budget` nếu dùng Gemini 2.x |
| `GENERATOR_MAX_RETRIES` | `1` | Số lần retry tối đa của `testcase_validator` |
| `MAX_RETRIES` | `2` | Retry cho evaluator agent (`/evaluate`) |
| `MODEL_VERSION` | `evaluator-v1.0` | Ghi vào `meta.modelVersion` của response |
| `SERVICE_API_KEY` | — | Nếu set, `/generate-testcases` & `/evaluate` yêu cầu `X-API-Key` header |

Tất cả LLM call đi qua `llm.call_structured(prompt, response_model)` — thay model / thinking level chỉ cần sửa `.env`, không cần sửa code.

---

## 13. Running the Service

```bash
# Local dev
cd AI-Evaluation
source venv/bin/activate
uvicorn api:app --reload --host 0.0.0.0 --port 8000

# Quick CLI smoke (no HTTP)
python main.py live_coding         # hoặc behavioral / core_conceptual

# Docker
docker build -t ai-evaluation .
docker run --rm -p 8000:8000 --env-file .env ai-evaluation
```

Endpoints:
- `GET  /health`
- `POST /generate-testcases` (header `X-API-Key: <SERVICE_API_KEY>`)
- `POST /evaluate`
- `GET  /leetcode/fetch?url=two-sum`

---

## 14. Performance

Đo trên `leetcode/group-anagrams` với `numTestcases=20, numVisible=17` (≈ `numHidden=3`):

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

> Sinh starter code 4 ngôn ngữ thay vì 1 không tăng latency đáng kể — output tokens chỉ thêm ~300–500 tokens, nhỏ so với reasoning + testcase generation.
