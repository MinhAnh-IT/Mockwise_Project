# Handoff — Giao diện quản lý Blueprint phỏng vấn (Admin)

> Trạng thái: **THIẾT KẾ — chưa code FE**. Backend đã xong (controller + service +
> migration seed). Tài liệu này ghi lại toàn bộ những gì đã đọc trong codebase và
> chính xác những việc cần làm để dựng màn hình quản lý blueprint cho admin, theo
> đúng pattern của console "Ngân hàng câu hỏi" đã có.

---

## 1. Blueprint là gì?

`InterviewBlueprint` = **mẫu (template) cấu hình một buổi phỏng vấn**, khoá duy
nhất theo bộ ba `(targetRole, level, interviewType)`. Khi user bấm `/start`,
interview-service tra blueprint khớp track/level/loại để biết:

- Hỏi những **chủ đề (topics)** nào, mức quan trọng & độ khó mong muốn ra sao.
- Ngân sách câu hỏi / thời gian.
- Số follow-up tối đa mỗi topic và mỗi phiên.
- Có dùng AI selector chọn câu kế tiếp hay không.
- Đâu là blueprint **mặc định** cho bộ ba đó (mỗi `(role, level, type)` chỉ
  được có **đúng 1** default — ràng buộc partial-unique ở DB).

Hiện admin chỉ có thể thêm/sửa blueprint qua migration SQL. Mục tiêu: dựng UI
để admin tự CRUD + đặt mặc định, giống console ngân hàng câu hỏi.

---

## 2. Hợp đồng Backend (interview-service) — ĐÃ CÓ SẴN

### 2.1 Đường dẫn

- interview-service `server.servlet.context-path = /api/v1/interviews`.
- Controller: `AdminBlueprintController` `@RequestMapping("/admin/blueprints")`.
- **Base path đầy đủ qua gateway:** `/api/v1/interviews/admin/blueprints`.
- Gateway tự ép `ROLE_ADMIN` cho mọi path chứa `/admin/` (không cần
  `@PreAuthorize`). FE chỉ cần `AdminRoute` làm UX guard như các trang admin
  khác.

### 2.2 Bảng endpoint

| Method | Path | Body | Trả về |
|---|---|---|---|
| `GET` | `/admin/blueprints?targetRole&level&interviewType&isDefault&page&size` | — | `ApiResponse<ApiListResponse<BlueprintAdminResponse>>` → **có bọc** envelope. `unwrap` rồi đọc `{ totalCount, items }` |
| `POST` | `/admin/blueprints` | `BlueprintCreateRequest` | `201` `ApiResponse<BlueprintAdminResponse>` → `unwrap` |
| `GET` | `/admin/blueprints/{id}` | — | `ApiResponse<BlueprintAdminResponse>` → `unwrap` |
| `PUT` | `/admin/blueprints/{id}` | `BlueprintUpdateRequest` | `ApiResponse<BlueprintAdminResponse>` → `unwrap` |
| `PATCH` | `/admin/blueprints/{id}/default` | `{ "isDefault": boolean }` | `ApiResponse<BlueprintAdminResponse>` → `unwrap` |
| `DELETE` | `/admin/blueprints/{id}` | — | `204` `ApiResponse(null)` → fire-and-forget `request` |

> ⚠️ **Khác biệt so với question-bank:** list của QB trả **bare**
> `{totalCount, items}` (dùng `request`). List blueprint **CÓ bọc**
> `ApiResponse` → phải dùng `unwrap`, kết quả là `ApiListResponse`
> (`{ totalCount: number, items: [] }` — xác nhận shape ở
> `src/types/interview.ts` & doc QB handoff). `page` mặc định `0`, `size`
> mặc định `20`. Sort cố định phía server theo `targetRole, level,
> interviewType` (FE không cần gửi sort).

### 2.3 Shape request/response

`BlueprintCreateRequest` và `BlueprintUpdateRequest` **giống hệt nhau**:

```jsonc
{
  "targetRole": "BACKEND",        // bắt buộc, ≤20 ký tự
  "level": "mid",                 // bắt buộc, ≤20 ký tự
  "interviewType": "BEHAVIORAL",  // bắt buộc: BEHAVIORAL | CORE | CODING
  "topics": [ /* BlueprintTopicDto[], KHÔNG rỗng */ ],
  "questionBudget": 8,            // tuỳ chọn, ≥1, default 8
  "timeBudgetMinutes": 45,        // tuỳ chọn, ≥1, default 45
  "maxFollowUpsPerTopic": 2,      // tuỳ chọn, ≥0, default 2
  "maxFollowUpsPerSession": 4,    // tuỳ chọn, ≥0, default 4
  "useAiSelector": false,         // tuỳ chọn, default false
  "isDefault": false              // tuỳ chọn, default false
}
```

`BlueprintTopicDto`:

```jsonc
{
  "kind": "COMPETENCY",          // bắt buộc (@NotNull): COMPETENCY | DOMAIN
  "topicValue": "OWNERSHIP",     // bắt buộc (@NotBlank), ≤50 ký tự
  "importance": "MED",           // bắt buộc: HIGH | MED | LOW
  "targetDifficulty": "EASY",    // bắt buộc: EASY | MEDIUM | HARD
  "orderHint": 1                 // ≥0
}
```

`BlueprintAdminResponse` (trả về):

```jsonc
{
  "id": "uuid",
  "targetRole": "BACKEND",       // ĐÃ normalize (uppercase + alias)
  "level": "mid",                // ĐÃ normalize (lowercase + alias)
  "interviewType": "BEHAVIORAL",
  "topics": [ /* BlueprintTopicDto[], topicValue đã UPPERCASE */ ],
  "questionBudget": 8,
  "timeBudgetMinutes": 45,
  "maxFollowUpsPerTopic": 2,
  "maxFollowUpsPerSession": 4,
  "useAiSelector": false,
  "isDefault": true,
  "createdAt": "2026-05-16T10:00:00Z",   // OffsetDateTime
  "updatedAt": "2026-05-16T10:00:00Z"
}
```

### 2.4 Mã lỗi (StatusCode)

| Code | HTTP | Khi nào | Thông điệp gợi ý hiển thị |
|---|---|---|---|
| `4042` | 404 | `BLUEPRINT_NOT_FOUND` — id không tồn tại | "Không tìm thấy blueprint." |
| `4002` | 400 | `BLUEPRINT_NO_TOPICS` — topics rỗng | "Blueprint cần ít nhất một chủ đề." |
| `4096` | 409 | `BLUEPRINT_DUPLICATE` — trùng default cho bộ ba | "Đã tồn tại blueprint mặc định cho (vị trí, cấp, loại) này." |
| `4097` | 409 | `BLUEPRINT_IN_USE` — còn session tham chiếu, không xoá được | "Không thể xoá: vẫn còn phiên phỏng vấn dùng blueprint này." |

Client (`src/api/client.ts`) parse `body.message` & `body.code` vào
`ApiError` → FE chỉ cần bắt `ApiError` và hiển thị `err.message`, hoặc map
theo `err.code` nếu muốn thông điệp tiếng Việt đẹp hơn.

---

## 3. Quy tắc nghiệp vụ phải nắm (đọc từ `BlueprintAdminService`)

1. **Default là partial-unique theo `(targetRole, level, interviewType)`.**
   Khi tạo/sửa với `isDefault=true`, server tự `clearOtherDefaults(...)` —
   gỡ cờ default của các blueprint anh em cùng bộ ba **trước** khi set cờ cho
   bản ghi hiện tại. → FE **không** cần tự bỏ default bản khác; chỉ cần phản
   ánh lại danh sách sau khi server trả về (refetch hoặc cập nhật optimistic
   rồi reconcile).

2. **Normalize phía server (FE không kiểm soát chính tả cuối cùng):**
   - `targetRole`: alias-table + uppercase + bỏ ký tự lạ
     (`"Back-End"` → `BACKEND`, `"Mobile / iOS"` → `MOBILE_IOS`).
   - `level`: alias-table + lowercase (`"Senior"`/`"Lead"`/`"Staff"` →
     `senior`; `"Intern"`/`"Fresher"` → `junior`; `"Middle"` → `mid`).
   - `topicValue`: `.trim().toUpperCase()`.
   → Response có thể **khác** input về hoa/thường. FE đừng so sánh chuỗi thô;
   hiển thị theo giá trị server trả về. Nên cho admin chọn từ **dropdown
   token chuẩn** thay vì gõ tay để tránh tạo blueprint "mồ côi" không match
   lúc `/start`.

3. **`topicValue` phải khớp byte-for-byte enum của question-bank:**
   - `kind=COMPETENCY` → giá trị thuộc enum `Competency`
     (`CONFLICT_RESOLUTION`, `LEADERSHIP`, `OWNERSHIP`, `TEAMWORK`,
     `FAILURE`, `GROWTH`, `COMMUNICATION`, `PRIORITIZATION`).
   - `kind=DOMAIN` → giá trị thuộc enum `Domain` (`OS`, `NETWORKING`,
     `DATABASE`, `SYSTEM_DESIGN`, ... — xem `src/types/questionBank.ts`).
   → FE **tái dùng** `COMPETENCIES/COMPETENCY_LABEL` và
   `DOMAINS/DOMAIN_LABEL` từ `src/types/questionBank.ts` cho dropdown topic.

4. **Xoá bị chặn nếu blueprint đang được session tham chiếu** (`4097`).
   FE phải xử lý nhánh lỗi này tử tế (toast đỏ + giữ nguyên hàng), không chỉ
   "Xoá thất bại" chung chung.

5. **CODING khác hẳn BEHAVIORAL/CORE** (đọc `V5__seed_coding_blueprints.sql`):
   - CODING **không adaptive**: không có ma trận topic, không planner, không
     follow-up. `topics` chỉ là **kế hoạch độ khó có thứ tự** — mỗi phần tử
     là một "slot" 1 câu, picker đọc `targetDifficulty` theo `orderHint` và
     lấy đúng số câu `LIVE_CODING` từ question-bank.
   - `questionBudget` = **số slot**; `maxFollowUpsPerTopic` =
     `maxFollowUpsPerSession` = `0`.
   - Seed SQL **bỏ trống** `kind`/`topicValue` cho CODING. **NHƯNG** API admin
     đi qua `@Valid BlueprintTopicDto` có `@NotNull kind` + `@NotBlank
     topicValue` → nếu FE gửi slot CODING thiếu 2 field này sẽ **400**.

   > 🔴 **Rủi ro/quyết định cần chốt:** với CODING tạo qua API admin, FE bắt
   > buộc phải bơm placeholder để qua validation. Đề xuất: gửi
   > `kind="DOMAIN"`, `topicValue="CODING"` cho mọi slot (picker CODING bỏ
   > qua 2 field này nên vô hại về nghiệp vụ). Phương án thay thế: xin BE nới
   > lỏng validation cho `interviewType=CODING` (ghi vào mục "Câu hỏi mở"
   > cuối tài liệu). **Tài liệu này mặc định dùng placeholder.**

6. `interviewType` chỉ còn **3 giá trị**: `BEHAVIORAL | CORE | CODING`.
   `MIXED` đã bị gỡ khỏi enum (commit `6dc78ea`) — seed V2/V3 còn chữ `MIXED`
   là dữ liệu cũ, **FE tuyệt đối không** thêm lại `MIXED` vào dropdown.

---

## 4. Hiện trạng Frontend đã đọc (pattern phải bám theo)

- **Routing:** `src/App.tsx` — `react-router-dom`, mọi trang admin bọc
  `<AdminRoute>`. Pattern route: `/admin`, `/admin/questions`,
  `/admin/questions/new/:kind`, `/admin/questions/:kind/:id/edit`.
- **Shell + UI dùng chung:** `src/components/admin/ui.tsx` export sẵn:
  `AdminShell` (sidebar + header + breadcrumb + actions), `Button`,
  `Field`, `Input`, `Textarea`, `Select`, `EnumSelect<T>`,
  `ChipMultiSelect<T>`, `TagInput`, `Pill`, `DifficultyPill`, `StatusPill`,
  `ConfirmDialog`, `Toast`/`ToastState`, `Spinner`, `ErrorState`,
  `EmptyState`. **Tái dùng tối đa, không tạo primitive mới trùng lặp.**
- **Nav sidebar admin:** hằng `ADMIN_NAV` trong `ui.tsx`
  (`/admin` = Tổng quan, `/admin/questions` = Ngân hàng câu hỏi). Thêm mục
  blueprint vào đây.
- **API client:** `src/api/client.ts` — `request<T>` (trả raw JSON, dùng cho
  list bare & DELETE 204) và `unwrap<T>` (bóc `ApiEnvelope.data`). Tự refresh
  token + đính `Authorization`. `ApiError { message, status, code }`.
- **Trang list mẫu:** `src/pages/admin/AdminQuestionsPage.tsx` — tabs + bộ
  lọc trong card + nút "Áp dụng/Xoá lọc" + "Tải thêm" cộng dồn
  (`hasNext = items.length < totalCount`) + `ConfirmDialog` xoá + `Toast` +
  nhận `location.state.flash` từ form page.
- **Trang form mẫu:** `src/pages/admin/BehavioralFormPage.tsx` — 1 component
  dùng chung create/edit (`mode = id ? 'edit' : 'create'`), prefill từ
  `location.state.record` hoặc refetch theo `id`, `validate()` cục bộ, submit
  xong `navigate('/admin/...', { state: { flash } })`.
- **Dashboard:** `src/pages/admin/AdminDashboardPage.tsx` — hero band + card
  thống kê mỗi loại + quick actions.
- **Nhãn tiếng Việt + enum tái dùng:** `src/types/questionBank.ts` đã có
  `COMPETENCIES/COMPETENCY_LABEL`, `DOMAINS/DOMAIN_LABEL`,
  `DIFFICULTIES/DIFFICULTY_LABEL`, `TARGET_ROLES/TARGET_ROLE_LABEL`,
  `Difficulty`. Blueprint UI **import lại** các map này.

---

## 5. Thiết kế UI

### 5.1 Sitemap / route mới (thêm vào `App.tsx`)

```
/admin/blueprints                 → BlueprintsPage    (list + filter + default + delete)
/admin/blueprints/new             → BlueprintFormPage (create)
/admin/blueprints/:id/edit        → BlueprintFormPage (edit)
```

Tất cả bọc `<AdminRoute>` y như route `/admin/questions`.

### 5.2 Wireframe — Trang danh sách `/admin/blueprints`

```
┌── AdminShell ───────────────────────────────────────────────────────────┐
│ [≡]  Tổng quan / Blueprint phỏng vấn                  [ + Tạo blueprint ] │
│      Quản lý mẫu cấu hình buổi phỏng vấn theo vị trí · cấp · loại        │
├──────────────────────────────────────────────────────────────────────────┤
│ ┌ Bộ lọc ───────────────────────────────────────────────────────────┐   │
│ │ [Vị trí ▾] [Cấp ▾] [Loại PV ▾] [Mặc định ▾]                        │   │
│ │ [ Áp dụng lọc ] [ Xoá lọc ]                       12 blueprint      │   │
│ └────────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│ ┌ Card blueprint ───────────────────────────────────────────────────┐   │
│ │ BACKEND · mid · ⟨Hành vi⟩                       ★ Mặc định         │   │
│ │ 6 chủ đề · 8 câu · 45 phút · follow-up 2/topic · 4/phiên · AI: tắt │   │
│ │ [Giao tiếp·HIGH·TB] [Làm việc nhóm·MED·Dễ] [+4 chủ đề]            │   │
│ │ Cập nhật 16/05/2026 10:00          [★ Đặt mặc định] [✎] [🗑]      │   │
│ └────────────────────────────────────────────────────────────────────┘   │
│ ┌────────────────────────────────────────────────────────────────────┐   │
│ │ BACKEND · mid · ⟨Lập trình⟩                                        │   │
│ │ 4 slot · ramp Dễ→TB→TB→Khó · 110 phút                              │   │
│ │ Cập nhật …                          [★ Đặt mặc định] [✎] [🗑]      │   │
│ └────────────────────────────────────────────────────────────────────┘   │
│                          [ Tải thêm ]                                     │
└──────────────────────────────────────────────────────────────────────────┘
```

Hành vi:
- Bộ lọc: `targetRole` (EnumSelect từ `TARGET_ROLES`), `level`
  (Select: `junior|mid|senior`), `interviewType` (EnumSelect 3 giá trị),
  `isDefault` (Select: Tất cả / Chỉ mặc định / Chưa mặc định → map sang
  `true|false|undefined`). Nút "Áp dụng/Xoá lọc" + tổng số bên phải.
- "Tải thêm" cộng dồn: `hasNext = items.length < totalCount`.
- "★ Đặt mặc định": gọi `PATCH /{id}/default {isDefault:true}` → sau khi
  thành công **refetch trang hiện tại** (vì server đã clear default anh em,
  optimistic dễ lệch) → toast xanh.
- "🗑": mở `ConfirmDialog` cảnh báo không hoàn tác; nếu BE trả `4097`
  (`BLUEPRINT_IN_USE`) → toast đỏ với message từ `ApiError`, **không** xoá
  khỏi list.
- "✎": `navigate('/admin/blueprints/:id/edit', { state:{ record } })`.
- Card hiển thị badge loại (`Hành vi/Chuyên môn/Lập trình`), pill "★ Mặc
  định" nếu `isDefault`, dòng tóm tắt ngân sách; với CODING hiển thị "ramp độ
  khó" thay cho danh sách chủ đề.

### 5.3 Wireframe — Form tạo/sửa `/admin/blueprints/new` & `/:id/edit`

```
┌── AdminShell · "Tạo blueprint" / "Sửa blueprint" ───────[ ← Quay lại ]──┐
│ ┌ form card ─────────────────────────────────────────────────────────┐  │
│ │ Vị trí *            Cấp *              Loại phỏng vấn *             │  │
│ │ [BACKEND ▾]         [mid ▾]            [Hành vi ▾]                  │  │
│ │                                                                    │  │
│ │ ── Chủ đề * ─────────────────────────────────────────────────────  │  │
│ │  (BEHAVIORAL / CORE)                                               │  │
│ │  #1 [Năng lực: Giao tiếp ▾] [Quan trọng: HIGH ▾] [Khó: TB ▾] [↑↓✕] │  │
│ │  #2 [Năng lực: Teamwork ▾]  [Quan trọng: MED  ▾] [Khó: Dễ ▾] [↑↓✕] │  │
│ │  [ + Thêm chủ đề ]                                                 │  │
│ │                                                                    │  │
│ │  (CODING) — chỉ slot độ khó, ẩn cột năng lực/lĩnh vực:             │  │
│ │  Slot #1 [Độ khó: Dễ ▾] [Quan trọng: MED ▾] [↑↓✕]                  │  │
│ │  Slot #2 [Độ khó: TB ▾] [Quan trọng: HIGH▾] [↑↓✕]                  │  │
│ │  [ + Thêm slot ]   (số câu = số slot, tự đồng bộ)                  │  │
│ │                                                                    │  │
│ │ Ngân sách câu hỏi   Thời lượng (phút)                              │  │
│ │ [ 8 ]               [ 45 ]                                          │  │
│ │ Follow-up / chủ đề  Follow-up / phiên     (ẩn khi CODING)          │  │
│ │ [ 2 ]               [ 4 ]                                           │  │
│ │ [ ] Dùng AI chọn câu kế tiếp                                       │  │
│ │ [ ] Đặt làm mặc định cho (vị trí, cấp, loại) này                   │  │
│ │ ⓘ Nếu bật, các blueprint mặc định cùng bộ ba sẽ tự bị bỏ cờ.       │  │
│ │                                                                    │  │
│ │                                   [ Huỷ ]  [ Lưu / Tạo blueprint ] │  │
│ └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

Logic form theo `interviewType`:

| | BEHAVIORAL | CORE | CODING |
|---|---|---|---|
| `kind` mỗi topic | `COMPETENCY` (ép) | `DOMAIN` (ép) | `DOMAIN` (placeholder) |
| `topicValue` chọn từ | `COMPETENCIES` | `DOMAINS` | cố định `"CODING"` |
| Cột năng lực/lĩnh vực | hiện | hiện | **ẩn** (chỉ độ khó) |
| `maxFollowUps*` | hiện, default 2/4 | hiện | **ẩn**, gửi `0/0` |
| `questionBudget` | nhập tay (default 8) | nhập tay | = số slot (read-only, auto) |
| Nhãn nhóm | "Chủ đề" | "Chủ đề" | "Slot độ khó" |

- Khi đổi `interviewType` mà `topics` đang có giá trị không hợp lệ cho loại
  mới → reset list `topics` (kèm xác nhận nếu list đã có dữ liệu) để tránh
  gửi `topicValue` sai enum.
- `orderHint` = chỉ số trong mảng (gán lại `1..n` lúc submit theo thứ tự hiển
  thị; nút ↑/↓ đổi chỗ phần tử).
- Validate cục bộ trước submit: bắt buộc `targetRole`, `level`,
  `interviewType`; `topics` không rỗng; mỗi topic có `topicValue` (trừ CODING
  auto); số > 0 cho budget/time.
- Submit: map sang `BlueprintCreateRequest/UpdateRequest`; thành công →
  `navigate('/admin/blueprints', { state:{ flash } })`. Lỗi `ApiError` (đặc
  biệt `4096` duplicate-default, `4002` no-topics) → `Toast` đỏ.
- Prefill edit: ưu tiên `location.state.record`, fallback
  `GET /admin/blueprints/{id}` → `unwrap`.

### 5.4 Dashboard — thêm card "Blueprint phỏng vấn"

Thêm 1 card vào `AdminDashboardPage` (cạnh các card ngân hàng câu hỏi): tổng
số blueprint (đọc `totalCount` từ `list({}, 0, 1)`), nút "Tạo blueprint" +
"Quản lý" (`navigate('/admin/blueprints')`). Có thể tách band riêng "Cấu hình
phỏng vấn" để không trộn ngữ nghĩa với "ngân hàng câu hỏi".

### 5.5 Sidebar — thêm mục nav

Trong `ui.tsx`, thêm vào `ADMIN_NAV`:

```ts
{ to: '/admin/blueprints', label: 'Blueprint phỏng vấn', icon: ClipboardList }
```

(`ClipboardList` hoặc `LayoutTemplate` từ `lucide-react`.)

---

## 6. Danh sách file cần tạo / sửa

**Tạo mới:**

| File | Vai trò |
|---|---|
| `src/types/blueprint.ts` | enum union + DTO + label map + helper map theo loại |
| `src/api/blueprint.ts` | `listBlueprints / getBlueprint / createBlueprint / updateBlueprint / setBlueprintDefault / deleteBlueprint` |
| `src/pages/admin/BlueprintsPage.tsx` | trang list + filter + default + delete |
| `src/pages/admin/BlueprintFormPage.tsx` | form create/edit + topic builder |
| (tuỳ chọn) `src/components/admin/TopicBuilder.tsx` | tách builder topics nếu `BlueprintFormPage` quá dài |

**Sửa:**

| File | Thay đổi |
|---|---|
| `src/App.tsx` | thêm 3 route blueprint (bọc `AdminRoute`) |
| `src/components/admin/ui.tsx` | thêm mục `ADMIN_NAV`; (tuỳ chọn) export `NumberField` nếu cần |
| `src/pages/admin/AdminDashboardPage.tsx` | thêm card/band thống kê blueprint |

---

## 7. Code skeleton (minh hoạ — chưa phải bản cuối)

### 7.1 `src/types/blueprint.ts`

```ts
import {
  COMPETENCIES, COMPETENCY_LABEL, DOMAINS, DOMAIN_LABEL,
  type Competency, type Difficulty, type Domain,
} from '@/types/questionBank';

export type InterviewType = 'BEHAVIORAL' | 'CORE' | 'CODING';
export type TopicKind = 'COMPETENCY' | 'DOMAIN';
export type Importance = 'HIGH' | 'MED' | 'LOW';

export type BlueprintTopic = {
  kind: TopicKind;
  topicValue: string;          // Competency | Domain enum, hoặc "CODING" (placeholder slot)
  importance: Importance;
  targetDifficulty: Difficulty;
  orderHint: number;
};

export type Blueprint = {
  id: string;
  targetRole: string;
  level: string;
  interviewType: InterviewType;
  topics: BlueprintTopic[];
  questionBudget: number;
  timeBudgetMinutes: number;
  maxFollowUpsPerTopic: number;
  maxFollowUpsPerSession: number;
  useAiSelector: boolean;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
};

export type BlueprintRequest = {
  targetRole: string;
  level: string;
  interviewType: InterviewType;
  topics: BlueprintTopic[];
  questionBudget?: number;
  timeBudgetMinutes?: number;
  maxFollowUpsPerTopic?: number;
  maxFollowUpsPerSession?: number;
  useAiSelector?: boolean;
  isDefault?: boolean;
};

export type BlueprintFilters = {
  targetRole?: string;
  level?: string;
  interviewType?: InterviewType;
  isDefault?: boolean;
};

// ── Nhãn tiếng Việt ────────────────────────────────────────────────
export const INTERVIEW_TYPE_LABEL: Record<InterviewType, string> = {
  BEHAVIORAL: 'Hành vi',
  CORE: 'Chuyên môn',
  CODING: 'Lập trình',
};
export const IMPORTANCE_LABEL: Record<Importance, string> = {
  HIGH: 'Cao', MED: 'Trung bình', LOW: 'Thấp',
};
export const LEVELS = ['junior', 'mid', 'senior'] as const;
export const LEVEL_LABEL: Record<(typeof LEVELS)[number], string> = {
  junior: 'Junior', mid: 'Mid', senior: 'Senior',
};
export const INTERVIEW_TYPES: InterviewType[] = ['BEHAVIORAL', 'CORE', 'CODING'];
export const IMPORTANCES: Importance[] = ['HIGH', 'MED', 'LOW'];

/** Placeholder bắt buộc cho slot CODING để qua @Valid (xem §3.5). */
export const CODING_SLOT_TOPIC_VALUE = 'CODING';

/** Map interviewType → cấu hình topicValue cho builder. */
export function topicOptionsFor(t: InterviewType): {
  kind: TopicKind;
  options: readonly string[];
  labels: Record<string, string>;
  structured: boolean; // false = CODING (chỉ slot độ khó)
} {
  if (t === 'BEHAVIORAL')
    return { kind: 'COMPETENCY', options: COMPETENCIES,
             labels: COMPETENCY_LABEL as Record<string, string>, structured: true };
  if (t === 'CORE')
    return { kind: 'DOMAIN', options: DOMAINS,
             labels: DOMAIN_LABEL as Record<string, string>, structured: true };
  return { kind: 'DOMAIN', options: [CODING_SLOT_TOPIC_VALUE],
           labels: { [CODING_SLOT_TOPIC_VALUE]: 'Slot lập trình' }, structured: false };
}
```

### 7.2 `src/api/blueprint.ts`

```ts
/**
 * Blueprint admin client (interview-service).
 * Envelope: list & single đều CÓ bọc ApiResponse → dùng `unwrap`.
 * DELETE trả 204/ApiResponse(null) → fire-and-forget `request`.
 */
import { request, unwrap } from '@/api/client';
import type {
  Blueprint, BlueprintFilters, BlueprintRequest,
} from '@/types/blueprint';

const BASE = '/api/v1/interviews/admin/blueprints';

export type ListPage<T> = { totalCount: number | null; items: T[] };

export function listBlueprints(
  f: BlueprintFilters, page: number, size: number,
): Promise<ListPage<Blueprint>> {
  return unwrap(BASE, {
    query: {
      targetRole: f.targetRole,
      level: f.level,
      interviewType: f.interviewType,
      isDefault: f.isDefault === undefined ? undefined : String(f.isDefault),
      page, size,
    },
  });
}

export const getBlueprint = (id: string) =>
  unwrap<Blueprint>(`${BASE}/${id}`);

export const createBlueprint = (body: BlueprintRequest) =>
  unwrap<Blueprint>(BASE, { method: 'POST', body });

export const updateBlueprint = (id: string, body: BlueprintRequest) =>
  unwrap<Blueprint>(`${BASE}/${id}`, { method: 'PUT', body });

export const setBlueprintDefault = (id: string, isDefault: boolean) =>
  unwrap<Blueprint>(`${BASE}/${id}/default`, {
    method: 'PATCH', body: { isDefault },
  });

export const deleteBlueprint = (id: string) =>
  request<void>(`${BASE}/${id}`, { method: 'DELETE' });
```

### 7.3 `src/pages/admin/BlueprintsPage.tsx` (rút gọn — bám `AdminQuestionsPage`)

```tsx
// State: filters, items, totalCount, page, loading/loadingMore, error,
//        toast, pendingDelete, deleting, reloadKey (giống AdminQuestionsPage).
// fetchPage(p) = listBlueprints(filters, p, PAGE_SIZE)
// useEffect([fetchPage, reloadKey]) tải trang 0; onLoadMore cộng dồn.
// Nhận location.state.flash → Toast (giống AdminQuestionsPage).

const onSetDefault = async (b: Blueprint) => {
  try {
    await setBlueprintDefault(b.id, true);
    setToast({ kind: 'success', text: 'Đã đặt làm mặc định.' });
    setReloadKey((k) => k + 1);           // refetch: server đã clear anh em
  } catch (err) {
    setToast({ kind: 'error',
      text: err instanceof ApiError ? err.message : 'Đặt mặc định thất bại.' });
  }
};

const confirmDelete = async () => {
  if (!pendingDelete) return;
  setDeleting(true);
  try {
    await deleteBlueprint(pendingDelete.id);
    setItems((l) => l.filter((it) => it.id !== pendingDelete.id));
    setTotalCount((c) => (c == null ? c : Math.max(0, c - 1)));
    setToast({ kind: 'success', text: 'Đã xoá blueprint.' });
    setPendingDelete(null);
  } catch (err) {
    // 4097 BLUEPRINT_IN_USE → message từ ApiError, KHÔNG gỡ khỏi list
    setToast({ kind: 'error',
      text: err instanceof ApiError ? err.message : 'Xoá thất bại.' });
  } finally { setDeleting(false); }
};

// <AdminShell title="Blueprint phỏng vấn" breadcrumb=[Tổng quan, Blueprint]
//   actions={<Button onClick={()=>navigate('/admin/blueprints/new')}>+ Tạo blueprint</Button>}>
//   Filter card (EnumSelect targetRole/level/interviewType + Select isDefault)
//   List card[] (BlueprintCard) + nút Tải thêm
//   ConfirmDialog xoá
```

`BlueprintCard`: tiêu đề `TARGET_ROLE_LABEL? · LEVEL_LABEL ·
INTERVIEW_TYPE_LABEL`, pill `★ Mặc định` nếu `isDefault`, dòng tóm tắt
budget; nếu `interviewType==='CODING'` render "ramp" độ khó các slot, ngược
lại render tối đa ~5 pill chủ đề + "+N". Nút: `Đặt mặc định` (ẩn nếu đã
default), `✎`, `🗑`.

### 7.4 `src/pages/admin/BlueprintFormPage.tsx` (lõi topic builder)

```tsx
// mode = id ? 'edit' : 'create'; prefill từ location.state.record | getBlueprint(id)
const [interviewType, setInterviewType] = useState<InterviewType | ''>('');
const [topics, setTopics] = useState<BlueprintTopic[]>([]);
const cfg = interviewType ? topicOptionsFor(interviewType) : null;

// Đổi loại → reset topics nếu khác cấu trúc (xác nhận nếu đang có dữ liệu)
function changeType(next: InterviewType) {
  if (topics.length && next !== interviewType) {
    if (!window.confirm('Đổi loại sẽ xoá danh sách chủ đề hiện tại?')) return;
  }
  setInterviewType(next);
  setTopics([]);
}

function addTopic() {
  if (!cfg) return;
  setTopics((ts) => [...ts, {
    kind: cfg.kind,
    topicValue: cfg.structured ? '' : CODING_SLOT_TOPIC_VALUE,
    importance: 'MED', targetDifficulty: 'MEDIUM', orderHint: ts.length + 1,
  }]);
}
// updateTopic(i, patch) / removeTopic(i) / moveTopic(i, dir) — đổi chỗ + gán lại orderHint

function buildBody(): BlueprintRequest {
  const isCoding = interviewType === 'CODING';
  return {
    targetRole, level, interviewType: interviewType as InterviewType,
    topics: topics.map((t, i) => ({
      kind: cfg!.kind,
      topicValue: isCoding ? CODING_SLOT_TOPIC_VALUE : t.topicValue,
      importance: t.importance,
      targetDifficulty: t.targetDifficulty,
      orderHint: i + 1,
    })),
    questionBudget: isCoding ? topics.length : questionBudget,
    timeBudgetMinutes,
    maxFollowUpsPerTopic: isCoding ? 0 : maxFollowUpsPerTopic,
    maxFollowUpsPerSession: isCoding ? 0 : maxFollowUpsPerSession,
    useAiSelector, isDefault,
  };
}

// validate(): targetRole/level/interviewType bắt buộc; topics≥1;
//   nếu structured → mọi topic phải có topicValue; budget/time > 0.
// submit: create/update → navigate('/admin/blueprints', { state:{ flash } })
//   catch ApiError → Toast đỏ (chú ý 4096 duplicate, 4002 no-topics).
```

Mỗi hàng topic (dùng `EnumSelect`/`Select` từ `ui.tsx`):

```tsx
{cfg.structured && (
  <Select value={t.topicValue}
    onChange={(e) => updateTopic(i, { topicValue: e.target.value })}>
    <option value="">— Chọn —</option>
    {cfg.options.map((o) => <option key={o} value={o}>{cfg.labels[o]}</option>)}
  </Select>
)}
<EnumSelect value={t.importance} options={IMPORTANCES}
  labels={IMPORTANCE_LABEL} onChange={(v)=>updateTopic(i,{importance:v})} />
<EnumSelect value={t.targetDifficulty} options={DIFFICULTIES}
  labels={DIFFICULTY_LABEL} onChange={(v)=>updateTopic(i,{targetDifficulty:v})} />
<button onClick={()=>moveTopic(i,-1)}>↑</button>
<button onClick={()=>moveTopic(i, 1)}>↓</button>
<button onClick={()=>removeTopic(i)}>✕</button>
```

### 7.5 Sửa `App.tsx`

```tsx
import BlueprintsPage from '@/pages/admin/BlueprintsPage';
import BlueprintFormPage from '@/pages/admin/BlueprintFormPage';
// ...
<Route path="/admin/blueprints"        element={<AdminRoute><BlueprintsPage /></AdminRoute>} />
<Route path="/admin/blueprints/new"    element={<AdminRoute><BlueprintFormPage /></AdminRoute>} />
<Route path="/admin/blueprints/:id/edit" element={<AdminRoute><BlueprintFormPage /></AdminRoute>} />
```

### 7.6 Sửa `ui.tsx` — `ADMIN_NAV`

```ts
import { ClipboardList } from 'lucide-react';
const ADMIN_NAV: AdminNavItem[] = [
  { to: '/admin', label: 'Tổng quan', icon: LayoutDashboard, end: true },
  { to: '/admin/questions', label: 'Ngân hàng câu hỏi', icon: Library },
  { to: '/admin/blueprints', label: 'Blueprint phỏng vấn', icon: ClipboardList },
];
```

---

## 8. Checklist nghiệm thu

- [ ] Sidebar admin có mục "Blueprint phỏng vấn", active đúng route.
- [ ] List: lọc theo vị trí/cấp/loại/mặc định hoạt động; "Tải thêm" cộng dồn
      đúng; tổng số khớp `totalCount`.
- [ ] Tạo blueprint BEHAVIORAL (topic = Competency) → 201, quay về list có
      toast.
- [ ] Tạo blueprint CORE (topic = Domain) → 201.
- [ ] Tạo blueprint CODING: chỉ nhập slot độ khó, FE tự bơm
      `kind=DOMAIN/topicValue=CODING`, `questionBudget=số slot`, follow-up
      `0/0` → 201 (không bị 400 validation).
- [ ] Sửa blueprint: prefill đúng (kể cả deep-link không có
      `location.state`), lưu PUT thành công.
- [ ] Đặt mặc định bản B khi bản A đang default cùng `(role,level,type)` →
      sau refetch chỉ còn B là default (A tự mất cờ).
- [ ] Bật `isDefault` ngay trong form tạo/sửa → tương tự, không 409.
- [ ] Xoá blueprint chưa có session → biến mất khỏi list.
- [ ] Xoá blueprint đang có session → toast đỏ message `4097`, hàng vẫn còn.
- [ ] Đổi `interviewType` trong form khi đã có topics → hỏi xác nhận + reset.
- [ ] `npm run build` / typecheck sạch; non-admin vào `/admin/blueprints` bị
      `AdminRoute` chặn.

---

## 9. Cạm bẫy đã biết

1. **List blueprint CÓ bọc `ApiResponse`** (khác QB list bare) → dùng
   `unwrap`, KHÔNG dùng `request`. Sai chỗ này sẽ đọc `undefined.items`.
2. **CODING + `@Valid` topic:** thiếu `kind/topicValue` ⇒ 400. Luôn bơm
   placeholder (§3.5). Nếu BE nới validation sau này thì bỏ placeholder.
3. **Normalize lệch:** đừng cho gõ tay `targetRole/level` tự do rồi so sánh
   chuỗi; dùng dropdown token chuẩn (`TARGET_ROLES`, `junior/mid/senior`) để
   blueprint match được lúc `/start`. Hiển thị giá trị **server trả về**.
4. **`isDefault` query param** phải truyền dạng chuỗi `"true"/"false"`;
   `undefined` thì client tự bỏ qua (đừng gửi `"undefined"`).
5. **Đặt mặc định / lưu với isDefault=true:** phải **refetch** danh sách —
   optimistic không thấy được việc server gỡ default ở bản anh em.
6. **`MIXED` đã bị gỡ** khỏi `InterviewType` — chỉ dùng 3 giá trị. Seed cũ
   còn chữ MIXED là rác lịch sử, không đưa vào UI.
7. `orderHint` nên gán lại `1..n` theo thứ tự hiển thị lúc submit, không phụ
   thuộc giá trị cũ khi user kéo đổi chỗ.

---

## 10. Câu hỏi mở / cần chốt với team

1. **CODING topic validation:** giữ placeholder phía FE, hay xin BE thêm
   nhánh nới `@Valid` khi `interviewType=CODING` (sạch hơn nhưng cần đổi
   `BlueprintTopicDto`/service)? *Mặc định tài liệu: placeholder FE.*
2. **`targetRole`/`level`:** chốt danh sách token cố định
   (`TARGET_ROLES` × `junior/mid/senior`) hay vẫn cho nhập tự do (BE cho ≤20
   ký tự tự do)? Đề xuất: dropdown cố định + (tuỳ chọn) ô "khác" nâng cao.
3. **Dashboard:** gộp card blueprint vào band hiện tại hay tách band "Cấu
   hình phỏng vấn" riêng?
4. **Trùng non-default:** BE chỉ chặn trùng *default* cho bộ ba; cho phép
   nhiều blueprint **không** default cùng `(role,level,type)`. UI có cần cảnh
   báo "đã tồn tại blueprint cùng bộ ba" không, hay để tự do?
```
