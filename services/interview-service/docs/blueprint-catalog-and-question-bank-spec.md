# Catalog Blueprint & Đặc tả tối thiểu cho Ngân hàng câu hỏi

> **Mục đích.** Đây là bản thiết kế (a) **toàn bộ blueprint cần có** cho mọi
> track/level thật của hệ thống, và (b) **yêu cầu tối thiểu của ngân hàng câu
> hỏi** để các blueprint đó chạy được — bạn dùng nó làm "đề bài" khi sưu tầm
> lại bộ câu hỏi.
>
> Viết dựa trên dữ liệu prod thật (VPS `contabo`, 2026-06-01) + cơ chế chọn câu
> đã phân tích trong `blueprint-interview-lifecycle.md`.
>
> Tài liệu liên quan:
> - `blueprint-interview-lifecycle.md` — vòng đời phiên & cơ chế planner/picker.
> - `question-selection-design.md` — đặc tả gốc.

---

## Mục lục

1. [Dữ liệu thật & vấn đề normalize (PHẢI đọc trước)](#1-dữ-liệu-thật--vấn-đề-normalize-phải-đọc-trước)
2. [Phạm vi: role × level đưa vào catalog](#2-phạm-vi-role--level-đưa-vào-catalog)
3. [Nguyên tắc thiết kế blueprint (công thức budget/time)](#3-nguyên-tắc-thiết-kế-blueprint-công-thức-budgettime)
4. [Catalog BEHAVIORAL (theo level)](#4-catalog-behavioral-theo-level)
5. [Catalog CODING (theo level)](#5-catalog-coding-theo-level)
6. [Catalog CORE (bộ domain theo từng role × level)](#6-catalog-core-bộ-domain-theo-từng-role--level)
7. [⭐ Đặc tả TỐI THIỂU của ngân hàng câu hỏi](#7--đặc-tả-tối-thiểu-của-ngân-hàng-câu-hỏi)
8. [Trường dữ liệu bắt buộc cho mỗi câu hỏi](#8-trường-dữ-liệu-bắt-buộc-cho-mỗi-câu-hỏi)
9. [Definition of Done — checklist nghiệm thu](#9-definition-of-done--checklist-nghiệm-thu)

---

## 1. Dữ liệu thật & vấn đề normalize (PHẢI đọc trước)

### 1.1 Track/level đang cấu hình trên prod

**9 track (active):** `AI/ML Engineer`, `BA`, `Backend`, `Data Engineer`,
`DevOps`, `Frontend`, `Fullstack`, `Mobile`, `QA/Tester`.

**8 level (active):** `Director`, `Intern`, `Junior`, `Lead`, `Manager`,
`Middle`, `Principal`, `Senior`.

### 1.2 🔴 Token sau khi qua `BlueprintNormalizer` (đây mới là khoá blueprint)

`/start` không khoá theo tên track, mà theo **token đã normalize**. Bảng dưới
là kết quả thật của `normalizeRole`/`normalizeLevel` trên tên track/level hiện
tại — **blueprint phải khoá đúng token này thì mới match**:

| Tên track (user-profile) | Token hiện tại | Token MONG MUỐN |
|---|---|---|
| Backend | `BACKEND` ✅ | BACKEND |
| Frontend | `FRONTEND` ✅ | FRONTEND |
| Fullstack | `FULLSTACK` ✅ | FULLSTACK |
| Mobile | `MOBILE` ✅ | MOBILE |
| DevOps | `DEVOPS` ✅ | DEVOPS |
| Data Engineer | `DATA_ENGINEER` ✅ | DATA_ENGINEER |
| **QA/Tester** | `QATESTER` ⚠️ | **QA** |
| **AI/ML Engineer** | `AIML_ENGINEER` ⚠️ | **AI_ML** |
| **BA** | `BA` ✅ (tình cờ) | BA |

| Level (user-profile) | Token hiện tại | Token MONG MUỐN |
|---|---|---|
| Intern / Junior | `junior` ✅ | junior |
| Middle | `mid` ✅ | mid |
| Senior / Lead | `senior` ✅ | senior |
| **Staff** (nếu có) | `staff` ⚠️ | senior |
| **Lead** | `senior` ✅ | senior |
| **Principal** | `principal` ⚠️ | **senior** |
| **Manager** | `manager` ⚠️ | **senior** (hoặc loại) |
| **Director** | `director` ⚠️ | **senior** (hoặc loại) |

### 1.3 Việc cần làm trước khi seed (prerequisite — sửa code)

Để khoá blueprint sạch và bền, **bổ sung alias table** trong
`interview-service/.../common/util/BlueprintNormalizer.java`:

```java
// TRACK_ALIASES — thêm:
"qa/tester",        "QA",
"qa",               "QA",
"tester",           "QA",
"ai/ml engineer",   "AI_ML",
"ai/ml",            "AI_ML",
"ml engineer",      "AI_ML",
"data engineer",    "DATA_ENGINEER",   // hiện fallback ra đúng, nhưng nên khai báo tường minh
"ba",               "BA",
"business analyst", "BA",

// LEVEL_ALIASES — thêm (gom cấp quản lý/cao về senior):
"principal", "senior",
"manager",   "senior",
"director",  "senior",
```

> Nếu **không** sửa normalizer, bạn buộc phải seed blueprint theo token xấu
> (`QATESTER`, `AIML_ENGINEER`, và 3 token level quản lý) — dễ vỡ khi admin đổi
> tên track. **Khuyến nghị mạnh: sửa normalizer rồi seed token sạch.** Phần còn
> lại của tài liệu dùng **token MONG MUỐN**.

---

## 2. Phạm vi: role × level đưa vào catalog

- **9 role** (token sạch): `BACKEND, FRONTEND, FULLSTACK, MOBILE, DEVOPS, QA,
  DATA_ENGINEER, AI_ML, BA`.
- **3 level kỹ thuật**: `junior, mid, senior` (mọi cấp quản lý gom về `senior`).
- **3 loại**: `BEHAVIORAL, CORE, CODING`.

**Ngoại lệ `BA`:** Business Analyst không lập trình. → **BA chỉ có BEHAVIORAL +
CORE**, **không seed CODING** cho BA. (Nếu sản phẩm muốn BA cũng có bài logic
nhẹ thì seed CODING junior cho BA — mặc định tài liệu: bỏ.)

**Tổng số blueprint:**
- BEHAVIORAL: 9 role × 3 level = **27**
- CODING: 8 role (trừ BA) × 3 level = **24**
- CORE: 9 role × 3 level = **27**
- **Tổng = 78 blueprint** (mỗi bộ ba đúng 1 bản `is_default=true`).

> BEHAVIORAL và CODING **độc lập với role** về nội dung (xem §4, §5) → tuy 27/24
> dòng nhưng chỉ dựng từ **3 template theo level**. Chỉ CORE mới cần thiết kế
> riêng theo role (§6).

---

## 3. Nguyên tắc thiết kế blueprint (công thức budget/time)

Rút ra từ planner (`NextQuestionPlanner`) — xem lý giải ở
`blueprint-interview-lifecycle.md` §5.3:

1. **Phủ hết topic dù có follow-up:**
   ```
   question_budget ≥ số_topic + max_follow_ups_per_session
   ```
   (Follow-up ăn budget nhưng không tăng độ phủ → thiếu budget = bỏ sót topic.)

2. **Thời gian khớp budget** (đồng hồ phiên là wall-clock, gồm cả độ trễ xử lý):
   ```
   time_budget_minutes ≈ question_budget × phút_mỗi_câu × 1.25 (đệm latency)
   ```
   - BEHAVIORAL/CORE (trả lời nói): ~4 phút/câu.
   - CODING: tính theo độ khó (E~15′, M~25′, H~35′), không nhân hệ số latency
     (CODING pin câu kế ngay, không chờ chấm).

3. **Mở nhẹ:** topic `orderHint=1` nên là EASY, `importance=MED` (câu khởi
   động). BEHAVIORAL luôn để 1 competency mở đầu.

4. **CODING:** `question_budget = số slot`; `max_follow_ups_* = 0`.

5. **Depth-first (1 round thật):** ưu tiên ÍT topic + follow-up RỘNG thay vì
   nhiều topic nông. Behavioral 3–4 LP, CORE 3–4 domain, CODING 2 bài. Cap
   follow-up cao chỉ *cho phép* đào sâu — planner tự quyết hỏi thêm hay chuyển.

---

## 4. Catalog BEHAVIORAL (theo level)

Nội dung **dùng chung cho cả 9 role** (năng lực hành vi không phụ thuộc track).
`kind=COMPETENCY` cho mọi topic. **Taxonomy = 16 Leadership Principles của
Amazon** (enum `Competency` ở question-bank, mở rộng + nới cột VARCHAR(64) ở
question-bank migration V5). `topicValue` = tên LP token byte-for-byte.

> **Depth-first (mô phỏng 1 round thật):** ít LP, **follow-up rộng** để planner
> đào sâu — phỏng vấn behavioral thật sống bằng follow-up (đào 3–5 lớp để vượt
> câu trả lời học thuộc), không phải lướt nhiều LP. Tiến trình theo level:
> junior = LP nền tảng; mid = thực thi/khách hàng; senior = phán đoán & lãnh đạo.

### junior — budget 7, fu **2**/topic · 4/phiên, **40 phút**
| # | competency (LP) | importance | targetDifficulty |
|---|---|---|---|
| 1 | OWNERSHIP | MED | EASY |
| 2 | CUSTOMER_OBSESSION | HIGH | EASY |
| 3 | LEARN_AND_BE_CURIOUS | HIGH | EASY |

→ 3 topic + 4 fu = budget 7.

### mid — budget 9, fu **3**/topic · 6/phiên, **50 phút**
| # | competency (LP) | importance | targetDifficulty |
|---|---|---|---|
| 1 | OWNERSHIP | MED | EASY |
| 2 | CUSTOMER_OBSESSION | HIGH | MEDIUM |
| 3 | DELIVER_RESULTS | HIGH | MEDIUM |

→ 3 + 6 = 9.

### senior — budget 10, fu **3**/topic · 6/phiên, **55 phút**
| # | competency (LP) | importance | targetDifficulty |
|---|---|---|---|
| 1 | OWNERSHIP | MED | MEDIUM |
| 2 | ARE_RIGHT_A_LOT | HIGH | HARD |
| 3 | HAVE_BACKBONE_DISAGREE_AND_COMMIT | HIGH | HARD |
| 4 | HIRE_AND_DEVELOP_THE_BEST | MED | MEDIUM |

→ 4 + 6 = 10.

> Follow-up cap chỉ **cho phép** đào sâu, không ép: planner chỉ hỏi follow-up khi
> câu trả lời yếu/nông và có weak-target (Case A2/C); câu trả lời mạnh (Case D)
> chuyển topic ngay. Nên cap cao = sâu-khi-cần, an toàn.

**16 LP trong enum `Competency`** (admin có thể tự lắp blueprint từ bất kỳ LP nào):
CUSTOMER_OBSESSION, OWNERSHIP, INVENT_AND_SIMPLIFY, ARE_RIGHT_A_LOT,
LEARN_AND_BE_CURIOUS, HIRE_AND_DEVELOP_THE_BEST, INSIST_ON_HIGHEST_STANDARDS,
THINK_BIG, BIAS_FOR_ACTION, FRUGALITY, EARN_TRUST, DIVE_DEEP,
HAVE_BACKBONE_DISAGREE_AND_COMMIT, DELIVER_RESULTS,
STRIVE_TO_BE_EARTHS_BEST_EMPLOYER, SUCCESS_AND_SCALE_BROAD_RESPONSIBILITY.
(Competency generic cũ — TEAMWORK, COMMUNICATION, ... — còn trong enum dạng
LEGACY để câu cũ không vỡ, blueprint mặc định không dùng.)

**7 LP được dùng trong catalog mặc định** (union 3 level): OWNERSHIP,
CUSTOMER_OBSESSION, LEARN_AND_BE_CURIOUS, DELIVER_RESULTS, ARE_RIGHT_A_LOT,
HAVE_BACKBONE_DISAGREE_AND_COMMIT, HIRE_AND_DEVELOP_THE_BEST.

**Độ khó cần phủ** (opening = target.downgrade() ở topic đầu, adapt ±1 sau đó):
junior→EASY(/MED); mid→EASY/MEDIUM; senior→MEDIUM/HARD.

---

## 5. Catalog CODING (theo level)

`topics` = lịch độ khó (bỏ trống `kind`/`topicValue`). Dùng chung mọi role (trừ
BA). `max_follow_ups_* = 0`. **Depth-first: 2 bài/round** — đúng 1 vòng coding
thật (1–2 bài có tương tác sâu), không phải bài thi online nhiều câu.

| level | slots (ramp) | budget | time |
|---|---|---|---|
| junior | EASY · MEDIUM | 2 | 60′ |
| mid | MEDIUM · HARD | 2 | 80′ |
| senior | HARD · HARD | 2 | 90′ |

> Mỗi slot cần **1 câu LIVE_CODING distinct trong phiên**. CODING là pool dùng
> chung (câu coding không gắn role/domain), nên cùng một bộ câu phục vụ mọi
> role. 2 bài/round cũng dễ lấp hơn khi ngân hàng coding còn mỏng.

---

## 6. Catalog CORE (bộ domain theo từng role × level)

`kind=DOMAIN`. Mỗi role có một "kit" domain xếp theo độ ưu tiên; level quyết
định **lấy bao nhiêu domain + độ khó + budget/time**. **Depth-first: ít domain,
follow-up rộng** (3–4 domain đào sâu thay vì lướt 6 domain nông).

- **junior:** **3 domain** đầu · `EASY` · budget = 3+3 = **6** · fu 1/topic·3/phiên · **40′**
- **mid:** **3 domain** đầu · `MEDIUM` · budget = 3+4 = **7** · fu 2/topic·4/phiên · **45′**
- **senior:** **4 domain** đầu · `HARD` (2 domain đầu) + `MEDIUM` · budget = 4+5 = **9** · fu 2/topic·5/phiên · **55′**

`importance`: domain #1–2 = HIGH, còn lại MED. `orderHint` = thứ tự trong kit.

> **System Design** chỉ là một **domain CORE conceptual bình thường** (không phải
> round riêng — hệ thống không có phỏng vấn system design). Nó nằm trong kit của
> các role thiên kiến trúc (backend/fullstack/devops/data/ai_ml); các role khác
> không có.

### Kit domain theo role (xếp ưu tiên giảm dần)

| Role | Domain kit (orderHint 1 → n) |
|---|---|
| **BACKEND** | DATABASE · SYSTEM_DESIGN · LANGUAGE_SPECIFIC · DESIGN_PATTERN · CACHING · TESTING · MESSAGING · SECURITY |
| **FRONTEND** | FRONTEND_DEV · FRAMEWORK · LANGUAGE_SPECIFIC · DESIGN_PATTERN · TESTING · NETWORKING · SECURITY |
| **FULLSTACK** | DATABASE · FRONTEND_DEV · SYSTEM_DESIGN · FRAMEWORK · LANGUAGE_SPECIFIC · TESTING · SECURITY |
| **MOBILE** | MOBILE_DEV · FRAMEWORK · LANGUAGE_SPECIFIC · DESIGN_PATTERN · TESTING · NETWORKING · SECURITY |
| **DEVOPS** | DEVOPS_TOOLS · NETWORKING · SYSTEM_DESIGN · OS · SECURITY · MESSAGING · DATABASE |
| **QA** | TESTING · LANGUAGE_SPECIFIC · DATABASE · NETWORKING · SECURITY |
| **DATA_ENGINEER** | DATA_ENGINEERING · DATABASE · SYSTEM_DESIGN · MESSAGING · CACHING · AI_ML |
| **AI_ML** | AI_ML · DATA_ENGINEERING · DATABASE · SYSTEM_DESIGN · LANGUAGE_SPECIFIC · MESSAGING |
| **BA** | BUSINESS_ANALYSIS · DATABASE · TESTING · SECURITY |

> Ví dụ `BACKEND-mid-CORE`: 3 domain đầu (DATABASE, SYSTEM_DESIGN,
> LANGUAGE_SPECIFIC) ở MEDIUM, budget 7, fu 2/4, 45′. `BACKEND-senior-CORE`: 4
> domain đầu, DATABASE+SYSTEM_DESIGN = HARD, còn lại MEDIUM, budget 9, 55′.
> `FRONTEND`/`MOBILE`/`QA`/`BA` không có SYSTEM_DESIGN trong 3–4 domain đầu.

**Lưu ý filter `targetRole`:** CORE query lọc cứng `targetRole = ANY(target_roles)`
ở 2 lần thử đầu, **bỏ role ở lần relax**. → Câu CORE **nên** gắn `target_roles`
chứa role tương ứng để câu đúng-role được ưu tiên; nếu không gắn, vẫn dùng được
nhờ relax nhưng mất tính nhắm-role. (Domain + difficulty là bắt buộc; role là
nên-có.)

---

## 7. ⭐ Đặc tả TỐI THIỂU của ngân hàng câu hỏi

Đây là phần "đề bài" cho việc sưu tầm câu hỏi. Hai mức:
- **SÀN (functional):** đủ để mọi phiên *chạy không lỗi* (`/start` không
  `QUESTION_BANK_UNAVAILABLE`). Lặp lại nhiều, ít cá nhân hoá.
- **KHUYẾN NGHỊ (chất lượng):** đủ đa dạng để các phiên không trùng câu, hỗ trợ
  adapt độ khó và loại-trùng-đã-hỏi.

> Đơn vị "cell" = một ô `(competency|domain × difficulty)`.
> Lý do cần ≥3/cell ở mức khuyến nghị: picker loại `excludeIds` (câu đã hỏi),
> adapt độ khó ±1, và nhiều phiên khác nhau không nên gặp cùng 1 câu.

### 7.1 BEHAVIORAL (7 LP được dùng × độ khó)

Catalog mặc định dùng **7 Leadership Principle** (xem §4): OWNERSHIP,
CUSTOMER_OBSESSION, LEARN_AND_BE_CURIOUS, DELIVER_RESULTS, ARE_RIGHT_A_LOT,
HAVE_BACKBONE_DISAGREE_AND_COMMIT, HIRE_AND_DEVELOP_THE_BEST.

| Mức | Mỗi LP cần | Tổng (7 LP dùng) |
|---|---|---|
| **SÀN** | ≥1 ở độ khó được dùng (opener cho LP đầu) + relax cho neighbor | ~7–14 |
| **KHUYẾN NGHỊ** | EASY ≥3 (≥1 opener) · MEDIUM ≥3 · HARD ≥2 | **≈56** |

> Depth-first dồn nhiều follow-up vào ÍT LP. Follow-up phần lớn do **AI sinh**
> (không tốn câu trong bank — xem §7.4), nên dù đào sâu, pool câu **gốc** mỗi LP
> không cần nhiều. LP HARD chỉ cho senior (ARE_RIGHT_A_LOT, HAVE_BACKBONE).
> Còn 9 LP nữa trong enum để admin tự lắp blueprint khác.

- **Opener:** câu mở phiên (`requireOpener` ở Q1) cần một số câu EASY gắn cờ
  `is_opener=TRUE`, ưu tiên các competency hay đứng `orderHint=1` (OWNERSHIP).
  *Không bắt buộc tuyệt đối* (lần thử 2 bỏ opener), nhưng có thì Q1 tự nhiên hơn.

### 7.2 CORE (theo domain được dùng × độ khó)

Depth-first chỉ lấy **3–4 domain đầu** mỗi kit → tập domain thực dùng (union
first-4 mọi role) ≈ **15 domain**:
`DATABASE, SYSTEM_DESIGN, LANGUAGE_SPECIFIC, DESIGN_PATTERN, TESTING, MESSAGING,
SECURITY, FRONTEND_DEV, FRAMEWORK, NETWORKING, MOBILE_DEV, DEVOPS_TOOLS, OS,
DATA_ENGINEERING, AI_ML, BUSINESS_ANALYSIS` (CACHING rơi ra khỏi top-4 → không bắt buộc).

Độ khó cần theo level: junior→EASY, mid→MEDIUM, senior→MEDIUM/HARD. → mỗi
domain xuất hiện ở 3 level của role cần cả 3 độ khó.

| Mức | Mỗi domain cần | Tổng (≈15 domain) |
|---|---|---|
| **SÀN** | ≥1 ở mỗi độ khó mà domain đó được dùng | ~18–32 |
| **KHUYẾN NGHỊ** | EASY ≥3 · MEDIUM ≥3 · HARD ≥2 | **≈120** |

- Domain "trục chính" của mỗi role (DATABASE cho BACKEND, MOBILE_DEV cho MOBILE,
  DEVOPS_TOOLS cho DEVOPS, TESTING cho QA, DATA_ENGINEERING cho DATA_ENGINEER,
  AI_ML cho AI_ML, BUSINESS_ANALYSIS cho BA) nên **nhiều hơn** (≥5/độ khó) vì
  xuất hiện ở mọi level của role đó.
- **`target_roles`:** gắn role phù hợp cho câu để được ưu tiên (xem §6).
- **Opener CORE:** vài câu EASY `is_opener=TRUE` ở domain trục chính mỗi role.

### 7.3 CODING (chỉ theo độ khó — pool dùng chung mọi role)

Câu coding không có competency/domain. Slot tối đa trong 1 blueprint = **2**; cần
distinct trong phiên.

| Mức | EASY | MEDIUM | HARD | Tổng |
|---|---|---|---|---|
| **SÀN** | ≥2 | ≥2 | ≥2 | ≥6 |
| **KHUYẾN NGHỊ** | ≥3 | ≥4 | ≥4 | **≈11** |

- **BẮT BUỘC mỗi câu coding:** có `functionMeta` + `testCases` (≥1 case) +
  `starterCode`. Thiếu một trong số này → picker **bỏ qua** câu đó (judge không
  chấm được). Đây là ràng buộc cứng, khác BEHAVIORAL/CORE.

### 7.4 Follow-up soạn sẵn (TUỲ CHỌN)

Planner khi cần đào sâu sẽ thử **follow-up soạn sẵn** (bảng follow-up, khớp
`parentQuestionId + weakTarget`); không có thì **AI tự sinh**. → **Tối thiểu = 0**
(AI lo). Khuyến nghị: soạn 1–2 follow-up cho các câu BEHAVIORAL/CORE hay dùng để
tiết kiệm chi phí AI và tăng chất lượng.

### 7.5 Tổng kết con số tối thiểu

| Loại | SÀN | KHUYẾN NGHỊ |
|---|---|---|
| BEHAVIORAL (7 LP) | ~7–14 | ~56 |
| CORE (~15 domain) | ~18–32 | ~120 |
| CODING | ≥6 | ~11 |
| Follow-up soạn sẵn | 0 | tuỳ |
| **Tổng** | **~31–52** | **~187** |

---

## 8. Trường dữ liệu bắt buộc cho mỗi câu hỏi

Picker đóng băng "snapshot" câu hỏi vào phiên; evaluator/judge đọc từ đó. Mỗi
loại cần tối thiểu:

### BEHAVIORAL
- `text` (đề), `difficulty`, `competency` (đúng enum), `tags[]` (tuỳ chọn — bias
  theo techStack/industry), `is_opener` (bool), `audioKey` (tuỳ chọn TTS).
- **`expectedSignals[]`** — tín hiệu mong đợi để evaluator chấm. Nên có.

### CORE_CONCEPTUAL
- `text`, `difficulty`, `domain` (đúng enum), `target_roles[]` (nên có),
  `tags[]` (tuỳ chọn), `is_opener`.
- **`keyConcepts[]`** + **`depthExpected`** — cho evaluator. Nên có.

### LIVE_CODING (ràng buộc cứng)
- `title`, `description`, `constraints`, `difficulty`.
- **`functionMeta`** (chữ ký hàm) — **bắt buộc**.
- **`testCases[]`** (≥1) — **bắt buộc** (judge chạy mọi case được đưa).
- `starterCode`, `optimalTimeComplexity`, `optimalSpaceComplexity` — nên có.
- **Không** có competency/domain.

> ⚠️ `tags` là chuỗi tự do và hiện so khớp **chính xác hoa/thường** trong SQL
> (`q.tags && :tags`) nhưng scoring Java lại lowercase → để tag phát huy tác
> dụng cá nhân hoá, **chuẩn hoá tag về lowercase** khi nhập (vd `react`,
> `nodejs`, `fintech`). Sai hoa/thường không làm hỏng phiên (relax bỏ tag) nhưng
> mất cá nhân hoá.

---

## 9. Definition of Done — checklist nghiệm thu

**Code prerequisite:**
- [ ] Đã bổ sung alias `BlueprintNormalizer` (§1.3): QA, AI_ML, BA, và
      principal/manager/director → senior. ✅ *(đã làm)*
- [ ] question-bank: enum `Competency` thêm 16 Amazon LP + migration V5 nới cột
      `behavioral_questions.competency` lên VARCHAR(64). ✅ *(đã làm)*
- [ ] Xác nhận token sạch: thử `normalizeRole`/`normalizeLevel` cho cả 9 track ×
      level thật → ra đúng token catalog.

**Seed blueprint (78 bản — migration V6):**
- [ ] 27 BEHAVIORAL (3 template level × 9 role) — topic = Amazon LP.
- [ ] 24 CODING (3 template level × 8 role, trừ BA).
- [ ] 27 CORE (9 kit role × 3 level), domain đúng enum, `targetDifficulty` theo
      level, budget = #topic + fu/session, time theo công thức §3.
- [ ] Mỗi bộ ba `(role, level, type)` đúng **1** bản `is_default=true`.
- [ ] (tuỳ chọn) Dọn các bản `MIXED` cũ (enum đã gỡ) — chưa làm để tránh FK.

**Ngân hàng câu hỏi (mức SÀN tối thiểu để chạy):**
- [ ] BEHAVIORAL: mỗi LP được dùng (**7**) ≥1 câu ở độ khó tương ứng, LP đầu có opener.
- [ ] CORE: mỗi domain trong **~15 domain** ≥1 câu ở độ khó được dùng; domain trục
      chính mỗi role có đủ EASY/MEDIUM/HARD.
- [ ] CODING: ≥2 EASY, ≥2 MEDIUM, ≥2 HARD — **mỗi câu có functionMeta + testCases + starterCode**.
- [ ] (khuyến nghị) tiến tới các con số ở cột KHUYẾN NGHỊ §7.5 để hết lặp câu.

**Kiểm thử end-to-end mỗi loại:**
- [ ] `/start` cho từng `(role, level, type)` → không `BLUEPRINT_NOT_FOUND`,
      không `QUESTION_BANK_UNAVAILABLE`; pin được câu 1.
- [ ] BEHAVIORAL/CORE chạy hết tới `EndSession` (coverage hoặc budget) → SCORED.
- [ ] CODING submit đủ slot → mỗi bài có verdict judge (+AI nếu chạy được).
