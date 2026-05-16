# Handoff — Giao diện quản lý Ngân hàng câu hỏi (Admin)

> Mục tiêu: trang admin `/admin/questions` để quản lý 3 loại câu hỏi
> (Behavioral / Core / Coding) — list + filter + tạo/sửa/xoá + đổi trạng thái,
> kèm tính năng AI sinh đề Coding (LeetCode hoặc custom) để admin review trước
> khi lưu.
>
> Tài liệu này ghi lại **bối cảnh, hợp đồng API, quy ước, phần đã làm và các
> bước còn lại chi tiết** để mở tab mới làm tiếp.

---

## 1. Bối cảnh hệ thống

- FE: `frontend/` — React 19, react-router-dom v7, Tailwind v4 (design tokens),
  lucide-react, alias `@/` → `src/`. Build/typecheck: `cd frontend && npm run lint`
  (chạy `tsc --noEmit`). Dev: `npm run dev` (port 3000).
- Text UI **tiếng Việt**. Pattern trang = `<Header />` + nội dung + `<Footer />`
  (xem `pages/history/HistoryPage.tsx`). Các trang lớn viết kiểu single-file
  to (HistoryPage 631 dòng, PracticeQuestionsPage 1374 dòng) — cứ theo idiom đó.
- Design tokens (xem `src/index.css`): `surface`, `surface-container[-low/high/...]`,
  `surface-container-lowest` (= trắng), `on-surface`, `on-surface-variant`,
  `outline`, `outline-variant`, `secondary` (#4b41e1 indigo), `on-secondary`,
  `secondary-fixed`/`on-secondary-fixed`, radius `xl`/`2xl`/`3xl`. Trạng thái
  hay dùng màu Tailwind sẵn: emerald (active), amber (pending), red (xoá).
- API client: `src/api/client.ts`.
  - `unwrap<T>(path, opts)` → trả `envelope.data` (cho body bọc `ApiResponse`
    `{success,code,message,data,timestamp}`).
  - `request<T>(path, opts)` → trả body thô (204 → `undefined`).
  - `opts`: `{ method, body, query, auth=true, signal }`. `query` chỉ nhận
    `Record<string,string|number|undefined>` (1 giá trị/khoá).
  - `ApiError { message, status, code }`.
- Auth: `useAuth()` từ `@/auth/useAuth` → `{ status, profile, role, signIn,
  signOut, refreshProfile }`. `role: 'USER'|'ADMIN'|null` (đã thêm — decode từ
  JWT claim `role`).

## 2. Hợp đồng Backend (question-bank-service)

Gateway route: `/api/v1/question-bank/**` (KHÔNG StripPrefix) →
question-bank-service có `context-path: /api/v1/question-bank`. Gateway **bắt
buộc `ROLE_ADMIN`** cho mọi path chứa `/admin/` (introspect JWT). Đây là ranh
giới bảo mật thật; FE chỉ gate UX.

**Base admin** = `/api/v1/question-bank/admin/questions`

| Method | Path | Body | Response body |
|---|---|---|---|
| GET | `/behavioral?competency&difficulty&status&tags&page&size` | — | **bare** `{totalCount, items[]}` (KHÔNG bọc ApiResponse → dùng `request`) |
| GET | `/core?domain&targetRole&difficulty&status&tags&page&size` | — | bare `{totalCount, items[]}` |
| GET | `/coding?difficulty&status&tags&page&size` | — | bare `{totalCount, items[]}` |
| POST | `/behavioral` \| `/core` \| `/coding` | request DTO | `ApiResponse<Resp>` (201) → `unwrap` |
| PUT | `/{kind}/{id}` | request DTO | `ApiResponse<Resp>` → `unwrap` |
| PATCH | `/{id}/status` | `{status}` | `ApiResponse<null>` → `request` |
| DELETE | `/{id}` | — | 204 → `request` |

`tags` là `List<String>` — FE truyền `?tags=a,b` (đã xử lý trong
`api/questionBank.ts` qua `tagsParam`, join bằng dấu phẩy).

Đọc 1 câu hỏi (public, dùng cho refetch khi deep-link edit):
`GET /api/v1/question-bank/questions/{id}` → `ApiResponse<Object>` → `unwrap`.
Với coding, response này **có** `testCases`.

**Enums** (mirror trong `src/types/questionBank.ts`):
- `Difficulty` EASY|MEDIUM|HARD
- `QuestionStatus` DRAFT|ACTIVE|INACTIVE
- `Competency` 8 giá trị, `Domain` 17 giá trị, `TargetRole` 9 giá trị (xem
  type file — đã có map nhãn tiếng Việt `*_LABEL` + mảng `COMPETENCIES`,
  `DOMAINS`, `TARGET_ROLES`, `DIFFICULTIES`, `STATUSES`).

**Request DTO bắt buộc** (validation BE):
- Behavioral: `difficulty`, `text` (NotBlank), `competency`, `expectedSignals`
  (NotEmpty). `tags` optional.
- Core: `difficulty`, `text`, `targetRoles` (NotEmpty list), `domain`,
  `keyConcepts` (NotEmpty), `depthExpected` (NotBlank).
- Coding: `difficulty`, `title`, `description`, `timeLimitMinutes` (≥1, default
  30), `optimalTimeComplexity`, `optimalSpaceComplexity`, `functionMeta`
  (`{fn, params:[{name,type}], return, orderMatters, inPlace}` — key JSON là
  `"return"`), `starterCode` optional `{java,python,cpp,javascript}`,
  `testCases` (NotEmpty) — mỗi phần tử `{id?, inputData, expectedOutput,
  is_hidden}` (**key JSON `is_hidden` snake_case**).

## 3. Hợp đồng AI (sinh đề Coding)

Endpoint qua gateway: `POST /api/ai/generate-testcases` (gateway để
`/api/ai/**` là **public**, StripPrefix=2; AI service chỉ check `X-API-Key`
nếu `SERVICE_API_KEY` được set — FE gửi header này chỉ khi có
`VITE_AI_API_KEY`). Body **camelCase only**:

```jsonc
// mode custom
{ "mode":"custom", "title":"...", "description":"...",
  "difficulty":"EASY", "tags":["array"], "optimalTimeComplexity":"O(n)",
  "optimalSpaceComplexity":"O(n)", "numTestcases":10, "numVisible":3 }
// mode leetcode
{ "mode":"leetcode", "leetcodeUrl":"two-sum", "numTestcases":10, "numVisible":3 }
```
Ràng buộc: `1 ≤ numVisible < numTestcases`. Custom cần `title`+`description`.

Response = full payload coding (camelCase, `functionMeta.return` giữ key
`"return"`, testcase dùng `isHidden`). Lỗi FastAPI: `{detail: ...}` (403
premium, 404 not_found, 400 validation, 422 pipeline).

> **Flow chuẩn (theo `AI/docs/testcase-generator-design.md`):** Admin gọi
> generate → review/sửa trên UI → bấm Lưu → FE POST vào question-bank
> `/admin/questions/coding`.

Helper đã có sẵn: `mapGeneratedToCodingRequest(g)` trong
`api/questionBank.ts` — chuyển `AiGeneratedCoding` → `CodingQuestionRequest`
(bridge `isHidden`→`is_hidden`, bỏ `label`/`note`, chuẩn hoá difficulty).

## 4. Đã hoàn thành (state hiện tại)

| File | Trạng thái | Nội dung |
|---|---|---|
| `src/types/questionBank.ts` | ✅ mới | toàn bộ enums, DTO request/response, AI types, `*_LABEL`, mảng options, `STARTER_LANGS` |
| `src/api/questionBank.ts` | ✅ mới | `listBehavioral/Core/Coding`, `getQuestion`, `create*`, `update*`, `updateStatus`, `deleteQuestion`, `generateCoding`, `mapGeneratedToCodingRequest`, type `ListPage<T>` |
| `src/lib/jwt.ts` | ✅ mới | `decodeJwtRole(token)` |
| `src/auth/AuthContext.tsx` | ✅ sửa | thêm `role` state (decode JWT qua `applyToken`), expose trong context |
| `src/auth/AdminRoute.tsx` | ✅ mới | guard: loading / redirect login / màn 403 / render |
| `src/lib/env.ts` | ✅ sửa | thêm `AI_API_KEY` |
| `src/vite-env.d.ts` | ✅ sửa | thêm `VITE_AI_API_KEY` |

> Lưu ý: chưa chạy `npm run lint` — nên chạy sau khi xong UI (Bước 5).

## 5. Việc còn lại — chi tiết

### Bước A — Shared admin UI: `src/components/admin/ui.tsx`

Export các thành phần dùng lại (giữ trong 1 file cho gọn):

- `AdminShell({ title, breadcrumb?, actions?, children })`: `<Header />` +
  container `max-w-6xl mx-auto px-6 pt-24 pb-16`, tiêu đề + slot actions phải,
  + `<Footer />`. (Header: `@/components/layout/Header`, Footer:
  `@/components/layout/Footer`.)
- `Pill`/`StatusPill(status)`/`DifficultyPill(difficulty)`: dùng `STATUS_LABEL`,
  `DIFFICULTY_LABEL`. Màu: ACTIVE→emerald, DRAFT→on-surface-variant,
  INACTIVE→amber; EASY→emerald, MEDIUM→amber, HARD→red. Pill = rounded-full
  px-2.5 py-0.5 text-xs font-medium.
- `TagInput({ value:string[], onChange })`: nhập tag, Enter/`,` để thêm, click
  ✕ để xoá. Chip = `bg-surface-container px-2 py-0.5 rounded-md`.
- `Field({ label, required?, hint?, error?, children })`: label + control +
  text lỗi đỏ. `Select`, `Input`, `Textarea` wrapper style nhất quán
  (border `outline-variant`, rounded-xl, focus ring `secondary`).
- `ConfirmDialog({ open, title, message, confirmText, danger?, onConfirm,
  onCancel })`: modal overlay `fixed inset-0 bg-black/40` + card trắng.
- `Pagination({ page, size, totalCount, onPage })` hoặc nút "Tải thêm" kiểu
  HistoryPage (load-more cộng dồn, `hasNext = items.length < totalCount`).
- `useToast()` đơn giản hoặc inline banner thành công/thất bại (không có lib
  toast — tự làm 1 banner cố định góc phải hoặc trên cùng).

### Bước B — Trang list: `src/pages/admin/AdminQuestionsPage.tsx`

- State: `kind: QuestionKind` (tab, default 'behavioral'), `filters` theo kind,
  `items`, `totalCount`, `page`, `loading`, `error`.
- Tabs 3 loại (dùng `KIND_LABEL`). Đổi tab → reset filter+page, fetch lại.
- FilterBar tuỳ kind:
  - behavioral: competency, difficulty, status, tags
  - core: domain, targetRole, difficulty, status, tags
  - coding: difficulty, status, tags
  - Select dùng mảng options + `*_LABEL`. Có nút "Xoá lọc".
- Fetch theo pattern HistoryPage (cancelled flag, `PAGE_SIZE=20`,
  `listBehavioral/Core/Coding(filters,page,size)` trả `ListPage<T>`).
- Mỗi item = card: tiêu đề (behavioral/core: `text` rút gọn 2 dòng; coding:
  `title`), hàng pill (DifficultyPill, StatusPill, type-specific:
  competency/domain/roles), tags, ngày cập nhật (`Intl.DateTimeFormat('vi-VN')`),
  và actions:
  - **Sửa** → navigate `/admin/questions/{kind}/{id}/edit` (kèm `state:{record}`
    để form prefill, fallback `getQuestion(id)` nếu vào thẳng URL).
  - **Đổi trạng thái**: menu nhỏ DRAFT/ACTIVE/INACTIVE → `updateStatus` →
    cập nhật item tại chỗ.
  - **Xoá** → `ConfirmDialog` → `deleteQuestion(id)` → bỏ khỏi list.
- Nút "Tạo câu hỏi" (theo tab) → `/admin/questions/new/{kind}`.
- Empty state + error state (có nút thử lại) như HistoryPage.

### Bước C — Form pages

`src/pages/admin/BehavioralFormPage.tsx`,
`CoreFormPage.tsx`, `CodingFormPage.tsx`. Mỗi trang dùng `AdminShell`, đọc
`useParams()` (`id?`), `useLocation().state?.record` để biết create vs edit
(`mode = id ? 'edit' : 'create'`). Nếu edit mà không có `state.record` →
`getQuestion(id)` rồi prefill. Submit → `create*`/`update*` →
điều hướng về `/admin/questions` + toast. Validate client tối thiểu khớp BE
(NotBlank/NotEmpty) trước khi gọi.

- **Behavioral**: difficulty (select), competency (select),
  text (textarea), expectedSignals (TagInput, ≥1), tags (TagInput).
- **Core**: difficulty, domain (select), targetRoles (multi-select chips từ
  `TARGET_ROLES`, ≥1), text (textarea), keyConcepts (TagInput, ≥1),
  depthExpected (textarea/input), tags.
- **Coding** (phức tạp nhất):
  - Trường: difficulty, title, description (textarea lớn), timeLimitMinutes
    (number ≥1), optimalTimeComplexity, optimalSpaceComplexity, tags.
  - `functionMeta`: fn (input), params (list editor: name+type, thêm/xoá),
    return (input), orderMatters/inPlace (checkbox).
  - `starterCode`: tab 4 ngôn ngữ (`STARTER_LANGS`) — dùng
    `@monaco-editor/react` (đã có trong deps; xem cách dùng ở
    `pages/practice/PracticeCodingPreviewPage.tsx`/`PracticeSessionPage.tsx`)
    hoặc `<textarea>` mono nếu muốn nhẹ.
  - `testCases`: editor list. Mỗi case: `inputData` & `expectedOutput` nhập
    bằng `<textarea>` JSON (parse khi blur/submit, báo lỗi nếu JSON sai),
    `is_hidden` checkbox, nút xoá. Nút "Thêm test case". ≥1 case.
  - **AI generate dialog** (nút "Sinh bằng AI" ở đầu form):
    - Modal: chọn mode (custom/leetcode). leetcode: input URL/slug. custom:
      title, description, difficulty?, tags?, complexity?. Chung:
      numTestcases (default 10), numVisible (default 3) — validate
      `1≤numVisible<numTestcases`.
    - Gọi `generateCoding(req)` (loading spinner; có thể mất ~30s — disable
      nút, hiện "Đang sinh đề...").
    - Thành công → `mapGeneratedToCodingRequest(res)` → prefill toàn bộ form
      (cho admin sửa tiếp). Hiện `res.warning` nếu có. Đóng modal.
    - Lỗi → hiện `ApiError.message` (đã map tiếng Việt trong api).

### Bước D — Routing + nav: `src/App.tsx`, `src/data/navigation.ts`, `src/components/layout/Header.tsx`

Thêm route (bọc `<AdminRoute>`):
```tsx
import AdminRoute from '@/auth/AdminRoute';
import AdminQuestionsPage from '@/pages/admin/AdminQuestionsPage';
import BehavioralFormPage from '@/pages/admin/BehavioralFormPage';
import CoreFormPage from '@/pages/admin/CoreFormPage';
import CodingFormPage from '@/pages/admin/CodingFormPage';
// ...
<Route path="/admin/questions" element={<AdminRoute><AdminQuestionsPage/></AdminRoute>} />
<Route path="/admin/questions/new/behavioral" element={<AdminRoute><BehavioralFormPage/></AdminRoute>} />
<Route path="/admin/questions/new/core" element={<AdminRoute><CoreFormPage/></AdminRoute>} />
<Route path="/admin/questions/new/coding" element={<AdminRoute><CodingFormPage/></AdminRoute>} />
<Route path="/admin/questions/behavioral/:id/edit" element={<AdminRoute><BehavioralFormPage/></AdminRoute>} />
<Route path="/admin/questions/core/:id/edit" element={<AdminRoute><CoreFormPage/></AdminRoute>} />
<Route path="/admin/questions/coding/:id/edit" element={<AdminRoute><CodingFormPage/></AdminRoute>} />
```
(Đặt trước route catch-all `<Route path="*" .../>`.)

Nav: trong `Header.tsx` `UserMenu`, thêm 1 mục "Quản lý câu hỏi" → `/admin/questions`
**chỉ khi** `useAuth().role === 'ADMIN'` (truyền `role` xuống hoặc đọc trong
Header). Có thể thêm vào `USER_MENU_ITEMS` kèm cờ `adminOnly` rồi lọc.

### Bước E — Typecheck & kiểm thử

1. `cd frontend && npm run lint` → sửa hết lỗi TS.
2. `npm run dev`, đăng nhập tài khoản ADMIN, vào `/admin/questions`:
   - List + filter + phân trang mỗi tab.
   - Tạo/sửa/xoá/đổi trạng thái mỗi loại.
   - Coding: AI generate (thử `mode=leetcode`, `leetcodeUrl=two-sum`) →
     prefill → sửa → lưu → thấy trong list.
3. Đăng nhập tài khoản USER → `/admin/questions` phải thấy màn 403; menu admin
   không hiện.

## 6. Checklist nghiệm thu

- [ ] 3 tab list + filter + pagination chạy với API thật.
- [ ] CRUD đầy đủ 3 loại, validate khớp BE (không 400 do thiếu field).
- [ ] Đổi trạng thái + xoá có confirm.
- [ ] AI generate (custom + leetcode) → review/sửa → lưu thành công.
- [ ] `testCases.is_hidden` (snake_case) & `functionMeta.return` gửi đúng key.
- [ ] AdminRoute chặn non-admin; menu admin ẩn với USER.
- [ ] `npm run lint` sạch.

## 7. Cạm bẫy đã biết

- List endpoints **không** bọc `ApiResponse` → phải `request`, không `unwrap`
  (đã đúng trong `api/questionBank.ts`).
- Testcase: AI trả `isHidden`, QB cần `is_hidden`; `label`/`note` phải bỏ —
  dùng `mapGeneratedToCodingRequest`.
- `functionMeta.return` là từ khoá — type đã đặt key `return: string`, giữ
  nguyên khi gửi.
- `query` của client chỉ 1 giá trị/khoá → `tags` phải join `","`.
- `role` có thể `null` vài ms sau hard refresh (optimistic cached profile);
  `AdminRoute` coi `authenticated && role===null` là "đang kiểm tra".
- AI call có thể mất ~30s → UI phải có trạng thái loading rõ ràng, không để
  người dùng bấm lại.
- Monaco editor: đã có `@monaco-editor/react`; tham khảo trang practice coding
  để biết cách cấu hình (theme/onChange) cho nhất quán.
