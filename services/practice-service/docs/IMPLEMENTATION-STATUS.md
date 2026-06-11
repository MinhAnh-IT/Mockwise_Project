# Practice Service — Implementation Status

> **Mục đích file này:** nhật ký tiến độ để bất kỳ session Claude nào (hoặc người) mở ra
> là biết ngay **đã làm xong gì**, **đang ở đâu**, **làm tiếp gì** mà không phải prompt lại từ đầu.
> Cập nhật file này mỗi khi hoàn thành một mảng việc.
>
> Thiết kế tổng thể: xem [`practice-service-design.md`](./practice-service-design.md).
> Branch: `feat/practice-leetcode` (tách từ `main`).

Cập nhật lần cuối: **2026-06-11** — **HOÀN TẤT** backend + frontend + CI/CD. DB `practice` đã tạo trên VPS. Sẵn sàng merge→deploy.

---

## Tóm tắt 1 dòng

LeetCode-style practice = **service mới `practice-service`**, tái dùng `judge-service` (chạy code) +
`question-bank-service` (kho đề). Gửi code sang judge qua **Kafka async**, phân biệt luồng bằng field
`origin=PRACTICE`. Bản đầu làm đầy đủ: **history + problem status (solved/attempted) + stats**.

Quyết định kiến trúc đã chốt (không cần hỏi lại):
1. Service mới riêng, KHÔNG nhồi vào interview/question-bank.
2. Kafka async + `origin` filter (fallback: lookup-by-submissionId nếu không sửa judge).
3. Phạm vi đầy đủ history + status + stats.
4. Practice là ranh giới user DUY NHẤT tự strip hidden testcases; question-bank chỉ phơi endpoint **internal**.

---

## Bảng tiến độ theo Phase

| Phase | Nội dung | Trạng thái |
|---|---|---|
| **0** | Catalog facade (duyệt đề, mở đề) | ✅ **DONE** (compile sạch) |
| **1** | Run (chạy thử sample) + judge integration (`origin`) | ✅ **DONE** |
| **2** | DB + Flyway + entities; Submit (full testcases) + history + problem_status | ✅ **DONE** |
| **3** | Stats tổng hợp; gắn `myStatus` thật vào list | ✅ **DONE** (`acceptanceRate` global per-đề = future work) |
| **4** | docker-compose + gateway route + `.env.example` + watchdog + **Frontend** + **CI/CD** | ✅ **DONE** |

> **Toàn bộ chạy end-to-end** (compile sạch: judge, interview, question-bank, practice; FE `tsc` + `vite build` sạch).
> Chuỗi: nginx `/api/` → api-gateway → route `/api/v1/practice/**` → practice-service:8089 →
> (Feign) question-bank `/internal/coding-problems` + (Kafka `origin=PRACTICE`) judge.
> **DB `practice` đã được tạo trên VPS** (postgres `mockwise-infra-postgres-1`, owner mockwise).
> **CI/CD đã thêm practice-service** vào ci.yml + cd.yml (detect/filter/build/deploy). Push lên `main` là build+deploy.

---

## ✅ Phase 0 — Catalog facade (ĐÃ XONG)

**Verify:** `cd services && mvn -pl question-bank-service,practice-service compile` → BUILD SUCCESS.
Chưa commit (chờ user yêu cầu). Service Phase 0 chạy được KHÔNG cần DB (đã `exclude` DataSource autoconfig).

### question-bank-service (đã thêm)
- `repository/CodingQuestionRepository.java` → method `findActiveProblems(difficulty, tags, q, pageable)`
  (ACTIVE-only + filter difficulty/tags + search tiêu đề ILIKE, phân trang).
- `dto/response/CodingProblemSummary.java` → row gọn cho list (KHÔNG kèm testcases).
- `service/CodingCatalogService.java` → `listProblems(...)` (ACTIVE-only) + `getDetail(id)`
  (404 nếu không tồn tại / không LIVE_CODING / không ACTIVE). Detail trả **FULL** incl hidden.
- `controller/InternalCodingProblemController.java` → mount `/internal/coding-problems`:
  - `GET /internal/coding-problems` (list, params: difficulty, tags, q, page, size)
  - `GET /internal/coding-problems/{id}` (full detail incl hidden testcases)

### practice-service (service MỚI, package `com.mockwise.practice`, port **8089**, context-path `/api/v1/practice`)
- Build/skeleton: `pom.xml` (đã đăng ký module vào `services/pom.xml`), `Dockerfile`,
  `PracticeApplication.java` (tạm `exclude = {DataSourceAutoConfiguration, HibernateJpaAutoConfiguration}`
  — **Phase 2 BỎ exclude này** khi thêm DB), `application.yml`, `application-local.yml`.
- Security: `common/security/SecurityConfig.java` + `UserContextFilter` (đọc `X-User-Id/Role/Email` từ
  gateway) + `InternalAuthFilter` (`X-Internal-Auth`) + `CustomUserDetails` + `CurrentUser`
  (`requireUserId()` lấy từ token, KHÔNG nhận từ body). `common/config/InternalAuthProperties`.
- Exception: `common/exception/{StatusCode, BusinessException, GlobalException}` (GlobalException map
  `FeignException` → 502 QUESTION_BANK_UNAVAILABLE).
- Feign: `client/questionbank/QuestionBankClient` (gọi 2 endpoint internal) +
  `config/QuestionBankFeignConfig` (inject `X-Internal-Auth`, NEVER_RETRY, `ErrorDecoder` dịch 404→
  `PROBLEM_NOT_FOUND`) + client DTOs `QbPage / QbProblemSummary / QbCodingDetail`.
- Facade: `service/PracticeCatalogService` (unwrap ApiResponse + **strip hidden cases** + map sang
  `CodingProblemView`), `controller/PracticeCatalogController`:
  - `GET /api/v1/practice/problems` (list, filter difficulty/tags/q/page/size) → `PageResponse<ProblemSummary>`
  - `GET /api/v1/practice/problems/{id}` → `CodingProblemView` (chỉ sample/non-hidden cases)
  - `controller/HealthController` → `GET /health` (permitAll).
- DTO user: `dto/response/{ProblemSummary, CodingProblemView, PageResponse}`, `enums/ProblemStatus`.

### Hạ tầng đã đụng
- `services/pom.xml`: thêm `<module>practice-service</module>`.
- 8 Dockerfile khác (api-gateway, iam, interview, mail, question-bank, storage, tts-stt, user-profile):
  thêm dòng `COPY practice-service/pom.xml practice-service/pom.xml` (reactor cần, nếu không `mvn -pl`
  của các service đó sẽ lỗi "child module does not exist"). judge-service KHÔNG đụng (build standalone,
  không nằm trong parent pom).

### Khác biệt có chủ đích so với design doc
Doc mô tả user-facing `/api/v1/coding-problems` + internal `/full`. Khi code đã **gộp thành 2 endpoint
internal** ở question-bank; practice-service là nơi duy nhất strip hidden. An toàn hơn (full payload có
hidden không bao giờ reachable qua gateway cho user). *(Doc design chưa sync lại điểm này.)*

### Nợ kỹ thuật / lưu ý cho Phase sau
- `ProblemSummary.acceptanceRate` = null, `myStatus` = `NONE` (placeholder) → Phase 3 điền thật.
- `PracticeCatalogService.listProblems/getProblem` đã nhận `userId` (chưa dùng) → sẵn cho Phase 3 join status.
- question-bank `getForJudge(id)` (đã có sẵn) trả full testcases + functionMeta nhưng **hardcode
  `language="java"`** → Phase 1/2 khi build Submit phải bỏ qua field language đó, dùng ngôn ngữ user chọn.
  (Hoặc practice tự lấy full từ `/internal/coding-problems/{id}` đã implement.)

---

## ✅ Phase 1 — Run + judge integration (XONG)

**judge-service** (`origin` xuyên suốt, backward-compatible):
- `dto/SubmissionEvent` + `dto/JudgeResultEvent`: thêm `String origin` (nullable).
- `entity/JudgeJob`: cột `origin`; `mapper/JudgeMapper`: auto-map vào job + echo `job.origin` ra result event.
- Flyway `V2__add_origin_to_judge_jobs.sql` (MySQL, `origin VARCHAR(20) NULL`). origin null ⇒ INTERVIEW.

**interview-service** (BẮT BUỘC — tránh poison-loop): giờ practice cũng publish `submission-judged`.
- `message/event/SubmissionJudgedEvent`: thêm `origin`.
- `JudgeResultConsumer`: nếu `origin != null && != INTERVIEW` → **ack + skip** (nếu không, practice verdict
  sẽ rơi vào `applyCodeJudged` → `ANSWER_NOT_FOUND` → throw → redeliver vô hạn = poison, đúng bài học
  `project_prod_disk_full_judge_logs`).

**practice-service**: producer `CodeSubmissionProducer` (`code-submission`, await có timeout → fail rõ ràng),
consumer `SubmissionJudgedConsumer` (group `practice-service`, lọc `origin=PRACTICE`, manual-ack, idempotent),
`KafkaConfig` (JsonSerializer no type-info + String consumer manual-ack — mirror interview). `POST /problems/{id}/run`.

## ✅ Phase 2 — Submit + history + status (XONG)
- Flyway `V1__init_practice.sql` (**PostgreSQL**, KHÔNG phải MySQL): `practice_submission`
  (+ `problem_title`, `difficulty`, `hidden_case_ids` JSONB denormalized), `practice_submission_case`
  (cờ `hidden`; stdout/stderr null cho hidden), `practice_problem_status` (+ `difficulty`).
- Entities + repositories; bỏ `exclude` DataSource; datasource/jpa/flyway vào `application.yml`.
- `POST /problems/{id}/submit` (full testcases từ `/internal/coding-problems/{id}`). `applyJudged` idempotent
  theo trạng thái submission (DONE/FAILED → no-op), upsert `problem_status` (lần đầu ATTEMPTED, AC→SOLVED,
  giữ best_runtime). `GET /submissions` (filter problemId/mode/verdict), `GET /submissions/{id}` (per-case,
  ẩn I/O hidden). **Verdict judge = AC|WA|TLE|MLE|RE|CE** (AC = solved) — lưu raw code, FE tự map.

## ✅ Phase 3 — Stats (XONG)
- `GET /stats`: `solvedTotal`, `solvedByDifficulty{EASY,MEDIUM,HARD}`, `attemptedTotal`,
  `acceptanceRate` (= accepted SUBMIT / total SUBMIT, null nếu chưa submit), `currentStreakDays`,
  `longestStreakDays` (tính on-read từ ngày có AC submit; không có bảng rollup).
- `ProblemSummary.myStatus` join thật `practice_problem_status` theo userId (catalog list).
- `acceptanceRate` **global per-đề** vẫn = null (future work — cần đếm ngược về question-bank).

## ✅ Phase 4 — Vận hành + Frontend + CI/CD (XONG)
**Hạ tầng:**
- `docker/docker-compose.prod.yml`: service `practice-service` (port 8089, internal-net, env
  `SPRING_PROFILES_ACTIVE=prod`, datasource `…/practice`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`,
  `INTERNAL_API_KEY`, `QUESTION_BANK_BASE_URL`); gateway thêm `PRACTICE_SERVICE_URL` + depends_on.
- api-gateway `application.yml`: route `/api/v1/practice/**` (đặt TRƯỚC catch-all `/api/v1/**`) + probe monitoring "Practice".
- `.env.example`: ghi chú cần tạo DB `practice`.
- Watchdog `SubmissionWatchdog` (@Scheduled): submission kẹt `JUDGING` quá `practice.watchdog.timeout-seconds`
  (mặc định 120s) → `FAILED`. Verdict tới trễ vẫn an toàn (applyJudged no-op trên terminal).

**Frontend (XONG)** — tên FE là **"Luyện đề" / `/problems`** (tách khỏi `/practice` = mock-interview):
- `types/practice.ts` + `api/practice.ts` (gọi `/api/v1/practice/**`, enveloped → `unwrap`).
- `pages/problems/ProblemListPage.tsx` (filter độ khó + search debounce + cờ myStatus + phân trang).
- `components/problems/PracticeCodingWorkspace.tsx` — **tái dùng** CodeEditor/TestcasePanel/Markdown/theme của
  interview coding; Run/Submit → practice API, poll `GET /submissions/{id}`, banner verdict cho Submit.
  `pages/problems/ProblemWorkspacePage.tsx` bọc nó.
- `pages/problems/SubmissionsPage.tsx` (history + expand chi tiết per-case, hidden có 🔒, xem source).
  `pages/problems/StatsPage.tsx` (solved/độ khó/acceptance/streak).
- Routes thêm vào `App.tsx` (static `/problems/submissions|stats` trước `/problems/:id`); nav "Luyện đề" trong
  `data/navigation.ts`. `npm run lint` (tsc) + `npm run build` (vite) đều sạch.

**CI/CD (XONG):** `.github/workflows/ci.yml` + `cd.yml` đã thêm `practice-service`
(detect-changes output + paths-filter + build job + deploy needs + compute-list). Push `main` ⇒ build image
`ghcr.io/.../practice-service` + `docker compose up -d --no-deps practice-service`.

**Đã chạy trên VPS:** `CREATE DATABASE practice OWNER mockwise` trên `mockwise-infra-postgres-1`.

**Deploy lưu ý (memory):** `--env-file` khi recreate, **restart api-gateway** sau khi tạo container mới
(tránh stale-IP 502 — lần này api-gateway tự rebuild vì route yaml đổi), grep file thực thi tránh rò `</content>`.

---

## Lệnh hữu ích
```bash
cd services
# compile riêng phần practice
mvn -pl question-bank-service,practice-service compile
# build jar
mvn -pl practice-service -am package -DskipTests
```
