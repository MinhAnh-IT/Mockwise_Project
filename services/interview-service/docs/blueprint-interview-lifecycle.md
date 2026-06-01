# Blueprint & Vòng đời một buổi phỏng vấn

> Tài liệu nghiệp vụ (backend, interview-service). Giải thích **blueprint là
> gì**, nó **lập kế hoạch (plan)** một buổi phỏng vấn ra sao, **chọn câu hỏi**
> theo cơ chế nào, **luồng khớp blueprint với user** khi bấm `/start`, và
> **toàn bộ vận hành** một buổi phỏng vấn chạy trên blueprint đó.
>
> Đây là tài liệu "đọc để hiểu hệ thống", viết dựa trực tiếp trên code thật:
> - `entity/InterviewBlueprint.java`, `entity/BlueprintTopic.java`
> - `service/BlueprintLoader.java`, `service/QuestionPicker.java`
> - `service/SessionService.java`, `service/AnswerService.java`
> - `planner/NextQuestionPlanner.java`
> - `db/migration/V3__seed_behavioral_and_core_blueprints.sql`,
>   `V5__seed_coding_blueprints.sql`
>
> Tài liệu chị em:
> - `docs/question-selection-design.md` — đặc tả gốc cây quyết định planner.
> - `docs/start-session-first-question-flow.md` — chi tiết bước `/start`.
> - `frontend/docs/blueprint-admin-design.md` — giao diện admin CRUD blueprint.

---

## Mục lục

1. [Blueprint là gì — mô hình dữ liệu](#1-blueprint-là-gì--mô-hình-dữ-liệu)
2. [Ba loại phỏng vấn & khác biệt cốt lõi](#2-ba-loại-phỏng-vấn--khác-biệt-cốt-lõi)
3. [Blueprint "plan" một buổi phỏng vấn như thế nào](#3-blueprint-plan-một-buổi-phỏng-vấn-như-thế-nào)
4. [Luồng khớp blueprint với user khi `/start`](#4-luồng-khớp-blueprint-với-user-khi-start)
5. [Vận hành buổi BEHAVIORAL / CORE (adaptive)](#5-vận-hành-buổi-behavioral--core-adaptive)
6. [Vận hành buổi CODING (non-adaptive)](#6-vận-hành-buổi-coding-non-adaptive)
7. [Cơ chế chọn câu hỏi chi tiết (QuestionPicker)](#7-cơ-chế-chọn-câu-hỏi-chi-tiết-questionpicker)
8. [Kết thúc & chấm điểm tổng](#8-kết-thúc--chấm-điểm-tổng)
9. [Đồng hồ thời gian (time budget) & reaper](#9-đồng-hồ-thời-gian-time-budget--reaper)
10. [Ví dụ end-to-end](#10-ví-dụ-end-to-end)
11. [Bảng tham số các blueprint seed](#11-bảng-tham-số-các-blueprint-seed)
12. [Cạm bẫy & quy tắc bất biến](#12-cạm-bẫy--quy-tắc-bất-biến)

---

## 1. Blueprint là gì — mô hình dữ liệu

`InterviewBlueprint` là **mẫu cấu hình (template) cho một buổi phỏng vấn**. Nó
**không** phải là một buổi phỏng vấn cụ thể — nó là khuôn mà mỗi `/start` sẽ
sao chép ra để dựng một `InterviewSession`. Một blueprint được khoá logic theo
bộ ba:

```
(targetRole, level, interviewType)
```

Mỗi bộ ba chỉ được có **đúng một** blueprint `is_default = TRUE` (ràng buộc
partial-unique index ở DB). Đây chính là bản sẽ được dùng khi user bắt đầu phỏng
vấn với đúng track/level/loại đó.

### Các trường của `interview_blueprint`

| Trường | Ý nghĩa | Mặc định |
|---|---|---|
| `target_role` | Track ứng tuyển, ĐÃ normalize (vd `BACKEND`, `FRONTEND`, `FULLSTACK`). ≤20 ký tự | — |
| `level` | Cấp độ, ĐÃ normalize lowercase: `junior` / `mid` / `senior`. ≤20 ký tự | — |
| `interview_type` | `BEHAVIORAL` \| `CORE` \| `CODING` (`MIXED` đã bị gỡ khỏi enum) | — |
| `topics` (jsonb) | Mảng `BlueprintTopic[]`. **Khác nghĩa hoàn toàn** giữa adaptive vs CODING (xem §2-3) | `[]` |
| `question_budget` | Trần số câu hỏi của cả phiên. Với CODING = số slot | `8` |
| `max_follow_ups_per_topic` | Trần follow-up cho **mỗi topic**. CODING = `0` | `2` |
| `max_follow_ups_per_session` | Trần follow-up cho **cả phiên**. CODING = `0` | `4` |
| `time_budget_minutes` | Tổng thời lượng (đồng hồ phiên, không có giới hạn theo từng câu) | `45` |
| `use_ai_selector` | Cờ bật bộ chọn câu kế tiếp bằng AI (xem ghi chú §12) | `false` |
| `is_default` | Bản mặc định cho bộ ba. Chỉ 1 bản TRUE / bộ ba | `false` |
| `created_at` / `updated_at` | Dấu thời gian | auto |

### `BlueprintTopic` (mỗi phần tử trong `topics`)

```jsonc
{
  "kind": "COMPETENCY",        // COMPETENCY | DOMAIN  (CODING: bỏ trống)
  "topicValue": "OWNERSHIP",   // tên enum Competency/Domain của question-bank
  "importance": "HIGH",        // HIGH | MED | LOW
  "targetDifficulty": "EASY",  // EASY | MEDIUM | HARD
  "orderHint": 1               // số thứ tự gợi ý (≥0)
}
```

> ⚠️ `topicValue` là **String** cố ý — nó phải khớp **byte-for-byte** tên enum
> `Competency` (taxonomy chính = **16 Amazon Leadership Principles**:
> `CUSTOMER_OBSESSION`, `OWNERSHIP`, `ARE_RIGHT_A_LOT`, `LEARN_AND_BE_CURIOUS`,
> `BIAS_FOR_ACTION`, `DIVE_DEEP`, `DELIVER_RESULTS`,
> `HAVE_BACKBONE_DISAGREE_AND_COMMIT`, `THINK_BIG`, `EARN_TRUST`, ... + block
> LEGACY cũ như `TEAMWORK`/`COMMUNICATION`) hoặc `Domain`
> (`DATABASE`, `SYSTEM_DESIGN`, `LANGUAGE_SPECIFIC`, `DESIGN_PATTERN`,
> `CACHING`, `TESTING`, `NETWORKING`, `SECURITY`, `FRONTEND_DEV`, ...) của
> question-bank. Việc validate khớp enum xảy ra lúc **load**, không phải lúc
> parse JSON — nên admin có thể stage blueprint trỏ tới enum tương lai. Nếu
> `topicValue` sai chính tả, lúc chọn câu hỏi sẽ không match và rơi vào nhánh
> relax (§7).

### Quan hệ blueprint ↔ session

```
InterviewBlueprint (catalog, admin sửa)
        │  copy tại /start (đóng băng)
        ▼
InterviewSession (1 buổi cụ thể của 1 user)
        ├── session_topic_state[]   ← chỉ adaptive: ma trận tiến độ từng topic
        ├── session_question[]      ← các câu đã "pin" (gắn) cho phiên
        └── metadata.codingPlan / profileSnapshot / overallReview
```

Điểm mấu chốt: session **sao chép** topic list vào `session_topic_state` tại
`/start`, nên admin sửa blueprint sau đó **không** ảnh hưởng các phiên đang
chạy. Session cũng giữ `blueprintId` để planner tra lại các trần (budget,
follow-up) trong suốt phiên.

---

## 2. Ba loại phỏng vấn & khác biệt cốt lõi

`interviewType` quyết định **toàn bộ cơ chế vận hành**. Đây là bảng so sánh
quan trọng nhất của tài liệu:

| | BEHAVIORAL | CORE | CODING |
|---|---|---|---|
| Bản chất | Adaptive (thích nghi) | Adaptive | **Non-adaptive** (cố định) |
| `topics[]` nghĩa là | ma trận **năng lực** cần đo | ma trận **lĩnh vực kỹ thuật** | **lịch độ khó có thứ tự** (mỗi slot = 1 bài) |
| `kind` mỗi topic | `COMPETENCY` | `DOMAIN` | bỏ trống (không dùng) |
| `topicValue` | tên `Competency` | tên `Domain` | bỏ trống |
| Loại câu hỏi bank | `BEHAVIORAL` | `CORE_CONCEPTUAL` | `LIVE_CODING` |
| Có planner không? | ✅ có | ✅ có | ❌ không |
| Có follow-up không? | ✅ (theo trần) | ✅ (theo trần) | ❌ (trần = 0) |
| Có ma trận `session_topic_state`? | ✅ | ✅ | ❌ |
| Câu kế tiếp được chọn khi nào | sau khi AI đánh giá câu trước | sau khi AI đánh giá câu trước | **pin ngay** khi submit câu trước |
| Chấm điểm 1 câu | AI evaluator (transcript) | AI evaluator (transcript) | judge-service (test cases) → AI live_coding |
| `question_budget` | trần số câu (planner dừng khi chạm) | như BEHAVIORAL | = số slot (cố định) |

**Tóm tắt tư duy:**
- **BEHAVIORAL/CORE = một cuộc hội thoại có định hướng**. Blueprint cấp một
  "ngân sách" và một "danh mục topic"; planner đóng vai người phỏng vấn quyết
  định: đào sâu (follow-up), chuyển chủ đề, hay kết thúc — dựa trên chất lượng
  từng câu trả lời.
- **CODING = một đề thi cố định**. Blueprint là một bảng kê độ khó; hệ thống
  bốc đủ số bài `LIVE_CODING` ngay từ đầu, không có "thông minh" gì giữa chừng.

---

## 3. Blueprint "plan" một buổi phỏng vấn như thế nào

Blueprint mã hoá kế hoạch buổi phỏng vấn qua 4 đòn bẩy:

### 3.1 Danh mục chủ đề (`topics`) + tầm quan trọng + độ khó mục tiêu

Với **adaptive** (BEHAVIORAL/CORE), `topics` là **tập các chủ đề cần phủ**. Mỗi
topic mang:
- `importance` (HIGH/MED/LOW): quyết định **thứ tự ưu tiên** khi planner chọn
  topic kế tiếp (importance cao đi trước).
- `targetDifficulty` (EASY/MEDIUM/HARD): độ khó **mong muốn** của câu mở đầu cho
  topic đó (sẽ được điều chỉnh ± theo phong độ ứng viên).
- `orderHint`: gợi ý thứ tự, dùng để chọn **câu mở màn** và phá hoà khi cùng
  importance.

Ví dụ (seed `BACKEND-mid-BEHAVIORAL`): mở đầu nhẹ bằng `OWNERSHIP (EASY)` ở
`orderHint=1`, rồi mới tới các tín hiệu cao hơn `CONFLICT_RESOLUTION`,
`COMMUNICATION` (HIGH, MEDIUM). Đây là cách admin "thiết kế cung bậc" buổi PV:
khởi động dễ → leo lên topic quan trọng.

Với **CODING**, `topics` là **lịch độ khó**: mỗi phần tử là một "slot" cho đúng
một bài, đọc `targetDifficulty` theo thứ tự `orderHint`. `kind`/`topicValue`
không dùng. Ví dụ seed `BACKEND-mid-CODING`: ramp `EASY → MEDIUM → MEDIUM →
HARD`.

### 3.2 Ngân sách câu hỏi (`question_budget`)

Trần cứng tổng số câu (gồm cả follow-up) cho cả phiên. Với adaptive, planner
**luôn kiểm tra budget trước tiên** — pin đủ `questionBudget` câu là dừng
(`QUESTION_BUDGET_EXHAUSTED`), bất kể còn topic chưa phủ. Điều này chặn vòng lặp
vô hạn nếu blueprint cấu hình lệch. Với CODING, budget = số slot = số bài.

### 3.3 Ngân sách follow-up (2 tầng trần)

- `max_follow_ups_per_topic`: tối đa số lần đào sâu trong **một** topic.
- `max_follow_ups_per_session`: tối đa số follow-up trong **cả** phiên.

Planner xét cả hai trước khi quyết định đào sâu (§5.3). CODING đặt cả hai = 0.

### 3.4 Ngân sách thời gian (`time_budget_minutes`)

Đồng hồ **cấp phiên** (không có giới hạn theo từng câu). `deadline = startedAt +
timeBudgetMinutes`. Hết giờ → phiên tự `COMPLETED` (§9). User có thể override
qua `timeBudgetMinutesOverride` lúc `/start`.

### 3.5 Độ khó: blueprint chỉ cấp "điểm neo", phiên tự điều chỉnh

`targetDifficulty` của topic là **điểm neo**. Độ khó thực tế của câu = điểm neo
±offset, trong đó offset đến từ:
- `globalDifficultyOffset` (tính từ kinh nghiệm user lúc `/start`, §4).
- `stretchMode` (bật khi ứng viên trả lời tốt liên tiếp, §5).
- offset một-lần do planner áp cho từng nước đi (vd Case A hạ 1 nấc).

→ Cùng một blueprint, hai ứng viên khác phong độ sẽ nhận độ khó câu hỏi khác
nhau. **Đây là phần "adaptive" cốt lõi.**

---

## 4. Luồng khớp blueprint với user khi `/start`

`SessionService.start()` (toàn bộ chạy trong **một transaction**):

```
POST /api/v1/interviews/start   { interviewType, timeBudgetMinutesOverride? }
        │
        ▼
1. Lấy hồ sơ user  ── userProfileAdapter.getProfile(userId)
        │            (track, level, kinh nghiệm, techStack, industries, ngôn ngữ)
        ▼
2. Normalize khoá blueprint
     role  = BlueprintNormalizer.normalizeRole(profile.position.trackName)
     level = BlueprintNormalizer.normalizeLevel(profile.position.levelName)
        │   vd "Back-End"→BACKEND ; "Senior"/"Lead"/"Staff"→senior ; "Fresher"→junior
        ▼
3. Tra blueprint mặc định
     blueprintLoader.findFor(role, level, interviewType)
        │   = findFirst...AndIsDefaultTrue(...)
        │   không có  →  BLUEPRINT_NOT_FOUND (4042)
        ▼
4. Tính globalDifficultyOffset từ kinh nghiệm  (computeDifficultyOffset)
        ▼
5. Snapshot hồ sơ vào session.metadata.profileSnapshot
        │   (để pipeline trả lời không phải gọi lại user-profile mỗi câu)
        ▼
6. Rẽ nhánh theo interviewType  →  §5 (adaptive)  hoặc  §6 (CODING)
```

### 4.1 Normalize khoá — vì sao quan trọng

User profile lưu track/level dạng "đẹp cho người đọc" (`"Back-End Engineer"`,
`"Senior"`). Blueprint lưu token chuẩn (`BACKEND`, `senior`).
`BlueprintNormalizer` là cầu nối: alias-table + upper/lower + bỏ ký tự lạ. Nếu
không khớp, `findFor` trả empty → `BLUEPRINT_NOT_FOUND`. Vì thế admin **phải**
tạo blueprint bằng token chuẩn (xem cạm bẫy §12).

### 4.2 `globalDifficultyOffset` — hiệu chỉnh theo kinh nghiệm

```java
EXPECTED_MIN_YEARS = { junior:0, mid:2, senior:5 }
effectiveYears = yearsInCurrentRole != null
               ? min(experience, yearsInCurrentRole + 1)   // vừa đổi track → dễ hơn
               : experience
diff = effectiveYears - EXPECTED_MIN_YEARS[level]
offset = diff < -1 ? -1 : 0
```

- Nếu ứng viên được gán level **cao hơn** kinh nghiệm thực hỗ trợ (vd "senior"
  nhưng mới 2 năm) → `offset = -1`: dễ hơn 1 nấc toàn cục.
- `yearsInCurrentRole` **thắng** tổng kinh nghiệm: một senior vừa nhảy sang track
  mới được nương tay, dù tổng số năm cao.
- Hệ thống **không** chủ động tăng offset cho người "thừa kinh nghiệm" — việc
  nâng độ khó dành cho `stretchMode` trong planner (chạy theo phong độ thực tế),
  tránh đánh giá sai từ con số CV.

---

## 5. Vận hành buổi BEHAVIORAL / CORE (adaptive)

### 5.1 `/start` cho adaptive

```
6a. Tạo InterviewSession (status = IN_PROGRESS ngay, bỏ qua CREATED)
        questionCount = blueprint.questionBudget
        timeBudgetMinutes, globalDifficultyOffset, startedAt, metadata
        ▼
6b. blueprintLoader.seedTopicStates(sessionId, blueprint)
        → mỗi BlueprintTopic ⇒ 1 hàng session_topic_state ở trạng thái NOT_TESTED
          (kèm importance + targetDifficulty copy sang, questionsAsked=0, followUpsUsed=0)
        ▼
6c. firstTopic = blueprintLoader.pickFirstTopic(blueprint)
        ▼
6d. openingDifficulty = computeOpeningDifficulty(firstTopic, globalOffset)
        ▼
6e. firstQuestion = questionPicker.pickForTopic(...)  → session_question[1]
        ▼
6f. Promote topic đó: NOT_TESTED → PROBING, questionsAsked=1, lastDifficulty=...
        ▼
Trả StartSessionOutput (câu 1 đã .redacted() — giấu rubric/đáp án)
```

**Chọn câu mở màn (`pickFirstTopic`):** sắp xếp theo `orderHint` tăng dần, phá
hoà bằng importance giảm. Với BEHAVIORAL (mọi loại khác CORE) còn **ưu tiên
topic `COMPETENCY` trước** để câu đầu là câu mở (open-ended), không "đập" ngay
câu kỹ thuật khó. Blueprint chịu trách nhiệm đặt câu khởi động nhẹ ở
`orderHint=1`.

**Độ khó câu mở màn (`computeOpeningDifficulty`):** lấy `targetDifficulty` của
topic **hạ 1 nấc** (`downgrade()`) — luôn mở nhẹ hơn target để ứng viên vào
guồng; nếu `globalOffset < 0` thì hạ thêm 1 nấc nữa. (`EASY.downgrade()` vẫn là
`EASY` — không có gì dưới EASY.)

### 5.2 Vòng lặp một câu hỏi (cốt lõi của adaptive)

Mỗi câu trả lời chạy qua vòng đời bất đồng bộ qua Kafka:

```
User trả lời (VIDEO) → AnswerService.submit()
        │   ghi Answer (SUBMITTED→PROCESSING), stage "answer-submitted"
        ▼
tts-stt-service: ghi âm → transcript  → applyTranscriptReady()
        │   build payload đánh giá (kèm expectedSignals/keyConcepts từ snapshot)
        │   stage "evaluation-requested"
        ▼
ai-service: chấm câu trả lời  → applyEvaluationCompleted(answerId, rawEvaluation)
        │   - deriveVerdict(): chuẩn hoá thành AssessmentVerdict
        │     (scoreNormalized 0-10, correctness, completeness, depth,
        │      signalStrength, hireSignal, weakTargets[], strongTargets[])
        │   - lưu verdict, Answer → SCORED
        ▼
   *** runPlannerForAnswer() — "nhịp thông minh" ***
        │   1. cập nhật session_topic_state hiện tại (lastDifficulty/score/assessment)
        │   2. gom statuses tất cả topic + đếm số câu đã pin
        │   3. planner.plan(PlannerInputs) → PlannerOutcome { decision, sideEffects }
        │   4. sideEffectApplier.apply(...) — ghi diff vào JPA cùng transaction
        │   5. switch decision:
        │        AskFollowUp     → pin câu follow-up (Tier1 bank / Tier2 AI)
        │        MoveToNextTopic → pickForTopic topic mới, promote PROBING
        │        EndSession      → session → COMPLETED
        ▼
   sessionFinalizer.maybeRequestOverallReview() — gate chấm tổng (§8)
```

> Câu kế tiếp **chỉ xuất hiện sau khi AI chấm xong câu trước**. Đó là lý do
> luồng này bất đồng bộ và "có độ trễ" — đổi lại được tính thích nghi.

### 5.3 Cây quyết định của planner (`NextQuestionPlanner.plan`)

Planner là **hàm thuần** (pure): nhận snapshot bất biến `PlannerInputs`, trả
`PlannerOutcome`. Thứ tự nhánh **rất quan trọng**:

**Ngưỡng (trên thang 10):**
- `CASE_A_SCORE_THRESHOLD = 3.0` — dưới mức này = "rất tệ".
- `CASE_D_SCORE_THRESHOLD = 7.0` — từ mức này = "mạnh".
- `ADEQUATE_SCORE_THRESHOLD = 6.0` — chốt topic là ADEQUATE thay vì PARTIAL.
- `CONSECUTIVE_UNKNOWN_CRITICAL = 3` — 3 lần liên tiếp "không trả lời" → hạ độ khó toàn cục.
- `STRETCH_MODE_TRIGGER = 3` — 3 câu mạnh liên tiếp → bật stretch mode.

**Thứ tự xét:**

1. **Case B — NO_ANSWER (bỏ qua/không trả lời).** Ưu tiên cao nhất, override mọi
   nhánh điểm. Đóng topic = `UNKNOWN`, tăng `consecutiveUnknownCount`. Nếu chạm
   `CONSECUTIVE_UNKNOWN_CRITICAL` → set `globalDifficultyOffset = -1` (hạ toàn
   cục, áp ngay cho câu kế). Không follow-up.

2. **Case D — STRONG.** Điều kiện: `score ≥ 7` **và** `signalStrength = STRONG`
   **và** `hireSignal ∈ {yes, strong_yes}`. Đóng topic = `STRONG`, tăng
   `runningStrongCount`, reset chuỗi unknown. Đủ `STRETCH_MODE_TRIGGER` câu mạnh
   liên tiếp → bật `stretchMode` (nâng độ khó +1 cho các câu sau). Chuyển topic.

3. **Case A — câu trả lời rất tệ** (`score < 3`):
   - **A1 — WRONG** (hiểu sai nền tảng): bỏ topic ngay, đóng = `WEAK`, câu kế hạ
     1 nấc.
   - **A2 — MIXED + SURFACE** (có ý nhưng nông) và topic chưa dùng follow-up:
     cho **một** cơ hội thứ hai bằng follow-up ở độ khó hạ nấc, nhắm vào
     weak-target nặng nhất. Hết cơ hội → đóng `WEAK`.

4. **Case C — mặc định (câu trung bình).** Xét follow-up (`shouldFollowUp`):
   - Còn quota topic (`followUpsUsed < maxFollowUpsPerTopic`) **và** phiên
     (`totalFollowUpsUsed < maxFollowUpsPerSession`).
   - Có dấu hiệu cần đào sâu: `signalStrength ∈ {PARTIAL, NONE}` **hoặc** có
     weak-target severity HIGH **hoặc** câu chưa đầy đủ/nông
     (`INCOMPLETE`/`SURFACE`/`MODERATE`).
   - **Và** có weak-target cụ thể để nhắm (không có thì không bịa).
   - Thoả → `AskFollowUp` (nhắm weak-target nặng nhất, độ khó = lastDifficulty).
   - Không thoả → `handleCaseCClose`: chốt topic `ADEQUATE` (score ≥ 6) hoặc
     `PARTIAL`, chuyển topic.

5. **Điều kiện dừng** (trong `moveToNextOrEnd`), xét **theo thứ tự**:
   - **Budget:** đã pin ≥ `questionBudget` câu → `EndSession(QUESTION_BUDGET_EXHAUSTED)`.
     (Thắng coverage để chặn loop.)
   - **Coverage:** không còn topic `NOT_TESTED` nào ngoài topic đang đóng →
     `EndSession(COVERAGE_COMPLETE)`.
   - Còn topic → `pickNextTopic`.

**`pickNextTopic` (§6 của design):** trong các topic còn `NOT_TESTED`, chọn theo
`importance DESC → orderHint ASC → ưu tiên khác kind với topic hiện tại` (để xen
kẽ behavioral/domain, tránh dồn cùng loại).

**Tính độ khó topic kế (`computeOpeningDifficulty`):** dùng giá trị **hậu quyết
định** (effective offset/stretch sau khi áp side-effect của chính nước đi này),
cộng offset một-lần, clamp trong `[-1, +1]`, rồi `upgrade()`/`downgrade()` từ
`targetDifficulty` của topic kế.

### 5.4 Follow-up được lấy ở đâu (2 tầng)

Khi planner quyết `AskFollowUp`, `AnswerService.handleFollowUp` thử:
- **Tier 1 — `pickFollowUpFromBank`:** tìm follow-up **soạn sẵn** trong
  question-bank khớp `(parentQuestionId, weakTarget.kind, weakTarget.value)`.
  Lưu `source = PRE_AUTHORED_FOLLOWUP`. (Nếu câu cha vốn là AI-generated, bỏ qua
  Tier 1 luôn.)
- **Tier 2 — `pickFollowUpFromAi`:** gọi ai-service sinh follow-up tại chỗ dựa
  trên transcript câu cha + weak-target + strong-targets. Lưu `source =
  AI_GENERATED`, **không có `questionId`** (không ghi ngược về bank) — nội dung
  nằm inline trên `SessionQuestion`.

---

## 6. Vận hành buổi CODING (non-adaptive)

CODING bỏ qua hoàn toàn planner, topic-state matrix và follow-up. Toàn bộ "kế
hoạch" được chốt ngay tại `/start`.

### 6.1 `/start` cho CODING

```
difficultyPlan = blueprintLoader.codingDifficultyPlan(blueprint)
     → sort topics theo orderHint, map sang List<Difficulty>
       (slot thiếu targetDifficulty → mặc định MEDIUM)
        ▼
codingPlan = questionPicker.selectCodingPlan(difficultyPlan)
     → với MỖI độ khó trong plan:
         · gọi question-bank /filter (type=LIVE_CODING, difficulty=d)
         · shuffle pool, lấy bài đầu tiên CHƯA chọn
         · BẮT BUỘC snapshot có functionMeta + testCases (judge cần) — không có thì bỏ qua
         · nếu độ khó đúng cạn → relax sang bất kỳ độ khó nào
         · markAskedSoft(id)
         · entry = { questionId, difficulty, snapshot }
     → trả List entry (1 entry / bài). Nếu bank quá mỏng:
         · cắt ngắn plan (phiên ngắn-mà-hợp-lệ > phiên hỏng)
         · rỗng hoàn toàn → QUESTION_BANK_UNAVAILABLE
        ▼
Lưu codingPlan vào session.metadata.codingPlan
        ▼
Tạo InterviewSession (questionCount = codingPlan.size())
        ▼
pinCoding(plan[0], sequence=1)  → CHỈ pin câu 1
        │  (FE advance theo hàng pin cuối; pin hết một lúc sẽ nhảy thẳng tới cuối)
        ▼
Trả câu 1 (.redacted())
```

> Lưu ý: `questionCount` thực tế = **độ dài plan đã chọn được**, có thể **nhỏ
> hơn** `questionBudget` nếu bank không đủ bài distinct.

### 6.2 Vòng lặp một bài coding

```
User viết code → submit
        │
   ┌────┴─────────────────────────────────────────────┐
   │ AnswerService.submit() (type=CODE):               │
   │  · verifyStorageObject, ghi Answer                │
   │  · stage "code-answer-submitted" → judge-service  │
   │    (payload: submissionId, language, code,        │
   │     functionMeta, testCases từ snapshot đóng băng) │
   │  · pinNextCodingIfAny(session, currentSq)          │
   │      → pin NGAY bài kế (sequence+1) từ codingPlan  │
   │        (Task.md: "submit … load câu tiếp lập tức") │
   └────┬─────────────────────────────────────────────┘
        ▼
judge-service: chạy test cases → applyCodeJudged(answerId, verdict, results)
   · verdict không chạy được (CE/RE) → SCORED ngay, BỎ QUA AI (spam-guard, verdict rẻ)
   · verdict chạy được (passed/total) → lưu summary judge, stage
     "coding-evaluation-requested" → ai-service (EVALUATING)
        ▼
ai-service (live_coding eval): applyEvaluationCompleted → Answer SCORED
        │  (KHÔNG chạy planner: câu coding không gắn topic → skip,
        │   chỉ gọi maybeRequestOverallReview)
        ▼
Khi bài cuối submit: pinNextCodingIfAny thấy hết plan → không pin gì;
phiên finalise sau khi verdict bài cuối SCORED (hoặc user /finish, hoặc hết giờ)
```

**Điểm khác biệt then chốt:** ở CODING, bài kế được **pin ngay lúc submit** (FE
chuyển bài lập tức, không chờ chấm). Việc chấm (judge → AI) chạy nền song song,
chỉ phục vụ điểm cuối. Adaptive thì ngược lại — phải chờ chấm xong mới có câu
kế.

**Spam-guard:** nếu code không compile/không chạy (CE/RE), bỏ qua bước AI tốn
kém, chốt một verdict rẻ ngay — tránh đốt token cho code không chạy được.

---

## 7. Cơ chế chọn câu hỏi chi tiết (QuestionPicker)

### 7.1 `pickForTopic` — chọn câu cho một topic (adaptive)

Dùng cho câu mở màn (`/start`) và mỗi lần `MoveToNextTopic`. Thuật toán:

```
type = COMPETENCY → BEHAVIORAL ; DOMAIN → CORE_CONCEPTUAL
tagBias = techStack ∪ industries (lowercase)  ← từ profile snapshot

Chuỗi nới lỏng (relax sequence):
  1. tryFilter(requireOpener = (sequence==1))   ← câu đầu phiên ưu tiên câu "opener"
  2. tryFilter(requireOpener = false)
  3. tryFilterRelaxed: bỏ targetRole + tagBias, quét difficulty ±1
  → vẫn rỗng ⇒ QUESTION_BANK_UNAVAILABLE (lỗi cứng: không có câu thì không tiến được)

Chấm điểm cục bộ trên pool (applyLocalScoring):
  + 10  nếu requireOpener & câu là opener
  + 3   mỗi lần tag câu trùng techStack
  + 3   mỗi lần tag câu trùng industries
  − askCount/100   (tie-breaker nhẹ: ưu tiên câu ít được hỏi, không lấn bonus)
```

Sau khi chọn: `markAskedSoft(id)` (fire-and-forget, tăng ask_count), rồi pull
**rich snapshot** (expectedSignals/keyConcepts/depthExpected/testCases...) để
đóng băng vào `session_question.snapshot` — giúp evaluator & reviewer cuối phiên
không phải query lại. Outage bank → fallback thin snapshot (vẫn pin được).

`requireOpener` chỉ ở câu đầu phiên: ưu tiên câu mang cờ "opener" trong bank
(câu mở thoải mái như tự giới thiệu), giúp ứng viên vào guồng.

### 7.2 `selectCodingPlan` — chọn toàn bộ đề (CODING)

Đã mô tả ở §6.1. Khác biệt cốt lõi so với adaptive:
- Lấy **một lần** cả danh sách, **shuffle ngẫu nhiên** theo độ khó.
- **Bắt buộc** snapshot đủ `functionMeta + testCases` (judge cần) — bài thiếu bị
  bỏ qua, không như adaptive được phép fallback thin snapshot.
- Đảm bảo **không trùng** (set `chosen`).

### 7.3 Snapshot đóng băng — vì sao quan trọng

Mỗi `SessionQuestion.snapshot` lưu bản sao nội dung câu hỏi tại thời điểm pin
(text, audioKey, tags, và tuỳ loại: competency/expectedSignals,
domain/keyConcepts/depthExpected, hoặc title/description/functionMeta/
starterCode/testCases cho coding). Nhờ vậy: admin sửa/xoá câu trong bank **không**
làm hỏng phiên đang chạy hoặc báo cáo đã chấm, và evaluator có đủ "đáp án mẫu"
mà không cần gọi lại bank.

---

## 8. Kết thúc & chấm điểm tổng

Một phiên đi tới điểm cuối qua **một trong các đường**:
- Planner trả `EndSession` (budget cạn hoặc phủ hết topic) — adaptive.
- User bấm `/finish`.
- Hết `time_budget_minutes` (reaper hoặc guard, §9).
- (CODING) submit bài cuối + verdict của nó về.

Khi đó session → `COMPLETED`. Sau đó
`sessionFinalizer.maybeRequestOverallReview(sessionId)` là **cổng (gate)**: chỉ
khi **mọi** answer đã ở trạng thái terminal (SCORED/FAILED) mới stage **một**
request `overall-review` lên ai-service. Gate là idempotent — gọi nhiều lần chỉ
fire đúng một lần.

ai-service chấm tổng → `SessionService.applyOverallReview`:
- lưu `finalScore`, ghi `metadata.overallReview` (grade, hireSignal, nhận
  xét...), session → `SCORED`, `scoredAt = now`.
- stage `INTERVIEW_SCORED` cho mail-service gửi email kết quả.
- idempotent: delivery trùng đọc `status == SCORED` rồi bỏ qua.

Nếu chấm tổng lỗi → `applyOverallReviewFailed` ghi `metadata.overallReviewError`
nhưng **giữ** `COMPLETED` để operator replay được.

**Quyền xem:** chỉ khi `SCORED` thì API `/get` mới lộ đầy đủ rubric/đáp
án/phân loại + câu trả lời + verdict từng câu (`revealFull`). Poll giữa phiên
luôn `.redacted()` để ứng viên không đọc trộm đáp án/phân bố topic.

---

## 9. Đồng hồ thời gian (time budget) & reaper

- `deadlineOf(session) = startedAt + timeBudgetMinutes`. Một đồng hồ duy nhất cho
  cả phiên, **không** có giới hạn theo từng câu.
- **Guard khi ghi** (`ensureWithinTimeBudget`): mỗi lần submit, nếu quá deadline
  → finalize phiên + ném `SESSION_TIME_UP`, chặn submit muộn lọt vào.
- **Reaper nền** (`SessionDeadlineReaper` → `expireIfOverBudget`): quét định kỳ,
  khoá hàng, flip phiên quá hạn sang `COMPLETED` + mở gate chấm tổng. Chạy
  transaction riêng từng phiên để một phiên kẹt không chặn cả lượt quét.
- Cả hai đi chung đường `applyTimeBudgetExpiry`: chỉ tác động phiên đang
  `IN_PROGRESS`.

---

## 10. Ví dụ end-to-end

### 10.1 BEHAVIORAL — `BACKEND-mid-BEHAVIORAL` (seed thật)

Blueprint: 5 topic, budget 6 câu, follow-up 2/topic & 3/phiên, 30 phút.
```
OWNERSHIP(MED,EASY,1) · CONFLICT_RESOLUTION(HIGH,MED,2) · COMMUNICATION(HIGH,MED,3)
· LEADERSHIP(MED,MED,4) · FAILURE(MED,MED,5)
```
User: senior CV nhưng mới 2 năm thực tế → `globalOffset = -1`.

```
/start:
  pickFirstTopic → OWNERSHIP (orderHint 1, lại là COMPETENCY)
  openingDifficulty = EASY.downgrade()=EASY, offset<0 → EASY (sàn)
  → Q1: BEHAVIORAL OWNERSHIP EASY (opener)  | OWNERSHIP: PROBING

Q1 chấm: score 6.5, MODERATE depth, có weak-target MED
  → Case C: còn quota, có dấu hiệu nông + weak-target → AskFollowUp
  → Q2: follow-up OWNERSHIP (Tier1 bank hoặc Tier2 AI)  | followUpsUsed=1

Q2 chấm: score 7.5, STRONG, hireSignal yes
  → Case D: đóng OWNERSHIP=STRONG, runningStrong=1
  → pickNextTopic: importance DESC → CONFLICT_RESOLUTION (HIGH, orderHint2)
  → Q3: CONFLICT_RESOLUTION, độ khó = MED + offset(-1) → EASY

Q3 chấm: score 2.0, WRONG
  → Case A1: đóng CONFLICT_RESOLUTION=WEAK, câu kế hạ nấc
  → pickNextTopic → COMMUNICATION (HIGH, orderHint3)
  → Q4: COMMUNICATION ...

... tiếp tục tới khi pin đủ 6 câu (budget) HOẶC phủ hết 5 topic.
Giả sử chạm budget ở Q6 → EndSession(QUESTION_BUDGET_EXHAUSTED) → COMPLETED.
  → mọi answer SCORED → overall-review → SCORED + email.
```

### 10.2 CODING — `BACKEND-mid-CODING` (seed thật)

Blueprint: 4 slot ramp `EASY → MEDIUM → MEDIUM → HARD`, budget 4, follow-up 0/0,
110 phút.

```
/start:
  difficultyPlan = [EASY, MEDIUM, MEDIUM, HARD]
  selectCodingPlan: bốc 4 bài LIVE_CODING distinct, mỗi bài có functionMeta+testCases
  metadata.codingPlan = [4 entries] ; pin bài 1 (EASY)
  → trả bài 1

submit bài 1 → judge chạy test ; ĐỒNG THỜI pin ngay bài 2 (MEDIUM)
submit bài 2 → judge ; pin bài 3 (MEDIUM)
submit bài 3 → judge ; pin bài 4 (HARD)
submit bài 4 → judge ; pinNextCodingIfAny thấy hết plan → không pin

mỗi bài: judge runnable → AI live_coding eval → SCORED
         judge CE/RE → SCORED ngay (bỏ AI)
khi answer bài cuối SCORED (hoặc /finish / hết 110') → overall-review → SCORED.
```

---

## 11. Bảng tham số các blueprint seed

(Tất cả `is_default = TRUE`, `use_ai_selector = FALSE`.)

### BEHAVIORAL / CORE (V3)

| role · level · type | #topic | budget | fu/topic | fu/phiên | phút |
|---|---|---|---|---|---|
| BACKEND · junior · BEHAVIORAL | 4 | 5 | 1 | 2 | 25 |
| BACKEND · mid · BEHAVIORAL | 5 | 6 | 2 | 3 | 30 |
| FRONTEND · mid · BEHAVIORAL | 5 | 6 | 2 | 3 | 30 |
| FULLSTACK · mid · BEHAVIORAL | 5 | 6 | 2 | 3 | 30 |
| BACKEND · junior · CORE | 4 | 5 | 1 | 2 | 25 |
| BACKEND · mid · CORE | 6 | 7 | 2 | 4 | 40 |
| FRONTEND · mid · CORE | 6 | 7 | 2 | 4 | 40 |
| FULLSTACK · mid · CORE | 6 | 7 | 2 | 4 | 40 |

### CODING (V5)

| role · level · type | #slot (=budget) | ramp độ khó | phút |
|---|---|---|---|
| BACKEND · junior · CODING | 3 | EASY · EASY · MEDIUM | 75 |
| BACKEND · mid · CODING | 4 | EASY · MEDIUM · MEDIUM · HARD | 110 |
| FRONTEND · mid · CODING | 4 | EASY · MEDIUM · MEDIUM · HARD | 110 |
| FULLSTACK · mid · CODING | 4 | EASY · MEDIUM · MEDIUM · HARD | 110 |

> Mẫu thiết kế xuyên suốt: budget tăng + thời lượng tăng theo level; junior khởi
> động EASY nhiều hơn; mid có topic HIGH ở đầu để đo tín hiệu quan trọng sớm.

---

## 12. Cạm bẫy & quy tắc bất biến

1. **Khoá blueprint phải dùng token chuẩn.** `targetRole`/`level` lưu dạng đã
   normalize (`BACKEND`, `senior`). Nếu admin tạo blueprint bằng chuỗi tự do
   không khớp `BlueprintNormalizer`, `/start` sẽ `BLUEPRINT_NOT_FOUND`. Đây là
   lý do FE nên cho chọn từ dropdown token (xem `frontend/docs/blueprint-admin-design.md`).

2. **`topicValue` phải khớp byte-for-byte enum question-bank.** Sai chính tả →
   `/filter` không match → rơi vào relax → có thể lấy câu lệch topic hoặc
   `QUESTION_BANK_UNAVAILABLE`.

3. **CODING không dùng `kind`/`topicValue`** trong seed, nhưng API admin có
   `@Valid` bắt buộc 2 field này → FE phải bơm placeholder
   (`kind=DOMAIN, topicValue="CODING"`); picker CODING bỏ qua nên vô hại.

4. **Câu coding không gắn topic** → planner bị **skip** cho answer coding
   (`sq.topicKind == null`). Đừng kỳ vọng EndSession do planner ở CODING — phiên
   coding kết thúc qua submit bài cuối / `/finish` / hết giờ.

5. **`question_budget` thắng coverage** trong điều kiện dừng adaptive — chặn loop
   khi blueprint cấu hình budget < số topic có thể sinh câu.

6. **Mỗi bộ ba chỉ 1 default.** Set default mới → service tự `clearOtherDefaults`
   (gỡ cờ các bản anh em) trước khi set bản hiện tại. Trùng default = `4096`.

7. **Xoá blueprint còn session tham chiếu bị chặn** (`4097
   BLUEPRINT_IN_USE`) — vì session giữ `blueprintId` để planner tra trần suốt
   phiên.

8. **`use_ai_selector`** hiện được lưu trên blueprint nhưng **chưa thay đổi**
   đường chọn topic adaptive trong code đã đọc (planner luôn dùng quy tắc
   importance/orderHint xác định). Coi đây là cờ dành cho tương lai; đừng giả
   định nó bật một nhánh AI selector khác.

9. **Snapshot đóng băng là nguồn sự thật của phiên.** Sau khi pin, nội dung câu
   hỏi sống trong `session_question.snapshot`, độc lập với question-bank. Sửa
   bank không hồi tố phiên cũ.

10. **`MIXED` đã chết.** Enum `interviewType` chỉ còn 3 giá trị. Seed V2/V3 còn
    chữ MIXED là rác lịch sử — không tạo blueprint MIXED mới.


Verdict ngắn gọn

Tỷ lệ câu/thời gian trong từng blueprint là hợp lý cho bản v1, nhưng thiết kế chưa "chuẩn production" vì 4 vấn đề nghiệp vụ thực sự — trong đó 1 cái nghiêm trọng (nhiều user sẽ không bắt đầu được phỏng vấn).

  ---
❌ Vấn đề 1 — Độ phủ ma trận (role, level) quá thưa → user bị chặn (NGHIÊM TRỌNG)

Kết quả audit seed thực tế:

┌───────────────────────┬────────┬─────┬────────┐
│                       │ junior │ mid │ senior │
├───────────────────────┼────────┼─────┼────────┤
│ BACKEND           │ ✅ ││✅❌─    │ ✅│  │ ❌      │
├───────────────────────┼────────┼─────┼────────┤
│ FRONTEND          │ ✅ ││❌❌     │ ✅   │ ❌      │
├───────────────────────┼────────┼─────┼────────┤
│ FULLSTACK         │ ✅ ││❌❌     │ ✅   │ ❌      │
├───────────────────────┼────────┼─────┼────────┤
│ (MOBILE/DATA/DEVOPS…) │ ❌      │ ❌   │ ❌      │
└───────────────────────┴────────┴─────┴────────┘

- Không có một blueprint senior nào. Mọi ứng viên normalize ra level=senior (kể cả "Lead"/"Staff") → findFor rỗng → BLUEPRINT_NOT_FOUND (4042), không phỏng vấn được.
- FRONTEND/FULLSTACK không có junior; chỉ BACKEND đủ junior+mid.
- Đây không phải lỗi logic blueprint, mà là lỗ hổng dữ liệu — nhưng với người dùng thì hậu quả như nhau: bấm Start là lỗi. Đây là lý do chính khiến tôi nói "chưa chuẩn".

▎ Khuyến nghị: hoặc seed đủ ma trận (role × level × 3 type), hoặc thêm cơ chế fallback trong BlueprintLoader.findFor (vd thiếu senior thì lùi về mid cùng role, hoặc một blueprint "GENERIC" mặc định) để không bao giờ trả 404 cho user hợp lệ.

  ---
⚠️ Vấn đề 2 — Đồng hồ phiên tính cả độ trễ xử lý vào thời gian của ứng viên (adaptive)

deadline = startedAt + timeBudgetMinutes là wall-clock cố định. Nhưng luồng BEHAVIORAL/CORE là bất đồng bộ: submit → STT → AI đánh giá → mới hiện câu kế. Suốt khoảng chờ đó (vài chục giây đến >1 phút mỗi câu) đồng hồ vẫn chạy, và ứng viên chỉ ngồi đợi.

- BEHAVIORAL mid: 30 phút / 6 câu ≈ 5 phút/câu để vừa suy nghĩ vừa nói. Nếu mỗi lượt mất 45–90s chờ pipeline → mất 5–9 phút "oan" trên tổng 30 phút (15–30%).
- Tức thời gian thực sự để trả lời nhỏ hơn ngân sách ghi trên blueprint. Về business, đây là vấn đề công bằng: con số "30 phút" không phản ánh 30 phút làm bài.

▎ Khuyến nghị: hoặc "pause clock" trong lúc xử lý (chỉ tính thời gian từ khi câu hiện ra đến khi submit), hoặc tăng timeBudgetMinutes để bù latency, hoặc nói rõ với ứng viên. CODING không dính lỗi này vì nó pin câu kế ngay khi submit (không chờ judge) — thiết kế đó đúng.

  ---
⚠️ Vấn đề 3 — Không có giới hạn thời gian theo từng câu/bài (rủi ro nhất ở CODING)

Chỉ có một đồng hồ cấp phiên, không phân bổ cho từng câu.

- CODING mid: 110 phút / 4 bài (EASY·MED·MED·HARD) = 27.5 phút/bài. Ứng viên hoàn toàn có thể đốt 70 phút vào bài 1 rồi hết giờ ở bài 3–4 → coverage hỏng mà hệ thống không cảnh báo/điều phối.
- Một bài HARD đáng được nhiều thời gian hơn EASY, nhưng ngân sách phẳng không thể hiện điều đó.

▎ Khuyến nghị: cân nhắc soft-limit/gợi ý thời gian mỗi bài (hiển thị "bài này ~25 phút"), hoặc phân bổ ngân sách theo độ khó. Tối thiểu nên show đồng hồ + cảnh báo để ứng viên tự điều phối.

  ---
⚠️ Vấn đề 4 — question_budget gồm cả follow-up → có thể bỏ sót topic HIGH một cách âm thầm

question_budget là trần tổng (câu chính + follow-up). Ví dụ BEHAVIORAL mid: 5 topic, budget 6, follow-up tối đa 3/phiên.

- Trường hợp xấu: topic1 (+2 follow-up) + topic2 (+1 follow-up) + topic3 = đã 6 câu → chạm budget → topic 4, 5 không bao giờ được hỏi, dù đó là các topic HIGH-importance.
- Planner ưu tiên importance-DESC nên topic quan trọng được phủ trước (tốt), nhưng việc một bài đánh giá "đo communication/leadership" rồi lặng lẽ bỏ qua một số năng lực là vấn đề validity của kết quả.

▎ Đây là đánh đổi chiều sâu vs độ phủ — cần là quyết định business có chủ đích, không nên để ngẫu nhiên. Nếu muốn đảm bảo phủ hết topic HIGH, nên tách "budget câu chính" và "budget follow-up" riêng, hoặc đặt questionBudget ≥ #topic + maxFollowUpsPerSession.

  ---
✅ Những phần đã hợp lý (giữ nguyên)

- Tỷ lệ tăng dần theo level: budget + thời lượng tăng từ junior→mid; junior nhiều EASY hơn; mid đẩy topic HIGH lên đầu — đúng tư duy thiết kế phỏng vấn.
- CODING pin câu kế ngay khi submit — tránh thuế latency, đúng.
- Spam-guard CE/RE bỏ qua AI — tiết kiệm chi phí hợp lý.
- question_budget thắng coverage để chặn loop — phòng thủ tốt.
- Hiệu chỉnh độ khó theo kinh nghiệm + stretch mode — cơ chế adaptive lành mạnh, dùng phong độ thực thay vì tin số năm trên CV.
- Cấu trúc topic của các seed (warm-up → topic quan trọng) hợp lý về mặt nội dung.

  ---
Ưu tiên xử lý (nếu hỏi tôi nên làm gì trước)

1. Lấp ma trận blueprint / thêm fallback 404 — chặn người dùng không vào được phỏng vấn (P0).
2. Xử lý latency với đồng hồ adaptive — công bằng thời gian (P1).
3. Làm rõ semantics budget gồm follow-up + đảm bảo phủ topic HIGH (P1).
4. Gợi ý/giới hạn thời gian mỗi bài CODING (P2).

Bạn muốn tôi đi sâu phương án kỹ thuật cho mục nào, hay seed bổ sung các blueprint còn thiếu (đặc biệt senior và các role/junior chưa có)?
</content>
</invoke>
