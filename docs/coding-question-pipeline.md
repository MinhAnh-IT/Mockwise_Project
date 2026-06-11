# Coding-Question Pipeline — End to End

> **Mục đích file này:** giúp bất kỳ session AI (hoặc người) nào hiểu **trọn vẹn** vòng đời một
> bài coding: từ lúc **AI sinh đề** → **lưu vào question-bank** → **chạy code trong judge** →
> **đối chiếu output ra verdict**. Đọc xong file này là đủ để debug "vì sao chạy bài X bị sai"
> mà không cần đọc lại toàn bộ source.
>
> Phạm vi: `AI/` (generator), `question-bank-service`, `practice-service`, `judge-service`,
> Judge0. Mọi đường dẫn file đều là **đường dẫn thật** trong repo.
>
> Cập nhật lần cuối: **2026-06-11**.

---

## 0. Bản đồ tổng thể (đọc cái này trước)

```
                ┌─────────────────────────────────────────────────────────────────┐
   ADMIN  ──1──▶│  AI service (FastAPI + LangGraph)                                │
   "generate"   │  router → problem_analyzer → testcase_generator → validator(↻3)  │
                │  OUT: draft JSON camelCase {title, functionMeta, testcases[...]} │
                └─────────────────────────────────────────────────────────────────┘
                                          │ 2 (admin review + edit, rồi POST)
                                          ▼
                ┌─────────────────────────────────────────────────────────────────┐
   ADMIN  ──3──▶│  question-bank-service                                           │
   "save"       │  coding_questions: functionMeta(jsonb) + testCases(jsonb)        │
                │  testCase = { inputData{}, expectedOutput{result:…}, is_hidden } │
                └─────────────────────────────────────────────────────────────────┘
                                          │ 4 (internal Feign: GET /internal/coding-problems/{id})
                                          ▼
                ┌─────────────────────────────────────────────────────────────────┐
   USER   ──5──▶│  practice-service   (RUN = visible cases | SUBMIT = all cases)   │
   "run/submit" │  build CodeSubmissionEvent → Kafka topic "code-submission"       │
                └─────────────────────────────────────────────────────────────────┘
                                          │ 6 (Kafka, origin=PRACTICE | INTERVIEW)
                                          ▼
                ┌─────────────────────────────────────────────────────────────────┐
                │  judge-service                                                  │
                │  CodeBuilder(inject user code vào Universal driver)             │
                │  StdinBuilder(metaJson + 1 dòng/param) → Judge0 (base64,async)  │
                │  ◀── Judge0 callback ── decodeBase64 → OutputComparator → verdict│
                └─────────────────────────────────────────────────────────────────┘
                                          │ 7 (Kafka topic "submission-judged")
                                          ▼
                          practice-service / interview-service áp verdict
```

**Quy ước truyền dữ liệu xuyên suốt:** mọi giá trị I/O của test case được mô tả bằng
`functionMeta` (chữ ký hàm) + cặp `inputData` / `expectedOutput`. Driver trong judge dựng lại
tham số từ `inputData`, gọi hàm của thí sinh, in kết quả ra stdout, rồi judge so stdout với
`expectedOutput.result`. **Toàn bộ tính đúng/sai nằm ở việc khớp shape JSON này.**

---

## 1. AI sinh đề (thư mục `AI/`)

### 1.1 Điểm vào
- Admin gọi `POST /admin/questions/coding/generate`
  (`question-bank-service/.../controller/AdminQuestionController.java:74`).
- Chuyển tiếp qua Feign `CodingGenerationClient`
  (`question-bank-service/.../ai/CodingGenerationClient.java`) → AI service endpoint
  `generate-testcases`.
- AI service chạy một **LangGraph** (`AI/generator/graph.py`):

```
generator_router → problem_analyzer → testcase_generator → testcase_validator
                                            ▲                       │
                                            └──── retry (≤3) ───────┘  (needs_retry)
```

### 1.2 Các node (file: `AI/generator/nodes/`)

| Node | File | Việc làm | Output vào state |
|---|---|---|---|
| `generator_router` | `generator_router.py` | Validate request (mode=leetcode/custom). | `validated_input` hoặc `final_output`(lỗi) |
| `problem_analyzer` | `problem_analyzer.py` | (leetcode) fetch đề thật từ LeetCode GraphQL; rồi LLM suy ra metadata: title, description, **fn/params/return_type/order_matters/in_place**, complexity, constraints, edge_case_hints, starter_code 4 ngôn ngữ. | `problem_analysis` |
| `testcase_generator` | `testcase_generator.py` | LLM sinh `num_testcases` test case. | `raw_testcases` |
| `testcase_validator` | `testcase_validator.py` | Kiểm tra cứng (xem 1.4); pass → dựng `final_output`, fail → retry. | `final_output` |

### 1.3 Hợp đồng dữ liệu (models — `AI/models/generator_outputs.py`)

`GenerateTestcasesResponse` (serialize **camelCase** qua `to_camel`, riêng FunctionMeta giữ key `"return"`):

```jsonc
{
  "title": "...", "description": "...", "difficulty": "EASY|MEDIUM|HARD",
  "tags": [...], "constraints": "markdown bullet list",
  "optimalTimeComplexity": "O(n)", "optimalSpaceComplexity": "O(n)",
  "functionMeta": {
    "fn": "twoSum",
    "params": [{"name":"nums","type":"int[]"}, {"name":"target","type":"int"}],
    "return": "int[]",            // <-- LƯU Ý: key là "return", KHÔNG phải "returnType"
    "orderMatters": false,
    "inPlace": false
  },
  "starterCode": {"python":"...","java":"...","cpp":"...","javascript":"..."},
  "testcases": [
    {
      "id":"tc-1", "label":"Sample — basic",
      "inputData": {"nums":[2,7,11,15], "target":9},
      "expectedOutput": {"result":[0,1]},   // <-- LUÔN đúng 1 key "result"
      "isHidden": false,
      "note": "giải thích tiếng Việt"
    }
  ],
  "meta": {...}
}
```

> ⚠️ **Mẹo Instructor/Gemini:** trong `testcase_generator.py`, `inputData`/`expectedOutput`
> được khai báo là **chuỗi JSON** (`str`), không phải `Dict`, vì Gemini structured-output trả
> `{}` cho dict mở. Node tự `json.loads` lại thành dict trước khi đẩy xuống validator.

### 1.4 Validator — các luật cứng (`testcase_validator.py`)

Mọi luật dưới đây **phản chiếu đúng hành vi của judge driver**; vi phạm = WA/RE hàng loạt:

1. Số lượng test case == `num_testcases`; số `is_hidden=true` == `num_hidden`.
2. Không trùng `inputData` (canonical hoá bằng `json.dumps(sort_keys=True)`).
3. **Keys của `inputData` == đúng tập `params[].name`** (StdinBuilder tra cứu theo tên; thiếu/thừa → `null` vào driver → RE).
4. `expectedOutput` là object có **đúng một key `"result"`** (OutputComparator lấy field đầu tiên của wrapper).
5. **Shape khớp**: `result` phải là JSON array nếu `return_type` thuộc nhóm array (hoặc `in_place=true` → khớp shape của `params[0]`), ngược lại là scalar. Bắt đúng lớp lỗi "scalar ở nơi cần array → WA mọi case".
6. Belt riêng: `in_place=true` mà `return_type != "void"` → bail `invalid_function_meta` (driver bỏ qua giá trị return khi in-place).

Retry tối đa `GENERATOR_MAX_RETRIES = 3`. Hết retry mà còn lỗi → trả best-effort + `warning`.

### 1.5 ⚠️ GIỚI HẠN CỐ HỮU: AI **không chạy code**
`expectedOutput.result` do **LLM tự suy luận**, không phải do chạy lời giải mẫu. Hệ quả:
- Có thể sai số học (LLM tính nhầm).
- **Bài nhiều đáp án hợp lệ** (Two Sum, "return any…") → LLM chốt một đáp án, nhưng thí sinh
  có thể trả đáp án hợp lệ **khác** → WA oan. Đây là lỗi #2 ở §6. Cách phòng tổng quát: ép input
  có **đáp án DUY NHẤT** (luật #9 đã thêm vào `AI/generator/prompts/testcase_generation.py`).

---

## 2. Lưu vào question-bank

### 2.1 Endpoint
- `POST /admin/questions/coding` (`AdminQuestionController.java:57`) — admin **review + chỉnh tay**
  draft của AI rồi mới lưu. AI chỉ đẻ draft; lưu là một bước riêng, cố ý.
- `PUT /admin/questions/coding/{id}` — sửa.

### 2.2 Lưu trữ (Postgres DB `question_bank`, bảng `coding_questions`)
Entity `question-bank-service/.../entity/CodingQuestion.java`:
- `functionMeta` → cột **jsonb** (`FunctionMeta.java`, key `"return"` qua `@JsonProperty`).
- `testCases` → cột **jsonb**, `List<TestCase>` (`TestCase.java`):
  ```
  TestCase { String id; Map inputData; Map expectedOutput; boolean is_hidden; String note; }
  ```
- Các cột phẳng: title, description, constraints, optimalTimeComplexity, optimalSpaceComplexity,
  starterCode (jsonb).

### 2.3 Endpoint nội bộ cho practice
`InternalCodingProblemController.java` mount `/internal/coding-problems`:
- `GET /internal/coding-problems` — list bài ACTIVE (KHÔNG kèm testcases).
- `GET /internal/coding-problems/{id}` — **full detail kèm hidden testcases**. Chỉ practice-service
  gọi (qua gateway prefix `/internal`, không lộ ra browser). practice-service tự strip hidden trước
  khi trả cho user.

---

## 3. practice-service: RUN vs SUBMIT

File: `practice-service/.../service/PracticeSubmissionService.java`.

| | RUN ("Chạy thử") | SUBMIT ("Nộp") |
|---|---|---|
| Test case dùng | chỉ **visible** (`!hidden`) | **toàn bộ** (visible + hidden) |
| Đụng problem_status | không | có (ATTEMPTED → SOLVED nếu verdict=AC) |

Luồng `dispatch()`:
1. `fetchDetail(problemId)` qua Feign QB internal.
2. Lọc test case theo mode.
3. Lưu `PracticeSubmission` (PENDING) + ghi `hiddenCaseIds`.
4. Dựng `CodeSubmissionEvent` rồi `producer.publish(...)` lên Kafka **`code-submission`**:
   ```
   CodeSubmissionEvent {
     submissionId, origin="PRACTICE", language, code,
     functionMeta { fn, params[], returnType, orderMatters, inPlace },
     testCases[] { id, inputData, expectedOutput }   // expectedOutput = {"result": …}
   }
   ```
   `toEventFunctionMeta()` map `QbFunctionMeta` → event; **`returnType` map sang key `"return"`**
   khi judge deserialize (judge `FunctionMeta` có `@JsonProperty("return")`).
5. Verdict quay về qua consumer `SubmissionJudgedConsumer` → `applyJudged()` (idempotent, Kafka
   at-least-once): đếm case AC, set verdict, hidden case **không lộ** stdout/stderr.

> interview-service cũng dùng chung judge (origin=INTERVIEW). Mỗi consumer **fast-filter theo
> `origin`** để không xử lý verdict của luồng kia (nếu không → poison-loop).

---

## 4. judge-service: chạy & đối chiếu (phần dễ sai nhất)

Vào: Kafka `code-submission` → `SubmissionConsumer.java` → `JudgeOrchestrator.handle()`.

### 4.1 Dựng source (`codebuilder/CodeBuilder.java`)
Inject code thí sinh vào **Universal driver** theo ngôn ngữ
(`judge-service/src/main/resources/drivers/Universal{Java,Python,Js,Cpp}Driver.*`):
- **Java**: tách `import` lên đầu, hạ `public class Solution` → `class Solution`, ghép driver `Main`.
- **Python/JS**: thay marker `# === USER_CODE_INJECTED_HERE ===` bằng code thí sinh.
- **C++**: thay marker code + sinh **dispatch block** từ `functionMeta` (C++ không có reflection,
  phải bake sẵn parse + call). Vì vậy `buildFullSource` cho C++ **bắt buộc** có `functionMeta`.

### 4.2 Giao thức stdin (`codebuilder/StdinBuilder.java` + `TypeSerializer.java`)
Mỗi test case → 1 chuỗi stdin:
```
Dòng 1: functionMeta dạng JSON (1 dòng)
Dòng 2: serialize của params[0]
Dòng 3: serialize của params[1]
...
```
Quy tắc serialize (`TypeSerializer.serialize(value, type)`):
- `int/long/double/boolean/string/char` → `String.valueOf` (string KHÔNG có nháy bao).
- array/matrix/List/TreeNode/ListNode → `objectMapper.writeValueAsString` (JSON array).
  - **TreeNode** = mảng level-order BFS, ví dụ `[3,9,20,null,null,15,7]`.
  - **ListNode** = mảng int phẳng, ví dụ `[1,2,3,4,5]`.

Driver đọc dòng 1 (metaJson), parse từng dòng param theo `type`, gọi `Solution.<fn>(...)`, rồi **in 1 dòng JSON**:
- `inPlace=true` → in `params[0]` **sau khi mutate** (bỏ qua giá trị return).
- `inPlace=false` → in giá trị return (`ListNode/TreeNode` null → in `[]`).

### 4.3 Gửi Judge0 (`judge0/Judge0Client.java`)
- `POST {judge0}/submissions?base64_encoded=true&wait=false` — source + stdin **base64** (encoder cơ bản, OK vì đầu vào không có newline thừa).
- Language IDs: Java=62, JS=63, C++=54, Python=71. CPU 15s, mem 512000.
- Async: Judge0 **POST callback** về `/api/v1/judge/callback/{taskId}` khi xong.

### 4.4 Callback & chấm (`JudgeOrchestrator.handleCallback` + `codebuilder/OutputComparator.java`)
1. `decodeBase64(payload.stdout/stderr/compileOutput)`.
   - **PHẢI dùng `Base64.getMimeDecoder()`** — xem lỗi #1 ở §6.
2. `resolveTaskStatus(judge0StatusId, stdout, expectedOutput, orderMatters)`:
   - Judge0 status 3 (Accepted) → `OutputComparator.compare(...)` → AC | WA.
   - 5→TLE, 6→CE, 7..14→RE.
3. `OutputComparator.compare(stdout, expectedOutputJson, orderMatters)`:
   - Parse `stdout` → JsonNode `actual`; lấy field đầu của `{"result": …}` → `expected`.
   - `orderMatters=false` và cả hai là array → **sort rồi so** (so theo SET, không theo thứ tự).
   - Ngược lại → `actual.equals(expected)`.
   - Lỗi parse → fallback so chuỗi đã trim.
4. Atomic `incrementDoneCases` → khi `done==total` → `finalizeJob` → `VerdictAggregator` gộp verdict
   → Kafka **`submission-judged`**.

### 4.5 Verdict gộp
AC chỉ khi **mọi** case AC. Thứ tự ưu tiên xấu: CE > RE > TLE > MLE > WA > AC (xem `VerdictAggregator`).
Spam-guard interview: verdict chỉ CE/RE → bỏ qua chấm AI (xem `[[project_coding_interview_design]]`).

---

## 5. Bảng kiểu dữ liệu (NGUỒN SỰ THẬT — phải khớp 3 nơi)

Bất kỳ token kiểu nào cũng phải tồn tại đồng thời ở:
- `AI/generator/nodes/problem_analyzer.py` → `_ALLOWED_PARAM_TYPES`
- `judge-service/.../codebuilder/TypeSerializer.java`
- `judge-service/src/main/resources/drivers/UniversalJavaDriver.java` (`getJavaType`) + 3 driver còn lại + `CodeBuilder.cppTypeFor`

| Nhóm | Token hợp lệ |
|---|---|
| Scalar | `int`,`long`,`double`,`boolean`,`Integer`,`Long`,`Double`,`Boolean`,`char`,`Character`,`String`,`string` |
| Array 1D | `int[]`,`long[]`,`double[]`,`String[]`,`string[]` |
| Array 2D | `int[][]`,`char[][]`,`String[][]` |
| Generic | `List<Integer>`,`List<String>`,`List<List<Integer>>`,`List<List<String>>` |
| Struct | `TreeNode`,`ListNode` |
| Return-only | `void` |

Thêm kiểu mới = phải sửa **cả 3 nơi** cùng lúc, nếu không StdinBuilder/driver sẽ RE.

---

## 6. Các lỗi đã gặp & bất biến phải giữ (CASE STUDY — đọc kỹ)

### Lỗi #1 — Judge decode base64 sai → WA giả với MỌI output dài (đã fix 2026-06-11)
- **Triệu chứng:** lời giải đúng nhưng các case có output lớn bị WA; case nhỏ thì AC.
  (Merge Sorted Array: RUN 3/3 AC nhưng SUBMIT WA đúng 2 case lớn nhất.)
- **Bằng chứng:** trong `judge.judge_task_results`, `stdout` còn nguyên base64 **có `\n` ở giữa**;
  decode tay ra **đúng khớp** `expected`.
- **Gốc rễ:** Judge0 trả stdout base64 dạng **MIME (RFC 2045)** — chèn `\n` mỗi 76 ký tự khi output
  > ~57 byte. `decodeBase64` cũ dùng `Base64.getDecoder()` (basic) → gặp `\n` là **ném exception**
  → khối `catch` trả lại **nguyên chuỗi base64** → đem so với JSON → WA. Output ngắn lọt 1 dòng nên
  may mắn pass → giải thích vì sao RUN (case nhỏ) qua mà SUBMIT (case lớn) trượt.
- **Fix:** `JudgeOrchestrator.decodeBase64` dùng **`Base64.getMimeDecoder()`** (bỏ qua line
  separator). Ảnh hưởng mọi bài có output > 57 byte, cho cả interview lẫn practice.
- **BẤT BIẾN:** đừng bao giờ "đơn giản hoá" về `getDecoder()`. Bất kỳ chỗ nào decode output Judge0
  đều phải khoan dung với newline.

### Lỗi #2 — Test case nhiều đáp án hợp lệ (Two Sum) → WA oan (đã fix phía prompt 2026-06-11)
- **Triệu chứng:** Two Sum SUBMIT WA vài hidden case dù code thí sinh đúng.
- **Bằng chứng:** `expected [0,4]` vs `stdout [2,3]` — input `nums=[1,5,8,12,19] target=20` có **2 cặp**
  cùng tổng 20 (1+19 và 8+12). AI chốt cặp này, lời giải chuẩn (hash-map) trả cặp kia.
- **Gốc rễ:** AI **không chạy code** (§1.5); với bài nhiều đáp án, `expectedOutput` cố định là sai
  về bản chất. `orderMatters=false` chỉ cứu khác **thứ tự**, không cứu khác **giá trị**.
- **Fix:** thêm luật #9 "ANSWER MUST BE UNIQUE PER INPUT" vào
  `AI/generator/prompts/testcase_generation.py` — ép generator dựng input có **đúng một** đáp án
  (đúng cam kết "exactly one solution" của LeetCode). Chỉ áp dụng cho bài **sinh mới**; data cũ
  trong DB vẫn ambiguous cho tới khi regenerate.
- **BẤT BIẾN:** với bài "return any / nhiều nghiệm", hoặc (a) ép input một-nghiệm, hoặc (b) cần
  special-judge. Hệ hiện tại **chỉ exact-match**, nên mặc định phải đi đường (a).

### Bất biến khác (nhợp đồng ngầm dễ vỡ)
- `functionMeta` JSON dùng key **`"return"`** (không phải `returnType`) ở mọi nơi serialize sang judge.
- `expectedOutput` luôn **đúng một key** (driver lấy field đầu, key thừa thành state ẩn).
- Keys `inputData` == đúng `params[].name` (StdinBuilder tra theo tên).
- `inPlace=true` ⇒ `return_type="void"` và `expectedOutput.result` khớp shape `params[0]`.
- Thêm kiểu dữ liệu = sửa cả 3 nơi ở §5.

---

## 7. Cách debug nhanh trên VPS (lệnh thật)

SSH: `ssh contabo`. Hai DB engine: **Postgres** `mockwise-infra-postgres-1`, **MySQL** `mockwise-infra-mysql-1`.

```bash
# Đề coding (Postgres, DB "question_bank")
ssh contabo "docker exec mockwise-infra-postgres-1 psql -U mockwise -d question_bank -tAc \
  \"SELECT id, title, function_meta FROM coding_questions;\""

# Một test case cụ thể theo id
ssh contabo "docker exec mockwise-infra-postgres-1 psql -U mockwise -d question_bank -tAc \
  \"SELECT jsonb_pretty(tc) FROM coding_questions, jsonb_array_elements(test_cases) tc \
    WHERE (tc->>'id')='<TESTCASE_ID>';\""

# Submission của practice (Postgres, DB "practice", bảng practice_submission / _case)
ssh contabo "docker exec mockwise-infra-postgres-1 psql -U mockwise -d practice -tAc \
  \"SELECT problem_title, mode, status, verdict, passed_cases, total_cases \
    FROM practice_submission ORDER BY created_at DESC LIMIT 15;\""

# stdout/expected THẬT của judge (MySQL, DB "judge") — vàng cho debug WA
ssh contabo "docker exec mockwise-infra-mysql-1 mysql -uroot -p<ROOT_PW> judge -e \
  \"SELECT order_index, status, expected_output, stdout FROM judge_task_results \
    WHERE status='WA' ORDER BY created_at DESC LIMIT 8\G\""
```
> Mật khẩu MySQL lấy từ env container: `docker inspect mockwise-infra-mysql-1 --format '{{range .Config.Env}}{{println .}}{{end}}' | grep MYSQL_`.
> hidden case bị strip stdout ở practice DB (privacy) → muốn xem output thật phải tra **judge DB**.

**Quy trình chẩn đoán "chạy bài bị sai":**
1. Lấy stdout thật từ judge DB. Nếu là **base64 có `\n`** → lỗi #1 (decode).
2. Decode stdout: nếu **== expected** → false negative (so sánh/decode), KHÔNG phải lỗi thí sinh.
3. Nếu stdout là một đáp án hợp lệ **khác** expected → lỗi #2 (test case ambiguous).
4. Nếu shape lệch (scalar vs array, thiếu nháy…) → sai `functionMeta`/serialize (§4.2, §5).

---

## 8. File tham chiếu nhanh

| Mảng | Đường dẫn |
|---|---|
| LangGraph flow | `AI/generator/graph.py` |
| Sinh / validate testcase | `AI/generator/nodes/testcase_{generator,validator}.py` |
| Prompt sinh testcase (luật #9) | `AI/generator/prompts/testcase_generation.py` |
| Model output AI | `AI/models/generator_outputs.py` |
| Lưu coding | `question-bank-service/.../controller/AdminQuestionController.java`, `entity/CodingQuestion.java` |
| Endpoint internal | `question-bank-service/.../controller/InternalCodingProblemController.java` |
| Run/Submit | `practice-service/.../service/PracticeSubmissionService.java` |
| Dựng source + stdin | `judge-service/.../codebuilder/{CodeBuilder,StdinBuilder,TypeSerializer}.java` |
| Driver thực thi | `judge-service/src/main/resources/drivers/Universal*Driver.*` |
| Gọi Judge0 | `judge-service/.../judge0/Judge0Client.java` |
| Callback + chấm + decode | `judge-service/.../service/JudgeOrchestrator.java` |
| So output | `judge-service/.../codebuilder/OutputComparator.java` |

Liên quan: `[[project_coding_interview_design]]`, `[[project_practice_leetcode_feature]]`,
`[[feedback_spring_service_runtime_gotchas]]`, `[[project_vps_deploy_ops]]`.
