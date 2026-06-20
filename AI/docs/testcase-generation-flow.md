# Coding-Question Generation — Luồng hoạt động hiện tại

> **Status:** Implemented (Phase A live · Phase B flag-gated OFF)
> **Service:** AI-Evaluation · `POST /generate-testcases`
> **Scope:** Mô tả luồng runtime sau khi thêm (A) model riêng mạnh hơn cho khâu
> sinh đề và (B) sinh input lớn bằng code. Phần API contract / schema / format
> chi tiết xem [`testcase-generator-design.md`](./testcase-generator-design.md).

---

## 1. Tóm tắt một dòng

Admin nhập đề (LeetCode URL hoặc custom) → LangGraph chạy 6 node → trả về **đầy
đủ data** của 1 `CodingQuestion` (metadata + functionMeta + starter 4 ngôn ngữ +
testcases visible/hidden đã được **chứng minh đúng output**) để Admin review rồi
lưu vào `question-bank-service`.

Hai trục chất lượng độc lập nhau:

- **Output đúng** — đã được bảo vệ từ trước bởi `expected_verifier`
  (dual-solution consensus, chạy lời giải thật qua judge driver). LLM **không**
  được tin để tự tính đáp án.
- **Input tốt** — là phần vừa được cải thiện: model mạnh hơn cho khâu sinh
  (Phase A) + sinh input lớn bằng code cho hidden case (Phase B).

---

## 2. Graph tổng thể

```
raw_input
    │
[generator_router]
    │   • validate request theo mode (camelCase only)
    │   • CHẶN nếu numTestcases > MAX_TESTCASES (mặc định 25) → error too_many_testcases
    │   • lỗi validation → final_output → END
    │
[problem_analyzer]
    │   • mode=leetcode: fetch GraphQL (title/desc/constraints/examples + starter 4 ngôn ngữ)
    │   • LLM (★ GENERATOR MODEL) suy: functionMeta, starter 4 lang,
    │     reference_solution + brute_force_solution, complexity, constraints,
    │     edge_case_hints, và unique_answer (cờ "đề có đúng 1 đáp án?")
    │   • override các field ground-truth từ LeetCode
    │
[testcase_generator]
    │   • LLM (★ GENERATOR MODEL) sinh đúng numTestcases case:
    │       visible = sample + edge + normal (có note giải thích tiếng Việt)
    │       hidden  = case lớn/khó (is_hidden=true)
    │
[input_generator]   ← MỚI (Phase B) · no-op nếu PROGRAMMATIC_INPUTS=false
    │   • CHỈ chạy khi: flag ON + VERIFY_EXPECTED_OUTPUTS ON + unique_answer=true
    │   • LLM (★ GENERATOR MODEL) viết hàm gen_inputs(num_cases, seed)
    │   • chạy hàm trong subprocess (timeout) → input LỚN/ngẫu nhiên đúng constraint
    │   • THAY inputData của các hidden case bằng input này, xoá expectedOutput cũ
    │   • mọi lỗi → degrade: giữ nguyên hidden case do LLM tạo + ghi warning
    │
[expected_verifier]  ──(còn case chưa chứng minh + còn budget)──► quay lại [problem_analyzer]
    │   • chạy CẢ reference_solution + brute_force_solution qua judge Python driver
    │     trên TỪNG inputData (kể cả input do Phase B sinh ra)
    │   • 2 lời giải KHỚP → ghi đè expectedOutput = đáp án đã chứng minh
    │   • lệch / cùng lỗi → repair loop (regenerate 2 lời giải, REFERENCE_MAX_RETRIES)
    │   • hết budget mà chưa giải được → giữ giá trị cũ + warning ⚠ REVIEW MANUALLY
    │
[testcase_validator]  ──(vi phạm + còn retry)──► quay lại [testcase_generator]
    │   • đủ count? đủ hidden? không trùng input? expectedOutput đúng shape?
    │   • vượt GENERATOR_MAX_RETRIES (mặc định 2) → trả partial + warning
    │   • gộp warning từ input_generator + expected_verifier vào response.warning
    │
   END  →  final_output (camelCase, sẵn sàng cho question-bank-service)
```

> Hai vòng lặp: **repair loop** (verifier → analyzer, sửa lời giải sai) và
> **validation retry** (validator → generator, sửa bộ testcase). Mỗi lần quay lại
> generator đều đi qua input_generator → verifier nên dữ liệu luôn nhất quán.

---

## 3. Phase A — Model riêng mạnh hơn cho khâu sinh

**Vấn đề:** trước đây MỌI tác vụ AI (chấm điểm, chọn câu, sinh đề) dùng chung
`MODEL_NAME` (Flash) + `THINKING_LEVEL=low`. Sinh đề/testcase là bước **suy luận
nặng nhất** (thiết kế nhiều input đa dạng, đúng constraint, không trùng) nhưng chỉ
chạy khi admin tạo đề (tần suất thấp) → đáng để dùng model mạnh hơn.

**Cách làm:** `llm.call_structured` nay nhận `model` / `thinking_level` override.
Hai node sinh đề truyền model riêng; phần còn lại của service vẫn dùng Flash.

| Node | Model dùng |
|---|---|
| `problem_analyzer` | `GENERATOR_MODEL_NAME` (mặc định `gemini-3.1-pro-preview`) + `GENERATOR_THINKING_LEVEL` (`medium`) |
| `testcase_generator` | như trên |
| `input_generator` | như trên |
| Mọi tác vụ khác (evaluator, selector…) | `MODEL_NAME` (Flash) + `THINKING_LEVEL` |

Kèm theo:
- `GENERATOR_MAX_RETRIES` nâng `1 → 2` (lỗi lệch count / trùng input thường hết ở
  lần regenerate thứ 2).
- **`MAX_TESTCASES` cap** (mặc định 25), chặn ngay ở `generator_router`. Trước đây
  **không có giới hạn nào** → user xin con số quá lớn mà bước sinh one-shot không
  kham nổi chính là nguyên nhân "sinh tùm bậy".

---

## 4. Phase B — Sinh input lớn bằng code (`input_generator`)

**Vấn đề cốt lõi:** LLM **không thể tự gõ** một mảng 10⁴ phần tử dạng JSON. Nên
các hidden case "input lớn" do LLM tạo thường nhỏ/giả, không tách được O(n) khỏi
O(n²) — và càng xin nhiều hidden càng lộ trùng/giả.

**Ý tưởng:** đừng bắt LLM *gõ* dữ liệu lớn, hãy bảo nó *viết hàm sinh* dữ liệu
lớn. Ta đã có sẵn reference solution chạy được + cơ chế chạy judge driver, nên:

1. LLM viết `gen_inputs(num_cases, seed)` (code nhỏ, LLM làm tốt).
2. Chạy hàm trong subprocess có timeout → danh sách input lớn/ngẫu nhiên.
3. Validate (key khớp param, không trùng) rồi **thay vào inputData của hidden case**.
4. `expected_verifier` (chạy ngay sau) tính output đúng cho các input mới này.

### Cổng an toàn (BẮT BUỘC, nếu không thoả → bỏ qua, giữ input của LLM)

| Điều kiện | Lý do |
|---|---|
| `PROGRAMMATIC_INPUTS=true` | Feature flag, mặc định OFF |
| `VERIFY_EXPECTED_OUTPUTS=true` | Phase B dựa vào verifier để điền output cho input mới |
| `analysis.unique_answer == true` | **Quan trọng nhất** — xem dưới |

**Vì sao cần `unique_answer`:** judge so khớp **tuyệt đối**. Với bài "trả về bất
kỳ đáp án hợp lệ nào" (twoSum — nhiều cặp index cùng đúng; tìm *một* đỉnh; *một*
subset hợp lệ…), một input ngẫu nhiên có thể có NHIỀU đáp án đúng → đáp án
đúng-khác của thí sinh sẽ bị chấm WA so với 1 `result` ta lưu. Vì vậy
`problem_analyzer` gắn cờ `unique_answer`; bài đa-đáp-án → **không** sinh input
ngẫu nhiên, giữ nguyên hidden case do LLM tự dựng (LLM được prompt để cố định đáp
án — rule 9 trong `testcase_generation.py`).

### Degrade gracefully

Mọi nhánh lỗi (LLM fail, hàm crash/timeout, output không hợp lệ, sinh thiếu) đều
**không làm hỏng request**: giữ nguyên hidden case của LLM và đẩy một dòng
`programmatic_warning` để admin biết. Output cuối luôn có đủ testcase.

---

## 5. Warnings trả về cho admin (`response.warning`)

`testcase_validator` gộp 2 nguồn note (không phải lỗi cứng) vào `warning`:

- từ `input_generator` — vd "skipped — multiple valid answers", "filled 4/5 hidden
  cases", "generation failed … kept the model's hidden cases".
- từ `expected_verifier` — vd "corrected N expectedOutput(s)", "⚠ COULD NOT VERIFY
  … REVIEW MANUALLY".

`warning = null` nghĩa là sạch hoàn toàn. Có giá trị thì admin nên đọc trước khi
lưu — không phải lúc nào cũng là lỗi, nhưng là tín hiệu cần liếc qua.

---

## 6. Biến môi trường mới / liên quan

| Env | Default | Dùng cho |
|---|---|---|
| `GENERATOR_MODEL_NAME` | `gemini-3.1-pro-preview` | Model riêng cho analyzer + testcase_generator + input_generator |
| `GENERATOR_THINKING_LEVEL` | `medium` | Thinking level cho generator model |
| `MAX_TESTCASES` | `25` | Cap numTestcases (chặn ở router) |
| `GENERATOR_MAX_RETRIES` | `2` | Retry của testcase_validator (trước là 1) |
| `PROGRAMMATIC_INPUTS` | `false` | Bật Phase B (sinh input lớn bằng code) |
| `PROGRAMMATIC_INPUT_SEED` | `42` | Seed cố định → deterministic qua các lần repair |
| `VERIFY_EXPECTED_OUTPUTS` | `true` | Bật `expected_verifier` (Phase B phụ thuộc) |
| `EXPECTED_VERIFY_TIMEOUT_SECONDS` | `15` | Timeout mỗi lần chạy lời giải/hàm sinh trên 1 case |
| `REFERENCE_MAX_RETRIES` | `2` | Số lần regenerate 2 lời giải (repair loop) |

> ⚠ **Model availability:** trên key prod hiện tại, `gemini-3-pro` và
> `gemini-3-pro-preview` đều KHÔNG generate được (404 / "no longer available").
> Default đã chọn `gemini-3.1-pro-preview` (đã verify generateContent +
> thinkingLevel=medium = 200). Nếu Google rút model này, đổi
> `GENERATOR_MODEL_NAME` sang `gemini-2.5-pro` (code tự dùng `thinking_budget`
> cho dòng 2.x) hoặc `gemini-pro-latest`.

---

## 7. Rollout Phase B

Phase B đang **OFF**. Khi muốn bật:

1. Ở staging: set `PROGRAMMATIC_INPUTS=true`, sinh thử vài bài unique-answer (vd
   `maximum-subarray`, `contains-duplicate`) và vài bài đa-đáp-án (`two-sum`) →
   kiểm tra bài đa-đáp-án ĐƯỢC bỏ qua đúng (warning), bài unique có input lớn thật.
2. Verify expectedOutput của các hidden case mới do verifier điền (không còn
   placeholder `0`/`[]`).
3. Bật prod sau khi smoke-test pass.

---

## 8. File liên quan

```
AI/
├── config.py                                  ← GENERATOR_MODEL_NAME, MAX_TESTCASES, PROGRAMMATIC_INPUTS…
├── llm.py                                      ← call_structured(model=, thinking_level=)
└── generator/
    ├── graph.py                                ← thêm node input_generator vào graph
    ├── state.py                                ← + programmatic_warning
    ├── nodes/
    │   ├── generator_router.py                 ← + cap MAX_TESTCASES
    │   ├── problem_analyzer.py                 ← + field unique_answer, dùng generator model
    │   ├── testcase_generator.py               ← dùng generator model
    │   ├── input_generator.py                  ← MỚI (Phase B)
    │   ├── expected_verifier.py                ← (không đổi) chạy cho cả input Phase B
    │   └── testcase_validator.py               ← gộp programmatic_warning
    └── prompts/
        ├── problem_analysis.py                 ← + hướng dẫn unique_answer
        ├── testcase_generation.py              ← (không đổi)
        └── input_generation.py                 ← MỚI (prompt cho gen_inputs)
```
