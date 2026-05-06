# Question Selection — Design Document

Phần thiết kế **logic chọn câu hỏi** trong 1 buổi phỏng vấn. Đây là tài liệu bổ sung cho [interview-service-design.md](./interview-service-design.md), tập trung trả lời:

- Câu đầu tiên cho user lấy từ đâu, dựa vào tiêu chí nào.
- Sau khi AI chấm câu trước, chọn câu kế thế nào trong 4 tình huống: trả lời tệ / không trả lời được / chưa đầy đủ / trả lời tốt.
- Trách nhiệm phân chia giữa `interview-service`, `AI service` và `question-bank`.

> Doc này dùng đúng terminology của codebase hiện tại: `competency` (cho behavioral), `domain` (cho core conceptual), `difficulty ∈ {EASY, MEDIUM, HARD}`. Không đưa ra khái niệm "skill" mới.

---

## Mục lục

- [1. Mental model — Topic Matrix + Evidence Level](#1-mental-model--topic-matrix--evidence-level)
- [2. Interview Blueprint](#2-interview-blueprint)
- [3. Câu hỏi đầu tiên](#3-câu-hỏi-đầu-tiên)
- [4. AI Assessment contract](#4-ai-assessment-contract)
- [5. Decision tree — chọn câu kế](#5-decision-tree--chọn-câu-kế)
  - [5.1 Case A — Trả lời quá tệ](#51-case-a--trả-lời-quá-tệ)
  - [5.2 Case B — Không trả lời được](#52-case-b--không-trả-lời-được)
  - [5.3 Case C — Chưa đầy đủ (FOLLOW-UP)](#53-case-c--chưa-đầy-đủ-follow-up)
  - [5.4 Case D — Trả lời tốt](#54-case-d--trả-lời-tốt)
- [6. Chọn topic kế tiếp](#6-chọn-topic-kế-tiếp)
- [7. Termination — kết thúc session](#7-termination--kết-thúc-session)
- [8. Phân chia trách nhiệm](#8-phân-chia-trách-nhiệm)
- [9. Database schema bổ sung](#9-database-schema-bổ-sung)
- [10. API contract bổ sung](#10-api-contract-bổ-sung)

---

## 1. Mental model — Topic Matrix + Evidence Level

Một interview thật **không** là "hỏi N câu rồi tính trung bình". Interviewer giỏi luôn nghĩ theo 2 trục:

- **Trục coverage (ngang)**: cần xác minh user có những năng lực gì — danh sách `competency` (behavioral) hoặc `domain` (core) cần test. Đây là *Topic Blueprint*.
- **Trục evidence (dọc)**: với mỗi topic, đã đủ bằng chứng (evidence) chưa? Nếu chưa → đào sâu (follow-up). Nếu đủ → đóng topic, chuyển topic khác.

→ Hệ thống **track trạng thái từng topic** trong session. Đây là core data structure (`session_topic_state`).

```
SessionTopicState
  session_id
  topic_kind        // COMPETENCY | DOMAIN
  topic_value       // vd. "CONFLICT_RESOLUTION" hoặc "DATABASE"
  status            // NOT_TESTED | PROBING | STRONG | ADEQUATE | PARTIAL | WEAK | UNKNOWN
  questions_asked   // tổng câu đã hỏi cho topic này
  follow_ups_used   // 0..MAX_FOLLOW_UPS_PER_TOPIC
  last_difficulty   // EASY | MEDIUM | HARD
  last_assessment   // snapshot kết quả câu cuối (để tham chiếu khi chọn câu kế)
  closed_at
```

Mọi quyết định "câu kế là gì" xuất phát từ matrix này, **không** chỉ từ score câu vừa rồi. Score là 1 input, không phải input duy nhất.

### Ý nghĩa của các status

| Status | Ý nghĩa | Tính vào điểm cuối? |
|---|---|---|
| `NOT_TESTED` | Chưa hỏi câu nào về topic này | Không |
| `PROBING` | Đang test, chưa có kết luận | Không |
| `STRONG` | Đã chứng minh thành thạo (score ≥ 7, signals STRONG) | Có (trọng số đầy đủ) |
| `ADEQUATE` | Đủ kiến thức cơ bản, chưa xuất sắc | Có |
| `PARTIAL` | Có hiểu nhưng nhiều lỗ hổng, đã probe hết quota | Có (trọng số giảm) |
| `WEAK` | Sai concept gốc / fail nhiều lần | Có (trọng số giảm, làm xấu điểm) |
| `UNKNOWN` | User trả lời "không biết" / im lặng — không có evidence | **Không tính** (báo cáo "chưa assess") |

Phân biệt `WEAK` và `UNKNOWN` rất quan trọng: user thật thà nói "không biết" khác user trả lời sai — không nên penalize giống nhau.

---

## 2. Interview Blueprint

### 2.0 Personalization sources từ `user-profile-service`

`user-profile-service` lưu các trường sau (`UserProfile` entity). Interview-service gọi `GET /api/v1/profiles/{userId}` lúc `/interviews/start` và derive blueprint key + filter constraints từ đây — **không** bắt user nhập lại:

| Profile field | Type | Dùng vào đâu trong selection |
|---|---|---|
| `position.track.name` | String | Map → `target_role` (vd. `Backend` → `BACKEND`) — filter `core_questions.target_roles[]` |
| `position.level.positionRole` | String | Map → blueprint key `level` (`Junior`/`Mid`/`Senior` → `junior`/`mid`/`senior`) |
| `experience` | Integer (years) | Cross-check với `level` để hiệu chỉnh `global_difficulty_offset` (xem dưới) |
| `yearsInCurrentRole` | Integer, nullable | Khi user vừa chuyển role (vd. BE 8 năm, mới chuyển FE 6 tháng): dùng `min(experience, yearsInCurrentRole + 1)` làm baseline level — tránh blueprint senior khi user thực ra đang junior ở track mới |
| `techStack` | List\<String\> normalized lowercase (vd. `["java","spring","postgresql"]`) | (a) Coding question: ưu tiên câu có starter cho ngôn ngữ user dùng. (b) Core question: bias selection trong cùng domain — match `tags_any` với techStack tokens |
| `preferredLanguage` | enum `VI`/`EN`, default `VI` | (a) Audio TTS: chọn `audio_key` ngôn ngữ tương ứng (cần question-bank multi-lingual audio sau này; hiện default VI). (b) AI feedback language: truyền vào `evaluation-requested` payload |
| `industries` | List\<String\> normalized (vd. `["fintech","ecommerce"]`) | Bias **behavioral question** selection — câu match `tags_any` chứa industry token được boost. Nếu pool trống thì ignore (không hard filter, vì có thể loại quá nhiều câu) |

#### Cross-check `experience` vs `level` → `global_difficulty_offset`

```
expected_min_years = { junior: 0, mid: 2, senior: 5 }
effective_years   = (yearsInCurrentRole != null) ? min(experience, yearsInCurrentRole + 1) : experience
diff              = effective_years - expected_min_years[level]

IF diff < -1:                          # vd. tự nhận senior nhưng effective_years=1
    session.global_difficulty_offset = -1   # toàn bộ câu giảm 1 nấc preemptive
ELIF diff > 3:                         # vd. mid 6 năm
    session.stretch_after = 2          # bật stretch sớm hơn (sau 2 STRONG, không phải 3)
```

Logic này **deterministic**, đặt trong `BlueprintLoader` của interview-service, không cần AI.

#### Profile fields KHÔNG dùng

- `fullName`, `city`, `avatar_object_key` — chỉ display, không ảnh hưởng selection.
- Self-reported "weak areas": **không có** trong profile (cố ý). Đánh giá cá nhân hoá long-term lấy từ **interview history** (`UserSkillProfile` derived view trong interview-service), không từ user tự khai.

### 2.1 Blueprint structure

Là catalog **admin-managed** quy định: với 1 buổi phỏng vấn `(role, level, type)`, cần test những topic nào, mỗi topic ở difficulty bao nhiêu, importance cao thấp ra sao.

```
InterviewBlueprint
  id
  target_role         // BACKEND | FRONTEND | FULLSTACK | MOBILE | ...
  level               // junior | mid | senior
  interview_type      // BEHAVIORAL | CORE | MIXED
  topics: [
    {
      kind                // COMPETENCY | DOMAIN
      value               // vd. "CONFLICT_RESOLUTION" hoặc "DATABASE"
      importance          // HIGH | MED | LOW
      target_difficulty   // EASY | MEDIUM | HARD
      order_hint          // int — ưu tiên thứ tự
    }
  ]
  question_budget                  // tổng câu tối đa (kể cả follow-up), vd. 8
  max_follow_ups_per_topic         // vd. 2
  max_follow_ups_per_session       // vd. 4
  time_budget_minutes              // vd. 45
```

### Ví dụ blueprint `backend-mid-MIXED`

| order | kind | value | importance | target_difficulty |
|---|---|---|---|---|
| 1 | COMPETENCY | OWNERSHIP | MED | EASY |
| 2 | COMPETENCY | CONFLICT_RESOLUTION | HIGH | MEDIUM |
| 3 | DOMAIN | DATABASE | HIGH | MEDIUM |
| 4 | DOMAIN | SYSTEM_DESIGN | HIGH | MEDIUM |
| 5 | DOMAIN | CACHING | MED | MEDIUM |
| 6 | DOMAIN | NETWORKING | LOW | EASY |

Khi `/interviews/start`: load blueprint theo `(role, level, type)`, copy mỗi topic vào `session_topic_state` với `status = NOT_TESTED`.

> **Tại sao tách blueprint khỏi question-bank?** Một topic (vd. `DATABASE`) có hàng trăm câu hỏi ở các difficulty khác nhau. Blueprint chỉ nói "cần test DATABASE ở MEDIUM", còn việc chọn câu cụ thể là quyết định runtime. Blueprint thay đổi chậm (admin), pool câu hỏi thay đổi nhanh (TTS sync, status update) — tách ra để 2 lifecycle độc lập.

---

## 3. Câu hỏi đầu tiên

### Nguyên tắc

- **Deterministic**, không gọi AI. Chưa có signal nào để AI thông minh hơn rule.
- **Dễ hơn target 1 nấc** (EASY nếu target = MEDIUM). Mục đích: warm-up + lấy baseline. Nếu target = EASY → giữ EASY.
- **Open-ended**. Câu mở để AI có nhiều signal phân tích.

### Thuật toán

```
INPUT: session_start_request + user_profile (đã fetch từ user-profile-service)

0. Derive từ profile (xem mục 2.0):
     target_role = map(profile.position.track.name)
     level       = map(profile.position.level.positionRole)
     tech_stack  = profile.techStack            // có thể rỗng
     industries  = profile.industries           // có thể rỗng
     language    = profile.preferredLanguage    // VI | EN, default VI
     session.global_difficulty_offset = cross_check(experience, yearsInCurrentRole, level)

1. blueprint = load(target_role, level, interview_type)
2. session_topic_state[] = init từ blueprint.topics, all NOT_TESTED
3. first_topic = blueprint.topics sorted by (order_hint ASC) → pick[0]
   - Nếu type = MIXED hoặc BEHAVIORAL → ưu tiên topic.kind = COMPETENCY
     và competency mở (vd. OWNERSHIP, không phải FAILURE)
   - Nếu type = CORE → topic.kind = DOMAIN, importance = HIGH
4. opening_difficulty = apply(global_difficulty_offset,
                              max(EASY, downgrade(first_topic.target_difficulty)))
5. candidate_pool = question-bank/filter:
     {
       type:                 BEHAVIORAL nếu first_topic.kind = COMPETENCY else CORE_CONCEPTUAL,
       competency:           first_topic.value (nếu BEHAVIORAL),
       domain:               first_topic.value (nếu CORE),
       difficulty:           opening_difficulty,
       status:               ACTIVE,
       target_role:          target_role,
       tags_any_boost:       tech_stack ∪ industries  // soft bias, không hard filter
                                                       // (xem 5.1 dưới)
       exclude_ids:          questions user đã được hỏi và pass STRONG trong 30 ngày,
       require_opener:       true   // tag "opener" mục 9
     }
6. Trong candidate_pool, scoring:
   pool_score(q) = base
                 + (q.is_opener ? +10 : 0)
                 + (overlap(q.tags, tech_stack) > 0 ? +3 : 0)   // BEHAVIORAL: bias industry
                 + (overlap(q.tags, industries) > 0 ? +3 : 0)   // CORE: bias tech
                 - (q.ask_count / 100)                          // ưu tiên câu ít hỏi
   pick câu có pool_score cao nhất.
   Nếu pool rỗng → relax theo thứ tự:
     a. bỏ require_opener
     b. mở rộng difficulty range ±1
     c. bỏ target_role (chấp nhận câu generic)
7. INSERT session_question (sequence=1, question_id, topic, difficulty)
8. UPDATE session_topic_state[first_topic].status = PROBING, questions_asked = 1
9. RETURN câu hỏi cho FE:
     - audio_url đã sign từ storage (nếu có audio_key match `language`)
     - text gốc từ question-bank
     - language hint cho FE biết render UI
```

> **Soft bias không hard filter**: `tech_stack` và `industries` không strict — nếu hard filter sẽ loại quá nhiều câu (đặc biệt khi industry hiếm như `["healthcare"]`). Dùng để **boost** điểm trong scoring, không loại trừ.

### Vì sao không hỏi AI cho câu đầu

- Không có lịch sử → AI selector chỉ rơi vào fallback "first_turn" (xem `AI/selector/retrieval/selector.py`).
- Tốn 1 lần gọi LLM + embedding cho thứ rule có thể quyết deterministic.
- Câu đầu cần **nhanh** — user vừa start session, latency cao gây bad UX.

---

## 4. AI Assessment contract

Đây là phần **quyết định 80% chất lượng hệ thống**. AI không chỉ trả `score + feedback` mà phải trả **structured signals** để decision tree dùng.

### AI hiện tại đã trả gì (`AI/models/outputs.py`)

| Field | BehavioralOutput | ConceptualOutput | LiveCodingOutput |
|---|---|---|---|
| `overall_score` | ✅ 0-100 | ✅ 0-100 | ✅ 0-100 |
| `scores[]` (sub-rubric) | ✅ 5 dim | ✅ 4 dim | ✅ 4 dim |
| Coverage signals | `signal_coverage[]` (detected/evidence) | `concept_coverage[]` (mentioned/correct/correction) | (n/a) |
| Issues | `red_flags[]` (type/severity) | `misconceptions[]` (claim/correction) | `code_issues[]` |
| Calibration | (gián tiếp qua scores) | `level_calibration` (expected vs actual, gap) | `analysis.detected_complexity` vs `optimal` |
| Summary | `summary.grade` (A-F), `summary.hire_signal` | same | same |

→ **Đã đủ phong phú**. Không cần ép AI thêm trường mới — chỉ cần **interview-service biết MAP** từ schema này sang `AssessmentVerdict` thống nhất sau đây.

### `AssessmentVerdict` — interview-service derive sau khi nhận `evaluation-completed`

Để decision tree không phải switch theo type, interview-service tổng hợp output AI thành verdict thống nhất:

```
AssessmentVerdict {
  answer_id
  topic_kind             // COMPETENCY | DOMAIN
  topic_value            // ref vào session_topic_state
  score_normalized       // 0.0 - 10.0 (chuẩn hoá từ overall_score/100 * 10)
  hire_signal            // strong_yes | yes | weak_yes | no | strong_no
  grade                  // A | B | C | D | F

  -- Derived signals dùng trong decision tree
  signal_strength        // NONE | PARTIAL | ADEQUATE | STRONG
                         // BEHAVIORAL: từ tỉ lệ signal_coverage.detected = true
                         // CORE: từ tỉ lệ concept_coverage.mentioned ∧ correct = true
  completeness           // NO_ANSWER | INCOMPLETE | COMPLETE
                         // Suy ra từ transcript word_count + AI flag (nếu có)
  correctness            // WRONG | MIXED | CORRECT
                         // CORE: dựa vào tỉ lệ concept_coverage.correct và misconceptions
                         // BEHAVIORAL: thường là MIXED/CORRECT (behavioral hiếm khi WRONG)
  depth                  // SURFACE | MODERATE | DEEP
                         // CORE: từ level_calibration.gap
                         // BEHAVIORAL: từ specificity score và star_breakdown

  -- Probe targets cho follow-up (CỰC QUAN TRỌNG)
  weak_targets: [
    { kind, value, severity }    // vd. {kind:"signal", value:"specific_recent_example", severity:"HIGH"}
                                 //     {kind:"concept", value:"composite_index_order", severity:"HIGH"}
                                 //     {kind:"misconception", value:"...", severity:"HIGH"}
  ]
  strong_targets: [
    { kind, value }
  ]
}
```

`weak_targets` được derive từ:
- BEHAVIORAL: `signal_coverage` có `detected = false` → 1 weak_target với `kind=signal`. `red_flags` có `severity ∈ {high, critical}` → 1 weak_target với `kind=red_flag`.
- CORE: `concept_coverage` có `mentioned = false` HOẶC `correct = false` → weak_target `kind=concept`. `misconceptions` → weak_target `kind=misconception`.

Đây là **logic ánh xạ trong interview-service**, không phải contract mới ép xuống AI service. AI giữ nguyên schema hiện tại.

---

## 5. Decision tree — chọn câu kế

Sau khi `evaluation-completed` về và `AssessmentVerdict` được derive, chạy decision tree này. Tất cả case đều update `session_topic_state[current_topic]` *trước* khi quyết.

### 5.1 Case A — Trả lời quá tệ

**Trigger**: `score_normalized < 3.0` HOẶC `hire_signal ∈ {no, strong_no}`, **VÀ** `completeness ≠ NO_ANSWER` (nếu NO_ANSWER → Case B).

Phân biệt 2 sub-case từ `correctness`:

#### A1. Sai concept gốc (`correctness = WRONG`)

User chưa nắm cơ bản → đào sâu cùng topic là vô nghĩa.

```
session_topic_state[current].status = WEAK
session_topic_state[current].closed_at = now()
→ MOVE NEXT TOPIC (xem mục 6)
   với difficulty_adjustment = -1 (giảm 1 nấc cho topic kế: MEDIUM → EASY)
```

#### A2. Có ý đúng nhưng quá nông (`correctness = MIXED`, `depth = SURFACE`)

User biết một chút. Cho **đúng 1 lần** second-chance ở câu dễ hơn cùng topic.

```
IF session_topic_state[current].follow_ups_used < 1:
    session_topic_state[current].follow_ups_used += 1
    new_difficulty = downgrade(last_difficulty)   // MEDIUM → EASY
    → SAME TOPIC, new question từ pool {topic, difficulty=new_difficulty}
ELSE:
    session_topic_state[current].status = WEAK
    → MOVE NEXT TOPIC (difficulty_adjustment = -1)
```

> **Nguyên tắc chống "đào hố"**: với 1 topic, tối đa **1 lần** xuống thang khó. Vẫn fail → kết luận `WEAK`, move on. Không hành hạ user trên topic họ không biết.

### 5.2 Case B — Không trả lời được

**Trigger**: `completeness = NO_ANSWER` HOẶC transcript `word_count < 5` (cấu hình `TRANSCRIPT_MIN_WORD_COUNT`) HOẶC user gửi text "tôi không biết" / "skip" / "next" (FE detect rồi gửi flag `gaveUp=true`).

Đây là tín hiệu **honest**, đối xử khác với "trả lời tệ":

```
session_topic_state[current].status = UNKNOWN
session_topic_state[current].closed_at = now()
session.consecutive_unknown_count += 1

IF session.consecutive_unknown_count >= 3:
    # Level mismatch nghiêm trọng → giảm tải toàn session
    session.global_difficulty_offset = -1   // toàn bộ câu sau đều giảm 1 nấc
    Hiển thị banner cho user: "Có vẻ level này hơi cao. Hệ thống sẽ giảm độ khó cho các câu còn lại."

→ MOVE NEXT TOPIC (KHÔNG hỏi lại topic này, KHÔNG hỏi dễ hơn cùng topic)
```

Trong feedback của câu hiện tại (hiển thị cuối session): include `study_resources` từ `topic_catalog` (vd. link tài liệu cho `DATABASE`) — biến thất bại thành learning moment.

> **Vì sao không hỏi dễ hơn cùng topic?** User đã thừa nhận không biết — hỏi câu dễ hơn vẫn không biết, chỉ làm mất thời gian và nhục. Move on, mark UNKNOWN, báo cáo cuối nói rõ "topic này chưa được đánh giá".

### 5.3 Case C — Chưa đầy đủ (FOLLOW-UP)

**Trigger**: `completeness = INCOMPLETE` HOẶC `depth ∈ {SURFACE, MODERATE}` HOẶC `weak_targets` có item `severity = HIGH`. Đây là case **quan trọng nhất** — quyết định hệ thống có "smart" hay không.

#### C.1 Quyết có follow-up hay không

```
IF session_topic_state[current].follow_ups_used >= blueprint.max_follow_ups_per_topic:
    # Đã đủ cơ hội cho topic này
    new_status = ADEQUATE if score_normalized >= 6.0 else PARTIAL
    session_topic_state[current].status = new_status
    → MOVE NEXT TOPIC

ELSE IF session.total_follow_ups_used >= blueprint.max_follow_ups_per_session:
    # Cap toàn session đã chạm
    session_topic_state[current].status = PARTIAL
    → MOVE NEXT TOPIC

ELSE IF weak_targets is empty:
    # AI không xác định được lỗ hổng cụ thể → không có gì để probe
    session_topic_state[current].status = ADEQUATE
    → MOVE NEXT TOPIC

ELSE:
    → ENTER FOLLOW-UP FLOW (C.2)
```

#### C.2 Tạo follow-up question — 2-tier

**Tier 1 — Pre-authored follow-up** (ưu tiên):

Nếu question hiện tại có `follow_up_questions[]` được tag theo subtopic (cần thêm bảng `question_follow_up` ở question-bank, mục 9):

```
target = weak_targets[0]   // severity HIGH nhất
matched = SELECT * FROM question_follow_up
          WHERE parent_question_id = current.question_id
            AND probes_target_kind = target.kind
            AND probes_target_value = target.value
          LIMIT 1

IF matched:
    persist session_question với:
      parent_question_id = current.question_id
      is_follow_up = true
      source = 'PRE_AUTHORED'
    → SEND TO FE
    SKIP TIER 2
```

**Tier 2 — AI-generated follow-up** (fallback):

Không có pre-authored match → gọi AI sync. AI service cần endpoint mới `POST /follow-up/generate` (xem mục 8 và 10):

```
request = {
  parent_question: { text, type, competency|domain, expected_signals|key_concepts },
  user_answer_transcript: ...,
  weak_target: { kind, value, severity },
  strong_targets: [...],   // tránh hỏi lại thứ user đã làm tốt
  difficulty: same as parent
}

response = POST AI:/follow-up/generate
  → { question_text, expected_points: [...], rationale, source: "AI_GENERATED" }

persist session_question với:
  parent_question_id = current.question_id
  is_follow_up = true
  source = 'AI_GENERATED'
  question_text inline (KHÔNG ghi vào question-bank — đây là one-off)
  expected_points inline
→ SEND TO FE
```

`session_topic_state[current].follow_ups_used += 1`. Topic vẫn `PROBING`, *không* đóng cho đến khi follow-up được trả lời.

#### C.3 Sau khi follow-up trả lời xong

AI assess follow-up bình thường (gửi kèm `parent_question_context` để AI hiểu đây là follow-up, không phải câu độc lập). Quay lại đầu decision tree với câu follow-up:

- Nếu follow-up tốt → topic = `ADEQUATE`, move on.
- Nếu vẫn yếu, còn quota → lặp C.1 (có thể có follow-up của follow-up — nhưng count vào cùng `follow_ups_used`).

#### Giới hạn cứng

| Cap | Default | Lý do |
|---|---|---|
| `max_follow_ups_per_topic` | 2 | Quá nhiều → user bực, mất thời gian topic khác |
| `max_follow_ups_per_session` | 4 | Tránh session toàn follow-up, lệch coverage |

### 5.4 Case D — Trả lời tốt

**Trigger**: `score_normalized >= 7.0` VÀ `signal_strength = STRONG` VÀ `hire_signal ∈ {yes, strong_yes}`.

```
session_topic_state[current].status = STRONG
session_topic_state[current].closed_at = now()
session.running_strong_count += 1
→ MOVE NEXT TOPIC
```

#### Stretch mode

Nếu user xuất sắc liên tục, blueprint mid-level không phân biệt được senior thật:

```
IF session.running_strong_count >= 3 AND ALL recent topics scored at target_difficulty:
    session.stretch_mode = TRUE
    Cho topic kế: difficulty = upgrade(target_difficulty)   // MEDIUM → HARD
```

Mục đích: tìm trần (ceiling). Báo cáo cuối session ghi rõ "user vượt level".

---

## 6. Chọn topic kế tiếp

Khi Case A / B / D đóng topic hiện tại HOẶC Case C cạn quota follow-up, chọn topic kế **deterministic**:

```
candidates = blueprint.topics WHERE session_topic_state[topic].status == NOT_TESTED

IF candidates is empty:
    → END SESSION (xem mục 7)

# Sắp xếp:
sort candidates BY:
  1. importance DESC                    (HIGH → MED → LOW)
  2. order_hint ASC                     (theo thứ tự admin định)
  3. NOT same_kind_as last 2 topics     (xen kẽ COMPETENCY/DOMAIN nếu type=MIXED)
  4. NOT same broad_category            (vd. tránh DATABASE → CACHING liên tiếp,
                                         nếu admin tag chúng cùng broad_category="data")

next_topic = candidates[0]

# Quyết difficulty cho câu mở đầu của topic kế:
running_perf = avg(score_normalized) các topic đã đóng (loại UNKNOWN)
adjustment = session.global_difficulty_offset (-1 nếu Case B trigger)

base = next_topic.target_difficulty
IF session.stretch_mode:                  difficulty = upgrade(base)
ELSE IF running_perf >= 7.5:              difficulty = upgrade(base)
ELSE IF running_perf <= 4.0:              difficulty = downgrade(base)
ELSE:                                      difficulty = base

difficulty = apply(adjustment, difficulty)
difficulty = clamp(difficulty, EASY, HARD)
```

Sau khi có `next_topic` và `difficulty`, có 2 lựa chọn cách chọn câu cụ thể trong topic:

### 6.1 Strategy A — Deterministic filter (đơn giản, mặc định)

```
candidate_pool = question-bank/filter:
  { type, competency|domain, difficulty, status=ACTIVE,
    exclude_ids: tất cả question_id đã hỏi trong session + K session gần nhất }

IF candidate_pool empty:
    relax difficulty (mở rộng ±1 nấc)
IF still empty:
    LOG "exhausted pool for topic X" → mark topic = ADEQUATE và skip
ELSE:
    pick candidate có ask_count thấp nhất → return
```

### 6.2 Strategy B — AI-driven (gọi `AI:/selector/next-question`)

Dùng khi muốn chọn câu **tận dụng weak signal toàn session** (không chỉ từ topic vừa đóng):

```
POST AI:/selector/next-question
  {
    session_id,
    interview_type: BEHAVIORAL | CORE_CONCEPTUAL,    // map từ topic.kind
    previous_evaluation: <full output AI vừa trả>,
    asked_question_ids: [...],
    constraints: {
      target_role: session.target_role,
      difficulty_hint: difficulty,
      competency: next_topic.value (nếu COMPETENCY),
      domain: next_topic.value (nếu DOMAIN)
    }
  }
→ { question_id, question_snapshot, rationale }

# Verify question_id thuộc question-bank và status=ACTIVE
# (chống AI hallucinate id từ embedding cũ)
```

### Khi nào chọn A vs B

| Tình huống | Strategy |
|---|---|
| Câu đầu tiên của topic mới (chuyển topic) | **A** — không cần AI vì rule rõ |
| Follow-up Tier 2 (Case C) | **B** với endpoint `/follow-up/generate` (không phải `/next-question`) |
| Câu thứ 2+ trong cùng topic (Case A2 second-chance) | **A** — đơn giản |
| Câu đầu session | **A** — như mục 3 |

→ Trong design này, `AI:/selector/next-question` **không** phải đường mặc định. Lý do:
- Selector dựa vào embedding similarity với weakness profile — phù hợp khi muốn "câu nào trong toàn pool match weakness", nhưng decision tree đã rule rõ topic & difficulty kế. Filter SQL đủ.
- Tiết kiệm 1 lần gọi LLM/embedding cho mỗi câu.
- Deterministic, debug được, explainable cho user ("hệ thống chọn câu này vì bạn chưa được test về caching").

Strategy B giữ làm **option** — admin/blueprint có thể bật `use_ai_selector = true` cho session level cao (senior) nơi nuance quan trọng hơn coverage thẳng.

---

## 7. Termination — kết thúc session

Bất kỳ điều kiện nào dưới đây thoả → flip `session.status = COMPLETED`:

1. **Coverage hoàn tất**: tất cả topic trong blueprint có `status != NOT_TESTED`.
2. **Question budget cạn**: `count(session_question) >= blueprint.question_budget` (kể cả follow-up).
3. **Time budget cạn**: `now() - session.started_at > blueprint.time_budget_minutes`.
4. **Hard stop từ user**: `POST /interviews/{sid}/finish`.
5. **Critical level mismatch**: Case B trigger giảm difficulty nhưng vẫn fail thêm 2 topic liên tiếp.

Sau khi tất cả answer đã `SCORED`, flip `session.status = SCORED`, build report:

```
SessionReport
  overall_score                 // weighted avg các topic đã assess (loại UNKNOWN)
  hire_signal_aggregated        // mode/median của hire_signal các topic STRONG/ADEQUATE
  topic_breakdown: [
    { topic_kind, topic_value, status, evidence_quality, score, feedback }
  ]
  topics_not_assessed: [...]    // status = UNKNOWN | NOT_TESTED — ghi rõ
  stretch_mode_triggered        // bool
  recommendations: [
    { area, suggestion, study_resources }   // cho UNKNOWN/WEAK topics
  ]
```

Báo cáo phân biệt rõ:
- **Topic assessed**: STRONG / ADEQUATE / PARTIAL / WEAK — có evidence, có điểm.
- **Topic not assessed**: UNKNOWN / NOT_TESTED — ghi "chưa đánh giá", không tính score.

Đây là yêu cầu honest reporting — không pretend đã test thứ chưa test.

---

## 8. Phân chia trách nhiệm

### `interview-service` (orchestrator) — owns:

- **Blueprint loading & topic state matrix** — toàn bộ `session_topic_state` + transition logic.
- **Decision tree** — class `NextQuestionPlanner` chứa logic Case A/B/C/D + chọn topic kế (mục 5-6).
- **Pre-authored follow-up resolution** — query `question_follow_up` table.
- **Question fetching** — gọi `question-bank/filter` với filter cụ thể.
- **AssessmentVerdict derivation** — map `evaluation-completed` payload (heterogeneous theo question type) thành `AssessmentVerdict` thống nhất.
- **Termination check** — sau mỗi câu, eval điều kiện kết thúc.
- **Report aggregation** — build `SessionReport` khi SCORED.

### AI service (`AI/`) — owns:

- **`/evaluate`** — đã có. Trả `BehavioralOutput` / `ConceptualOutput` / `LiveCodingOutput`.
- **`/selector/next-question`** — đã có. Dùng làm Strategy B (option), không phải đường mặc định.
- **`/follow-up/generate` (NEW)** — endpoint mới. Sinh follow-up question text + expected_points dựa vào parent question + weak_target. Sync, không qua Kafka (vì user đang đợi câu kế).

AI **không** quyết định:
- Topic nào hỏi kế (interview-service quyết theo blueprint).
- Có follow-up hay không (interview-service quyết theo Case C.1 rules).
- Khi nào kết thúc session (interview-service).

### `question-bank-service` — owns:

- **Catalog questions** — đã có entity Behavioral/Core/Coding.
- **`POST /questions/filter` (NEW)** — endpoint filter trả candidate pool (mục 10).
- **`question_follow_up` table (NEW)** — pre-authored follow-up linked to parent question.
- **`is_opener` tag (NEW)** — đánh dấu câu phù hợp mở đầu session.

### Sequence trao đổi (1 vòng full)

```
FE                interview-service       question-bank        AI service
 │ submit answer  │                            │                    │
 │───────────────►│                            │                    │
 │                │ persist answer SUBMITTED   │                    │
 │                │ (Kafka: answer-submitted) → tts-stt → transcript-ready
 │                │ (Kafka: evaluation-requested) ────────────────► │
 │                │                            │                    │ /evaluate
 │                │ (Kafka: evaluation-completed) ◄──────────────── │
 │                │                            │                    │
 │                │ derive AssessmentVerdict                        │
 │                │ update session_topic_state                      │
 │                │                            │                    │
 │                │ DECISION TREE (mục 5):                          │
 │                │   Case C → check pre-authored follow-up         │
 │                │   ─────────────────────────► (none)             │
 │                │   POST /follow-up/generate ─────────────────►  │
 │                │   ◄──────────────────── { question_text } ──── │
 │                │   persist as session_question (is_follow_up=true)
 │                │                                                  │
 │                │   Case D → MOVE NEXT TOPIC                       │
 │                │   compute next_topic + difficulty (mục 6)       │
 │                │   POST /questions/filter ──►│                   │
 │                │   ◄── candidate_pool ───────│                   │
 │                │   pick + persist            │                    │
 │                │                                                  │
 │ ◄ next question (poll hoặc SSE) ◄                                │
```

---

## 9. Database schema bổ sung

### Trong `question-bank-service` (DB `questionbank`)

```sql
-- Đánh dấu câu phù hợp mở đầu session
ALTER TABLE behavioral_questions ADD COLUMN is_opener BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE core_questions       ADD COLUMN is_opener BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_behavioral_opener ON behavioral_questions(is_opener) WHERE is_opener = TRUE;
CREATE INDEX idx_core_opener       ON core_questions(is_opener)       WHERE is_opener = TRUE;

-- Counter để chọn câu ít được hỏi (đa dạng cho user)
ALTER TABLE questions ADD COLUMN ask_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE questions ADD COLUMN last_asked_at TIMESTAMPTZ;
CREATE INDEX idx_questions_ask_count ON questions(ask_count);

-- Pre-authored follow-up
CREATE TABLE question_follow_up (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_question_id      VARCHAR(36) NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    probes_target_kind      VARCHAR(20) NOT NULL,    -- signal | concept | misconception | red_flag
    probes_target_value     VARCHAR(200) NOT NULL,   -- vd. "specific_recent_example", "composite_index_order"
    text                    TEXT NOT NULL,
    expected_points         JSONB,
    audio_key               VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (parent_question_id, probes_target_kind, probes_target_value)
);
CREATE INDEX idx_followup_parent ON question_follow_up(parent_question_id);
CREATE INDEX idx_followup_target ON question_follow_up(probes_target_kind, probes_target_value);
```

### Trong `interview-service` (DB `interview`)

```sql
-- Blueprint catalog (admin-managed, có thể seed bằng SQL)
CREATE TABLE interview_blueprint (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_role                 VARCHAR(20) NOT NULL,
    level                       VARCHAR(20) NOT NULL,
    interview_type              VARCHAR(20) NOT NULL,    -- BEHAVIORAL | CORE | MIXED
    topics                      JSONB NOT NULL,           -- xem mục 2
    question_budget             INT NOT NULL DEFAULT 8,
    max_follow_ups_per_topic    INT NOT NULL DEFAULT 2,
    max_follow_ups_per_session  INT NOT NULL DEFAULT 4,
    time_budget_minutes         INT NOT NULL DEFAULT 45,
    use_ai_selector             BOOLEAN NOT NULL DEFAULT FALSE,
    is_default                  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (target_role, level, interview_type, is_default) WHERE is_default = TRUE
);

-- Per-session topic state
CREATE TABLE session_topic_state (
    session_id              UUID NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    topic_kind              VARCHAR(20) NOT NULL,     -- COMPETENCY | DOMAIN
    topic_value             VARCHAR(50) NOT NULL,     -- vd. "CONFLICT_RESOLUTION", "DATABASE"
    status                  VARCHAR(20) NOT NULL,     -- NOT_TESTED|PROBING|STRONG|ADEQUATE|PARTIAL|WEAK|UNKNOWN
    importance              VARCHAR(10) NOT NULL,
    target_difficulty       VARCHAR(10) NOT NULL,
    questions_asked         INT NOT NULL DEFAULT 0,
    follow_ups_used         INT NOT NULL DEFAULT 0,
    last_difficulty         VARCHAR(10),
    last_score              REAL,
    last_assessment         JSONB,
    closed_at               TIMESTAMPTZ,
    PRIMARY KEY (session_id, topic_kind, topic_value)
);
CREATE INDEX idx_topic_state_session ON session_topic_state(session_id);

-- Mở rộng interview_session
ALTER TABLE interview_session ADD COLUMN blueprint_id              UUID REFERENCES interview_blueprint(id);
ALTER TABLE interview_session ADD COLUMN target_role               VARCHAR(20);
ALTER TABLE interview_session ADD COLUMN consecutive_unknown_count INT NOT NULL DEFAULT 0;
ALTER TABLE interview_session ADD COLUMN running_strong_count      INT NOT NULL DEFAULT 0;
ALTER TABLE interview_session ADD COLUMN global_difficulty_offset  INT NOT NULL DEFAULT 0;
ALTER TABLE interview_session ADD COLUMN stretch_mode              BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE interview_session ADD COLUMN total_follow_ups_used     INT NOT NULL DEFAULT 0;

-- Mở rộng session_question
ALTER TABLE session_question ADD COLUMN topic_kind            VARCHAR(20);
ALTER TABLE session_question ADD COLUMN topic_value           VARCHAR(50);
ALTER TABLE session_question ADD COLUMN difficulty            VARCHAR(10);
ALTER TABLE session_question ADD COLUMN parent_question_id    VARCHAR(36);    -- ref tới question gốc, NULL nếu không phải follow-up
ALTER TABLE session_question ADD COLUMN parent_session_question_id UUID;       -- ref tới session_question.id của câu cha trong cùng session
ALTER TABLE session_question ADD COLUMN is_follow_up          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE session_question ADD COLUMN source                VARCHAR(20) NOT NULL DEFAULT 'BANK';   -- BANK | PRE_AUTHORED_FOLLOWUP | AI_GENERATED
ALTER TABLE session_question ADD COLUMN inline_text           TEXT;            -- chỉ set khi source = AI_GENERATED
ALTER TABLE session_question ADD COLUMN inline_expected_points JSONB;

-- Mở rộng answer
ALTER TABLE answer ADD COLUMN verdict          JSONB;        -- AssessmentVerdict đã derive (mục 4)
ALTER TABLE answer ADD COLUMN raw_evaluation   JSONB;        -- Output gốc từ AI service (audit)
-- BỎ ràng buộc UNIQUE(session_id, question_id) cũ — follow-up có cùng parent_question_id
```

---

## 10. API contract bổ sung

### `question-bank-service`

#### `POST /api/v1/question-bank/questions/filter`

```json
// Request
{
  "type": "BEHAVIORAL",                  // BEHAVIORAL | CORE_CONCEPTUAL | LIVE_CODING
  "competency": "CONFLICT_RESOLUTION",   // optional, nếu type=BEHAVIORAL
  "domain": null,                        // optional, nếu type=CORE_CONCEPTUAL
  "target_role": "BACKEND",              // optional — chỉ filter câu match role
  "difficulty": "MEDIUM",                // optional
  "difficulty_in": ["EASY", "MEDIUM"],   // optional — alternative cho range
  "status": "ACTIVE",
  "tags_any": ["opener"],                // optional — match bất kỳ tag nào
  "tags_all": [],                        // optional — phải match tất cả
  "exclude_ids": ["q-001", "q-002"],
  "require_opener": true,                // optional — chỉ câu có is_opener=true
  "limit": 20,
  "order_by": "ASK_COUNT_ASC"            // ASK_COUNT_ASC | LAST_ASKED_DESC | RANDOM
}

// Response
{
  "code": 1000,
  "data": {
    "candidates": [
      {
        "id": "q-...",
        "type": "BEHAVIORAL",
        "competency": "CONFLICT_RESOLUTION",
        "difficulty": "MEDIUM",
        "text": "...",
        "audio_key": "questions/.../audio.mp3",
        "is_opener": true,
        "ask_count": 12
      }
    ],
    "total": 7
  }
}
```

#### `POST /api/v1/question-bank/internal/questions/{id}/mark-asked`

```json
// Request: empty body
// Response:
{ "code": 1000, "data": { "id": "...", "ask_count": 13, "last_asked_at": "..." } }
```

(Internal endpoint — chỉ interview-service gọi sau khi pin câu vào session.)

#### `GET /api/v1/question-bank/questions/{id}/follow-ups?probes_target_kind=signal&probes_target_value=specific_recent_example`

```json
// Response
{
  "code": 1000,
  "data": {
    "follow_ups": [
      {
        "id": "fu-...",
        "probes_target_kind": "signal",
        "probes_target_value": "specific_recent_example",
        "text": "Bạn vừa nói 'có lần đó'. Hãy mô tả cụ thể tình huống — khi nào, ai, làm gì?",
        "expected_points": ["thời gian cụ thể", "vai trò các bên", "hành động chi tiết"],
        "audio_key": "..."
      }
    ]
  }
}
```

### AI service (`/AI`)

#### `POST /follow-up/generate` (NEW)

```json
// Request
{
  "session_id": "...",
  "parent_question": {
    "id": "q-...",
    "type": "BEHAVIORAL",                   // BEHAVIORAL | CORE_CONCEPTUAL
    "text": "...",
    "competency": "CONFLICT_RESOLUTION",    // hoặc domain cho CORE
    "expected_signals": [...]               // hoặc key_concepts cho CORE
  },
  "user_answer_transcript": "...",
  "weak_target": {
    "kind": "signal",                       // signal | concept | misconception | red_flag
    "value": "specific_recent_example",
    "severity": "HIGH"
  },
  "strong_targets": [
    { "kind": "signal", "value": "fact_based_argument" }
  ],
  "difficulty": "MEDIUM",
  "language": "vi"
}

// Response
{
  "question_text": "Bạn vừa nói 'có lần đó'. Hãy mô tả tình huống cụ thể — thời gian, vai trò các bên, và hành động chi tiết của bạn.",
  "expected_points": [
    "thời gian cụ thể (tháng/năm/dự án)",
    "vai trò user trong tình huống",
    "ít nhất 2 hành động cụ thể"
  ],
  "rationale": "Câu trả lời gốc thiếu specific_recent_example — follow-up yêu cầu user cụ thể hoá để cover STAR.Situation.",
  "source": "AI_GENERATED",
  "model_meta": { "model": "gemini-flash-latest", "duration_ms": 850 }
}
```

#### `POST /selector/next-question` (đã có — dùng làm option Strategy B mục 6.2)

Giữ nguyên schema hiện tại. Interview-service chỉ gọi khi `blueprint.use_ai_selector = true`.

---

## Phụ lục — Checklist implement theo thứ tự

1. **Question-bank**: thêm `is_opener`, `ask_count`, `last_asked_at`, bảng `question_follow_up`. Endpoint `POST /questions/filter`, `POST /internal/questions/{id}/mark-asked`, `GET /questions/{id}/follow-ups`.
2. **Question-bank seed**: backfill `is_opener=true` cho các câu EASY mở (ưu tiên `audio_key` đã có sẵn).
3. **Interview-service schema**: tạo `interview_blueprint`, `session_topic_state`, mở rộng `session_question` + `answer` như mục 9. Seed 1-2 blueprint mẫu (`backend-mid-MIXED`).
4. **Interview-service**: implement `NextQuestionPlanner` với unit test cho từng Case A1/A2/B/C/D + topic selection + termination. Đây là core — phải có test coverage cao.
5. **Interview-service**: implement `AssessmentVerdictMapper` để chuẩn hoá output AI (Behavioral/Conceptual/LiveCoding) → `AssessmentVerdict`.
6. **AI service**: implement `POST /follow-up/generate` với 3 prompt template (Behavioral/Core/Coding). Test offline với một số weak_target mẫu.
7. **Interview-service flow integration**: wire decision tree vào `evaluation-completed` consumer → quyết câu kế → emit cho FE.
8. **Admin tooling**: UI/CLI quản lý `interview_blueprint` và `question_follow_up` (có thể postpone nếu seed bằng SQL trước).
