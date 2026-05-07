# Start Session — First Question Pick Flow

Mô tả tuần tự, có path tuyệt đối + tên hàm + số dòng, để agent đọc 1 lần là nắm được luồng mà không phải grep lại.

> Phạm vi: từ HTTP `POST /api/v1/interviews/start` đến lúc câu hỏi đầu tiên được lưu vào `session_question` và trả về cho FE.

---

## 0. Tổng quan 1 dòng

`SessionController` → `SessionService.start()` (1 transaction) → load profile → resolve blueprint → tạo session → seed topic states → pick first topic → tính opening difficulty → `QuestionPicker.pickForTopic()` (gọi `question-bank /filter`) → save `session_question` → promote topic sang `PROBING` → trả `StartSessionOutput`.

---

## 1. Entry point — Controller

**File**: `services/interview-service/src/main/java/com/mockwise/interview/controller/SessionController.java:42-49`

- Method: `start(CustomUserDetails user, StartSessionInput input)`
- DTO request: `services/interview-service/src/main/java/com/mockwise/interview/dto/request/StartSessionInput.java`
  - `interviewType` (BEHAVIORAL / CORE / MIXED) — `@NotNull`
  - `timeBudgetMinutesOverride` (optional)
- DTO response: `services/interview-service/src/main/java/com/mockwise/interview/dto/response/StartSessionOutput.java`
- Delegate ngay: `sessionService.start(user.getUserId(), input)`

---

## 2. Orchestrator — SessionService

**File**: `services/interview-service/src/main/java/com/mockwise/interview/service/SessionService.java:84-145`

Method: `start(String userId, StartSessionInput input)` — `@Transactional`

Thứ tự bước (số dòng tham chiếu trong cùng file):

| # | Code | Việc | Gọi tới |
|---|---|---|---|
| 1 | `:89` | Defensive null-check `interviewType` | — |
| 2 | `:93` | Load profile từ user-profile-service | `UserProfileAdapter.getProfile` |
| 3 | `:94-95` | Chuẩn hoá role + level | `BlueprintNormalizer.normalizeRole/Level` |
| 4 | `:97-98` | Tìm blueprint default | `BlueprintLoader.findFor` |
| 5 | `:100` | Tính `globalDifficultyOffset` | `SessionService.computeDifficultyOffset` (`:337-355`) |
| 6 | `:102-115` | Insert row `interview_session`, status = `IN_PROGRESS` | `InterviewSessionRepository.save` |
| 7 | `:117` | Seed `session_topic_state[]` (tất cả `NOT_TESTED`) | `BlueprintLoader.seedTopicStates` |
| 8 | `:119` | Chọn topic mở màn | `BlueprintLoader.pickFirstTopic` |
| 9 | `:120` | Tính difficulty mở màn | `SessionService.computeOpeningDifficulty` (`:357-363`) |
| 10 | `:122-123` | **Pick câu hỏi đầu** (sequence=1, excludeIds=`[]`) | `QuestionPicker.pickForTopic` |
| 11 | `:127-133` | Promote topic đó sang `PROBING`, set `questionsAsked=1` | `SessionTopicStateRepository.findById/save` |
| 12 | `:135-144` | Build `StartSessionOutput`, sign audio URL | `signAudio` (`:208-211`) → `StorageAdapter.signQuestionAudioUrl` |

### 2.1 `computeDifficultyOffset` (`:337-355`)
Input: profile.experience, profile.yearsInCurrentRole, level.
- Map `EXPECTED_MIN_YEARS = {junior:0, mid:2, senior:5}`.
- `effectiveYears = yearsInRole != null ? min(experience, yearsInRole+1) : experience`.
- Nếu `effectiveYears - minYears < -1` → return `-1` (giảm độ khó).
- Còn lại → `0` (start không bao giờ trả +1; stretch handled trong planner).

### 2.2 `computeOpeningDifficulty` (`:357-363`)
- `preliminary = topic.targetDifficulty.downgrade()` (1 nấc dưới target).
- Nếu `globalOffset < 0` → downgrade thêm 1 lần.

---

## 3. Helper class — BlueprintNormalizer

**File**: `services/interview-service/src/main/java/com/mockwise/interview/common/util/BlueprintNormalizer.java`

Static utility, không state.

| Method | Dòng | Nhiệm vụ |
|---|---|---|
| `normalizeRole(String)` | `:50-57` | "Backend" → `BACKEND`, fallback uppercase + replace whitespace/dash |
| `normalizeLevel(String)` | `:60-65` | "Mid"/"Middle" → `mid`, "Lead"/"Staff"/"Senior" → `senior`, … |

Bảng aliases ở `:25-47` (TRACK_ALIASES, LEVEL_ALIASES).

---

## 4. Blueprint resolver — BlueprintLoader

**File**: `services/interview-service/src/main/java/com/mockwise/interview/service/BlueprintLoader.java`

| Method | Dòng | Việc |
|---|---|---|
| `findFor(role, level, type)` | `:45-49` | Tìm `interview_blueprint` matching `(target_role, level, interview_type, is_default=true)` |
| `seedTopicStates(sessionId, blueprint)` | `:56-68` | Insert N row `session_topic_state` (1 row/topic), status `NOT_TESTED` |
| `pickFirstTopic(blueprint)` | `:80-94` | **Topic mở màn** |

### 4.1 `pickFirstTopic` logic (`:80-94`)
Comparator:
1. Nếu `interviewType ≠ CORE`: ưu tiên `kind == COMPETENCY` trước (thenComparing).
2. Tie-break tiếp theo: `orderHint ASC`.

> ⚠️ Code KHÔNG break tie tiếp theo bằng `importance DESC` (mặc dù Javadoc nói có). Hai topic cùng `kind` + cùng `orderHint` → thứ tự không xác định.

Repository: `services/interview-service/src/main/java/com/mockwise/interview/repository/InterviewBlueprintRepository.java`
Entity: `services/interview-service/src/main/java/com/mockwise/interview/entity/InterviewBlueprint.java`, `entity/BlueprintTopic.java`

Blueprint seeds (data đang chạy trên VPS):
- `services/interview-service/src/main/resources/db/migration/V2__seed_default_blueprints.sql` (MIXED)
- `services/interview-service/src/main/resources/db/migration/V3__seed_behavioral_and_core_blueprints.sql` (BEHAVIORAL + CORE)

---

## 5. Question picker — QuestionPicker.pickForTopic

**File**: `services/interview-service/src/main/java/com/mockwise/interview/service/QuestionPicker.java:80-118`

Signature:
```java
SessionQuestion pickForTopic(UUID sessionId, BlueprintTopic topic, Difficulty difficulty,
                             UserProfileResponse profile, List<String> excludeIds, int sequence)
```

Lưu ý: ở `/start`, `excludeIds = List.of()` và `sequence = 1`.

### 5.1 Bước 1 — Build tag bias (`:89`)
- `buildTagBias(profile)` (`:311-318`): `techStack ∪ industries`, lowercase, dedupe via `HashSet`.

### 5.2 Bước 2 — 3 tầng cascade (`:91-96`)
| Tier | Method | Tham số khác biệt | Khi nào fallback |
|---|---|---|---|
| 1 | `tryFilter(..., requireOpener=true)` | `requireOpener=true` (vì `sequence==1`) | pool rỗng |
| 2 | `tryFilter(..., requireOpener=false)` | bỏ ép opener | pool rỗng |
| 3 | `tryFilterRelaxed(...)` | bỏ `targetRole` + `tagBias`, mở rộng difficulty ±1 | pool rỗng → throw `QUESTION_BANK_UNAVAILABLE` |

### 5.3 `tryFilter` (`:130-153`)
- Build `QuestionFilterRequest` (`dto/QuestionFilterRequest.java`) với các field:
  - `type` (BEHAVIORAL / CORE_CONCEPTUAL — quyết định bằng `type(topic)` ở `:304-308`)
  - `competency` (nếu `topic.kind == COMPETENCY`)
  - `domain` (nếu `topic.kind == DOMAIN`)
  - `targetRole` (đã normalize)
  - `difficulty`
  - `tagsAny` = tagBias
  - `excludeIds`
  - `requireOpener`
  - `limit = 20`
- Gọi `QuestionBankAdapter.filter(req)` → trả `QuestionFilterResponse`.
- Nếu pool có dữ liệu → `applyLocalScoring(...)`.

### 5.4 `tryFilterRelaxed` (`:156-177`)
Lặp qua `difficultyNeighbours(d)` (`:179-185`):
- EASY → [EASY, MEDIUM]
- MEDIUM → [MEDIUM, EASY, HARD]
- HARD → [HARD, MEDIUM]

Mỗi lần gọi filter với `targetRole=null`, `tagsAny=null`, `requireOpener=false`, lấy `candidates.get(0)` (KHÔNG qua local scoring).

### 5.5 `applyLocalScoring` (`:187-205`)
Score mỗi candidate:
| Điều kiện | Điểm |
|---|---|
| `requireOpener && c.isOpener()` | +10 (⚠️ no-op trong tier 1 vì SQL đã ép `is_opener=TRUE` → mọi candidate đều +10 → không phân biệt) |
| `c.tags ∩ profile.techStack ≠ ∅` | +3 |
| `c.tags ∩ profile.industries ≠ ∅` | +3 |
| `(int)(c.askCount() / 100)` | -1 cho mỗi 100 lần asked (rất yếu) |

Pick `Stream.max` → tie-break là phần tử đầu tiên (deterministic theo `q.ask_count ASC, q.created_at DESC` từ SQL).

### 5.6 Bước 3 — Fire-and-forget mark-asked (`:99`)
- `QuestionBankAdapter.markAskedSoft(chosen.id())` — try/catch, log warn nếu fail.

### 5.7 Bước 4 — Lấy rich snapshot (`:104`)
- `QuestionBankAdapter.getSnapshotSoft(chosen.id())` — try/catch, fallback null nếu fail.

### 5.8 Bước 5 — Save `session_question` (`:106-117`)
- Sequence=1, isFollowUp=false, source=`BANK`.
- Snapshot build qua `buildSnapshot(c, rich)` (`:346-383`): merge candidate + rich snapshot, ưu tiên rich.
- Repository: `services/interview-service/src/main/java/com/mockwise/interview/repository/SessionQuestionRepository.java`

---

## 6. Adapters (cross-service)

### 6.1 UserProfileAdapter
**File**: `services/interview-service/src/main/java/com/mockwise/interview/client/userprofile/UserProfileAdapter.java`
- `getProfile(userId)` → `UserProfileResponse` (record có `position{trackName, levelName}`, `experience`, `yearsInCurrentRole`, `techStack`, `industries`, `preferredLanguage`).
- Feign client: `client/userprofile/UserProfileClient.java`.
- DTO: `client/userprofile/dto/UserProfileResponse.java`.

### 6.2 QuestionBankAdapter
**File**: `services/interview-service/src/main/java/com/mockwise/interview/client/questionbank/QuestionBankAdapter.java`

| Method | Dòng | Hard/Soft fail | Endpoint phía bank |
|---|---|---|---|
| `filter(req)` | `:38-40` | Hard (throw) | `POST /questions/filter` |
| `getSnapshotSoft(id)` | `:48-55` | Soft (Optional.empty) | `GET /questions/{id}/snapshot` |
| `findFollowUps(...)` | `:57-62` | Hard (throw) | `GET /questions/{id}/follow-ups` |
| `markAskedSoft(id)` | `:69-76` | Soft | `POST /internal/questions/{id}/mark-asked` |

Feign client: `client/questionbank/QuestionBankClient.java`
DTOs (mirror question-bank): `client/questionbank/dto/{QuestionFilterRequest, QuestionFilterResponse, QuestionCandidate, QuestionSnapshotResponse, MarkAskedResponse, FollowUpResponse}.java`

### 6.3 StorageAdapter
**File**: `services/interview-service/src/main/java/com/mockwise/interview/client/storage/StorageAdapter.java`
- `signQuestionAudioUrl(audioKey)` → presigned GET URL (soft-fail: empty Optional).
- Dùng ở `SessionService.signAudio` (`:208-211`).

---

## 7. Phía question-bank (downstream của `/filter`)

> Đây là service riêng — chỉ gọi qua HTTP. Đọc cả luồng SQL nếu cần debug pool size.

**Service**: `services/question-bank-service/src/main/java/com/mockwise/questionbank/service/QuestionSelectionService.java:46-85`
Method: `filter(QuestionFilterRequest req)`

- Validate (`:122-136`):
  - `BEHAVIORAL` cần `competency`
  - `CORE_CONCEPTUAL` cần `domain`
- Convert `tagsAny` & `excludeIds` thành Postgres `text[]` literal qua `toPgTextArray` (`:145-163`).
  - **Quan trọng**: `tagsAny` rỗng → null (không phải `"{}"`) để tránh `{} && q.tags = FALSE` (fix ở commit `90477c8`).
  - `excludeIds` rỗng → `"{}"` (an toàn với `NOT ANY`).
- Dispatch theo `type`:
  - `BEHAVIORAL` → `BehavioralQuestionRepository.findCandidates(...)`
  - `CORE_CONCEPTUAL` → `CoreQuestionRepository.findCandidates(...)`
- Map sang `QuestionCandidateResponse` (`:165-193`).

### 7.1 SQL — BehavioralQuestionRepository.findCandidates
**File**: `services/question-bank-service/src/main/java/com/mockwise/questionbank/repository/BehavioralQuestionRepository.java:49-71`

```sql
SELECT bq.* FROM behavioral_questions bq
JOIN questions q ON q.id = bq.id
WHERE q.status = 'ACTIVE'
  AND (:competency  IS NULL OR bq.competency = :competency)
  AND (:difficulty  IS NULL OR q.difficulty  = :difficulty)
  AND (:requireOpener = FALSE OR bq.is_opener = TRUE)
  AND (:tags         IS NULL OR q.tags && CAST(:tags AS text[]))
  AND NOT (q.id = ANY(CAST(:excludeIds AS text[])))
ORDER BY q.ask_count ASC, q.created_at DESC
LIMIT :limit
```

### 7.2 SQL — CoreQuestionRepository.findCandidates
**File**: `services/question-bank-service/src/main/java/com/mockwise/questionbank/repository/CoreQuestionRepository.java:49-73`

```sql
SELECT cq.* FROM core_questions cq
JOIN questions q ON q.id = cq.id
WHERE q.status = 'ACTIVE'
  AND (:domain      IS NULL OR cq.domain = :domain)
  AND (:targetRole  IS NULL OR CAST(:targetRole AS text) = ANY(cq.target_roles))
  AND (:difficulty  IS NULL OR q.difficulty = :difficulty)
  AND (:requireOpener = FALSE OR cq.is_opener = TRUE)
  AND (:tags         IS NULL OR q.tags && CAST(:tags AS text[]))
  AND NOT (q.id = ANY(CAST(:excludeIds AS text[])))
ORDER BY q.ask_count ASC, q.created_at DESC
LIMIT :limit
```

### 7.3 markAsked
**File**: `services/question-bank-service/src/main/java/com/mockwise/questionbank/service/QuestionSelectionService.java:87-98`
- Update `questions.ask_count = ask_count+1, last_asked_at = now()`.
- `QuestionRepository.markAsked(id)` (native UPDATE).

---

## 8. Bảng DB bị ghi/đọc

| Bảng | Service | Khi nào |
|---|---|---|
| `interview_session` | interview | INSERT (bước 6) |
| `session_topic_state` | interview | INSERT N rows (bước 7), UPDATE 1 row (bước 11) |
| `session_question` | interview | INSERT 1 row (`QuestionPicker.pickForTopic` :106) |
| `interview_blueprint` | interview | SELECT (bước 4) |
| `behavioral_questions` + `questions` | question-bank | SELECT (filter), UPDATE (mark-asked) |
| `core_questions` + `questions` | question-bank | SELECT (filter), UPDATE (mark-asked) |

Toàn bộ INSERT/UPDATE phía interview nằm trong **1 transaction** (`@Transactional` ở `SessionService.start`). Lỗi giữa chừng → rollback sạch.

> Lưu ý: `markAsked` phía question-bank là **transaction riêng** (gọi qua HTTP, không tham gia Tx của interview). `markAskedSoft` swallow exception → ngay cả khi `session_question` đã commit, ask_count có thể không tăng.

---

## 9. Output trả về FE

**File**: `services/interview-service/src/main/java/com/mockwise/interview/dto/response/StartSessionOutput.java`
**File**: `services/interview-service/src/main/java/com/mockwise/interview/dto/response/PinnedQuestionView.java`

Built ở `SessionService:135-144`. Bao gồm:
- `sessionId`, `targetRole`, `level`, `interviewType`, `questionCount`, `timeBudgetMinutes`
- `firstQuestion: PinnedQuestionView` — đã sign audio URL qua `signAudio` (`:208-211`)

---

## 10. Cheat sheet path tuyệt đối

```
# Interview service — flow chính
services/interview-service/src/main/java/com/mockwise/interview/controller/SessionController.java
services/interview-service/src/main/java/com/mockwise/interview/service/SessionService.java
services/interview-service/src/main/java/com/mockwise/interview/service/BlueprintLoader.java
services/interview-service/src/main/java/com/mockwise/interview/service/QuestionPicker.java

# Helpers
services/interview-service/src/main/java/com/mockwise/interview/common/util/BlueprintNormalizer.java

# DTOs
services/interview-service/src/main/java/com/mockwise/interview/dto/request/StartSessionInput.java
services/interview-service/src/main/java/com/mockwise/interview/dto/response/StartSessionOutput.java
services/interview-service/src/main/java/com/mockwise/interview/dto/response/PinnedQuestionView.java

# Entities
services/interview-service/src/main/java/com/mockwise/interview/entity/InterviewSession.java
services/interview-service/src/main/java/com/mockwise/interview/entity/InterviewBlueprint.java
services/interview-service/src/main/java/com/mockwise/interview/entity/BlueprintTopic.java
services/interview-service/src/main/java/com/mockwise/interview/entity/SessionTopicState.java
services/interview-service/src/main/java/com/mockwise/interview/entity/SessionQuestion.java

# Adapters / Feign
services/interview-service/src/main/java/com/mockwise/interview/client/userprofile/UserProfileAdapter.java
services/interview-service/src/main/java/com/mockwise/interview/client/questionbank/QuestionBankAdapter.java
services/interview-service/src/main/java/com/mockwise/interview/client/questionbank/QuestionBankClient.java
services/interview-service/src/main/java/com/mockwise/interview/client/storage/StorageAdapter.java

# Question-bank phía nhận /filter
services/question-bank-service/src/main/java/com/mockwise/questionbank/service/QuestionSelectionService.java
services/question-bank-service/src/main/java/com/mockwise/questionbank/repository/BehavioralQuestionRepository.java
services/question-bank-service/src/main/java/com/mockwise/questionbank/repository/CoreQuestionRepository.java

# Blueprint seeds đang chạy
services/interview-service/src/main/resources/db/migration/V2__seed_default_blueprints.sql
services/interview-service/src/main/resources/db/migration/V3__seed_behavioral_and_core_blueprints.sql
```

---

## 11. Điểm quan trọng để debug "câu hỏi lặp"

1. **3 vòng deterministic**: SQL `ORDER BY ask_count ASC, created_at DESC` + local scoring không random + `Stream.max` lấy phần tử đầu tiên trên tie → cùng input ra cùng output.
2. **`requireOpener=true` ở câu đầu** → SQL ép `is_opener=TRUE` → pool tier-1 thường rất nhỏ.
3. **Opener bonus +10 trong `applyLocalScoring`** là no-op ở tier 1 (mọi candidate đều opener nên cộng đồng đều).
4. **`ask_count` penalty `/100`** quá yếu — cần 100 lần hỏi mới giảm 1 điểm.
5. **`excludeIds` rỗng ở `/start`** — không có cross-session anti-repeat. `AnswerService.handleMoveNext` chỉ build excludeIds **trong cùng session**.
6. **Blueprint `targetDifficulty` đồng nhất + `questionBudget` thấp + 1 câu/topic** → pool key cố định, ít cơ hội rotate.
7. **`markAskedSoft` fail âm thầm** → ask_count không bao giờ tăng → kể cả penalty `/100` cũng vô nghĩa. Check log VPS với `grep "mark-asked failed"`.
