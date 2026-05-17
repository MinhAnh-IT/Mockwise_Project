import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Plus, Sparkles, Trash2, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  createCoding,
  generateCoding,
  getQuestion,
  mapGeneratedToCodingRequest,
  updateCoding,
} from '@/api/questionBank';
import {
  AdminShell,
  Button,
  CodeArea,
  EnumSelect,
  ErrorState,
  Field,
  Input,
  Spinner,
  TagInput,
  Textarea,
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import {
  DIFFICULTIES,
  DIFFICULTY_LABEL,
  STARTER_LANGS,
  type AiGenerateMode,
  type AiGenerateRequest,
  type CodingQuestion,
  type CodingQuestionRequest,
  type Difficulty,
  type ParamMeta,
  type StarterCode,
} from '@/types/questionBank';

type ScKey = keyof StarterCode; // 'java' | 'python' | 'cpp' | 'javascript'
type Sc = Record<ScKey, string>;
const EMPTY_SC: Sc = { python: '', java: '', cpp: '', javascript: '' };

type TcRow = {
  _k: string;
  id?: string;
  inputText: string;
  outputText: string;
  is_hidden: boolean;
  /** LeetCode-style explanation for this example. Shown only for non-hidden. */
  noteText: string;
  error?: string;
};

type Errors = Partial<
  Record<
    | 'difficulty'
    | 'title'
    | 'description'
    | 'optimalTimeComplexity'
    | 'optimalSpaceComplexity'
    | 'fn'
    | 'return'
    | 'testCases',
    string
  >
>;

const newKey = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : Math.random().toString(36).slice(2);

const pretty = (v: unknown) => JSON.stringify(v ?? {}, null, 2);

function emptyRow(): TcRow {
  return {
    _k: newKey(),
    inputText: '{}',
    outputText: '{}',
    is_hidden: false,
    noteText: '',
  };
}

export default function CodingFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const mode: 'create' | 'edit' = id ? 'edit' : 'create';

  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  // LeetCode-style constraints — free-form multi-line markdown, optional.
  // One constraint per row; rendered as a list in the candidate workspace.
  const [constraints, setConstraints] = useState('');
  const [optimalTimeComplexity, setOptimalTimeComplexity] = useState('');
  const [optimalSpaceComplexity, setOptimalSpaceComplexity] = useState('');
  const [tags, setTags] = useState<string[]>([]);

  const [fn, setFn] = useState('');
  const [params, setParams] = useState<ParamMeta[]>([]);
  const [retType, setRetType] = useState('');
  const [orderMatters, setOrderMatters] = useState(true);
  const [inPlace, setInPlace] = useState(false);

  const [starter, setStarter] = useState<Sc>(EMPTY_SC);
  const [scTab, setScTab] = useState<ScKey>('python');

  const [rows, setRows] = useState<TcRow[]>([emptyRow()]);

  const [errors, setErrors] = useState<Errors>({});
  const [toast, setToast] = useState<ToastState>(null);
  const [loading, setLoading] = useState(mode === 'edit');
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);

  function fillFromRequest(q: CodingQuestionRequest) {
    setDifficulty(q.difficulty);
    setTitle(q.title);
    setDescription(q.description);
    setConstraints(q.constraints ?? '');
    setOptimalTimeComplexity(q.optimalTimeComplexity ?? '');
    setOptimalSpaceComplexity(q.optimalSpaceComplexity ?? '');
    setTags(q.tags ?? []);
    setFn(q.functionMeta?.fn ?? '');
    setParams(q.functionMeta?.params ?? []);
    setRetType(q.functionMeta?.return ?? '');
    setOrderMatters(!!q.functionMeta?.orderMatters);
    setInPlace(!!q.functionMeta?.inPlace);
    setStarter({ ...EMPTY_SC, ...(q.starterCode ?? {}) });
    setRows(
      (q.testCases ?? []).length
        ? q.testCases.map((tc) => ({
            _k: newKey(),
            id: tc.id,
            inputText: pretty(tc.inputData),
            outputText: pretty(tc.expectedOutput),
            is_hidden: tc.is_hidden,
            noteText: tc.note ?? '',
          }))
        : [emptyRow()],
    );
  }

  function fillFromQuestion(q: CodingQuestion) {
    fillFromRequest({
      difficulty: q.difficulty,
      tags: q.tags,
      title: q.title,
      description: q.description,
      constraints: q.constraints ?? undefined,
      optimalTimeComplexity: q.optimalTimeComplexity,
      optimalSpaceComplexity: q.optimalSpaceComplexity,
      functionMeta: q.functionMeta,
      starterCode: q.starterCode ?? undefined,
      testCases: q.testCases ?? [],
    });
  }

  useEffect(() => {
    if (mode !== 'edit' || !id) return;
    const fromState = (location.state as { record?: CodingQuestion } | null)
      ?.record;
    // The admin list endpoint may omit testCases/functionMeta — only trust
    // the routed record if it actually carries them; otherwise refetch the
    // full payload from the public single-question route.
    if (
      fromState &&
      fromState.id === id &&
      fromState.functionMeta &&
      Array.isArray(fromState.testCases)
    ) {
      fillFromQuestion(fromState);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    getQuestion(id)
      .then((q) => {
        if (!cancelled) fillFromQuestion(q as CodingQuestion);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(
          err instanceof Error ? err.message : 'Không tải được câu hỏi.',
        );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [id, mode, location.state]);

  function buildBody(): { body?: CodingQuestionRequest; errors: Errors } {
    const e: Errors = {};
    if (!difficulty) e.difficulty = 'Chọn độ khó.';
    if (!title.trim()) e.title = 'Nhập tiêu đề.';
    if (!description.trim()) e.description = 'Nhập mô tả đề bài.';
    if (!optimalTimeComplexity.trim())
      e.optimalTimeComplexity = 'Nhập độ phức tạp thời gian tối ưu.';
    if (!optimalSpaceComplexity.trim())
      e.optimalSpaceComplexity = 'Nhập độ phức tạp không gian tối ưu.';
    if (!fn.trim()) e.fn = 'Nhập tên hàm.';
    if (!retType.trim()) e.return = 'Nhập kiểu trả về.';

    // Parse + validate every test case's JSON.
    let tcInvalid = false;
    const parsed = rows.map((r) => {
      let inputData: Record<string, unknown> | null = null;
      let expectedOutput: Record<string, unknown> | null = null;
      let rowErr: string | undefined;
      try {
        inputData = JSON.parse(r.inputText) as Record<string, unknown>;
      } catch {
        rowErr = 'Input không phải JSON hợp lệ.';
      }
      if (!rowErr) {
        try {
          expectedOutput = JSON.parse(r.outputText) as Record<string, unknown>;
        } catch {
          rowErr = 'Expected output không phải JSON hợp lệ.';
        }
      }
      if (rowErr) tcInvalid = true;
      return { r, inputData, expectedOutput, rowErr };
    });
    if (rows.length === 0) {
      e.testCases = 'Cần ít nhất một test case.';
    } else if (tcInvalid) {
      e.testCases = 'Có test case với JSON không hợp lệ — kiểm tra lại.';
      setRows((prev) =>
        prev.map((row) => {
          const p = parsed.find((x) => x.r._k === row._k);
          return { ...row, error: p?.rowErr };
        }),
      );
    }

    if (Object.keys(e).length > 0) return { errors: e };

    const starterCode: StarterCode = {};
    (Object.keys(starter) as ScKey[]).forEach((k) => {
      if (starter[k].trim()) starterCode[k] = starter[k];
    });

    const body: CodingQuestionRequest = {
      difficulty: difficulty as Difficulty,
      tags,
      title: title.trim(),
      description: description.trim(),
      constraints: constraints.trim() || undefined,
      optimalTimeComplexity: optimalTimeComplexity.trim(),
      optimalSpaceComplexity: optimalSpaceComplexity.trim(),
      functionMeta: {
        fn: fn.trim(),
        params: params
          .filter((p) => p.name.trim() || p.type.trim())
          .map((p) => ({ name: p.name.trim(), type: p.type.trim() })),
        return: retType.trim(),
        orderMatters,
        inPlace,
      },
      starterCode:
        Object.keys(starterCode).length > 0 ? starterCode : undefined,
      testCases: parsed.map((p) => ({
        id: p.r.id,
        inputData: p.inputData as Record<string, unknown>,
        expectedOutput: p.expectedOutput as Record<string, unknown>,
        is_hidden: p.r.is_hidden,
        note: p.r.noteText.trim() || undefined,
      })),
    };
    return { body, errors: {} };
  }

  async function onSubmit(ev: React.FormEvent) {
    ev.preventDefault();
    const { body, errors: e } = buildBody();
    setErrors(e);
    if (!body) return;

    setSubmitting(true);
    try {
      if (mode === 'edit' && id) await updateCoding(id, body);
      else await createCoding(body);
      navigate('/admin/questions', {
        state: {
          flash:
            mode === 'edit'
              ? 'Đã cập nhật câu hỏi lập trình.'
              : 'Đã tạo câu hỏi lập trình.',
        },
      });
    } catch (err) {
      setToast({
        kind: 'error',
        text:
          err instanceof ApiError
            ? err.message
            : 'Lưu thất bại, vui lòng thử lại.',
      });
      setSubmitting(false);
    }
  }

  function applyGenerated(req: CodingQuestionRequest, warning?: string | null) {
    fillFromRequest(req);
    setAiOpen(false);
    setToast({
      kind: warning ? 'error' : 'success',
      text: warning
        ? `Đã sinh đề (có cảnh báo): ${warning}`
        : 'Đã sinh đề bằng AI — kiểm tra và chỉnh sửa trước khi lưu.',
    });
  }

  return (
    <AdminShell
      title={mode === 'edit' ? 'Sửa câu hỏi Lập trình' : 'Tạo câu hỏi Lập trình'}
      breadcrumb={[
        { label: 'Ngân hàng câu hỏi', to: '/admin/questions' },
        { label: mode === 'edit' ? 'Sửa câu hỏi' : 'Tạo câu hỏi' },
      ]}
      actions={
        <>
          {mode === 'create' && (
            <Button variant="outline" onClick={() => setAiOpen(true)}>
              <Sparkles className="h-4 w-4" />
              Sinh bằng AI
            </Button>
          )}
          <Button
            variant="outline"
            onClick={() => navigate('/admin/questions')}
          >
            <ArrowLeft className="h-4 w-4" />
            Quay lại
          </Button>
        </>
      }
    >
      <Toast toast={toast} onClose={() => setToast(null)} />

      {loading ? (
        <Spinner label="Đang tải câu hỏi…" />
      ) : loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => navigate('/admin/questions')}
        />
      ) : (
        <form onSubmit={onSubmit} className="space-y-6">
          {/* General */}
          <section className="space-y-5 rounded-2xl border border-outline-variant bg-surface-container-lowest p-6">
            <h2 className="text-sm font-bold uppercase tracking-wide text-on-surface-variant">
              Thông tin chung
            </h2>
            <div className="grid gap-5 sm:grid-cols-2">
              <Field label="Tiêu đề" required error={errors.title}>
                <Input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Two Sum"
                />
              </Field>
              <Field label="Độ khó" required error={errors.difficulty}>
                <EnumSelect<Difficulty>
                  value={difficulty}
                  onChange={(v) => setDifficulty(v)}
                  options={DIFFICULTIES}
                  labels={DIFFICULTY_LABEL}
                  placeholder="— Chọn độ khó —"
                />
              </Field>
            </div>

            <Field label="Mô tả đề bài" required error={errors.description}>
              <Textarea
                rows={6}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Mô tả bài toán và ví dụ…"
              />
            </Field>

            <Field
              label="Ràng buộc"
              hint="Tuỳ chọn — mỗi ràng buộc một dòng (kiểu LeetCode). Hỗ trợ markdown."
            >
              <Textarea
                rows={4}
                value={constraints}
                onChange={(e) => setConstraints(e.target.value)}
                placeholder={'- 1 <= n <= 10^5\n- -10^9 <= nums[i] <= 10^9'}
              />
            </Field>

            <div className="grid gap-5 sm:grid-cols-2">
              <Field
                label="Time complexity tối ưu"
                required
                error={errors.optimalTimeComplexity}
              >
                <Input
                  value={optimalTimeComplexity}
                  onChange={(e) => setOptimalTimeComplexity(e.target.value)}
                  placeholder="O(n)"
                />
              </Field>
              <Field
                label="Space complexity tối ưu"
                required
                error={errors.optimalSpaceComplexity}
              >
                <Input
                  value={optimalSpaceComplexity}
                  onChange={(e) => setOptimalSpaceComplexity(e.target.value)}
                  placeholder="O(1)"
                />
              </Field>
            </div>

            <Field label="Tags" hint="Tuỳ chọn — dùng để lọc.">
              <TagInput value={tags} onChange={setTags} />
            </Field>
          </section>

          {/* Function meta */}
          <section className="space-y-5 rounded-2xl border border-outline-variant bg-surface-container-lowest p-6">
            <h2 className="text-sm font-bold uppercase tracking-wide text-on-surface-variant">
              Chữ ký hàm
            </h2>
            <div className="grid gap-5 sm:grid-cols-2">
              <Field label="Tên hàm" required error={errors.fn}>
                <Input
                  value={fn}
                  onChange={(e) => setFn(e.target.value)}
                  placeholder="twoSum"
                />
              </Field>
              <Field label="Kiểu trả về" required error={errors.return}>
                <Input
                  value={retType}
                  onChange={(e) => setRetType(e.target.value)}
                  placeholder="int[]"
                />
              </Field>
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <span className="text-sm font-semibold text-on-surface">
                  Tham số
                </span>
                <Button
                  variant="ghost"
                  onClick={() =>
                    setParams((p) => [...p, { name: '', type: '' }])
                  }
                >
                  <Plus className="h-4 w-4" />
                  Thêm tham số
                </Button>
              </div>
              {params.length === 0 ? (
                <p className="text-xs text-on-surface-variant">
                  Chưa có tham số nào.
                </p>
              ) : (
                <div className="space-y-2">
                  {params.map((p, i) => (
                    <div key={i} className="flex items-center gap-2">
                      <Input
                        value={p.name}
                        onChange={(e) =>
                          setParams((arr) =>
                            arr.map((x, j) =>
                              j === i ? { ...x, name: e.target.value } : x,
                            ),
                          )
                        }
                        placeholder="tên (vd: nums)"
                      />
                      <Input
                        value={p.type}
                        onChange={(e) =>
                          setParams((arr) =>
                            arr.map((x, j) =>
                              j === i ? { ...x, type: e.target.value } : x,
                            ),
                          )
                        }
                        placeholder="kiểu (vd: int[])"
                      />
                      <button
                        type="button"
                        onClick={() =>
                          setParams((arr) => arr.filter((_, j) => j !== i))
                        }
                        className="rounded-lg p-2 text-on-surface-variant transition hover:bg-red-50 hover:text-red-600"
                        aria-label="Xoá tham số"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex flex-wrap gap-6">
              <label className="flex items-center gap-2 text-sm text-on-surface">
                <input
                  type="checkbox"
                  checked={orderMatters}
                  onChange={(e) => setOrderMatters(e.target.checked)}
                  className="h-4 w-4 accent-secondary"
                />
                Thứ tự kết quả quan trọng
              </label>
              <label className="flex items-center gap-2 text-sm text-on-surface">
                <input
                  type="checkbox"
                  checked={inPlace}
                  onChange={(e) => setInPlace(e.target.checked)}
                  className="h-4 w-4 accent-secondary"
                />
                Sửa đổi tại chỗ (in-place)
              </label>
            </div>
          </section>

          {/* Starter code */}
          <section className="space-y-4 rounded-2xl border border-outline-variant bg-surface-container-lowest p-6">
            <h2 className="text-sm font-bold uppercase tracking-wide text-on-surface-variant">
              Starter code{' '}
              <span className="font-normal normal-case text-on-surface-variant">
                (tuỳ chọn)
              </span>
            </h2>
            <div className="flex gap-1 border-b border-outline-variant">
              {STARTER_LANGS.map((l) => (
                <button
                  key={l.key}
                  type="button"
                  onClick={() => setScTab(l.key)}
                  className={`-mb-px border-b-2 px-4 py-2 text-sm font-semibold transition ${
                    scTab === l.key
                      ? 'border-secondary text-secondary'
                      : 'border-transparent text-on-surface-variant hover:text-on-surface'
                  }`}
                >
                  {l.label}
                  {starter[l.key].trim() && (
                    <span className="ml-1 text-emerald-600">•</span>
                  )}
                </button>
              ))}
            </div>
            <CodeArea
              rows={8}
              value={starter[scTab]}
              onChange={(e) =>
                setStarter((s) => ({ ...s, [scTab]: e.target.value }))
              }
              placeholder={`// starter code ${scTab}`}
            />
          </section>

          {/* Test cases */}
          <section className="space-y-4 rounded-2xl border border-outline-variant bg-surface-container-lowest p-6">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-bold uppercase tracking-wide text-on-surface-variant">
                Test cases
              </h2>
              <Button
                variant="ghost"
                onClick={() => setRows((r) => [...r, emptyRow()])}
              >
                <Plus className="h-4 w-4" />
                Thêm test case
              </Button>
            </div>
            {errors.testCases && (
              <p className="text-xs text-red-600">{errors.testCases}</p>
            )}
            <div className="space-y-4">
              {rows.map((row, i) => (
                <div
                  key={row._k}
                  className="rounded-xl border border-outline-variant p-4"
                >
                  <div className="mb-3 flex items-center justify-between">
                    <span className="text-sm font-semibold text-on-surface">
                      Test case #{i + 1}
                    </span>
                    <div className="flex items-center gap-3">
                      <label className="flex items-center gap-1.5 text-xs text-on-surface-variant">
                        <input
                          type="checkbox"
                          checked={row.is_hidden}
                          onChange={(e) =>
                            setRows((arr) =>
                              arr.map((x) =>
                                x._k === row._k
                                  ? { ...x, is_hidden: e.target.checked }
                                  : x,
                              ),
                            )
                          }
                          className="h-3.5 w-3.5 accent-secondary"
                        />
                        Ẩn (hidden)
                      </label>
                      <button
                        type="button"
                        onClick={() =>
                          setRows((arr) =>
                            arr.filter((x) => x._k !== row._k),
                          )
                        }
                        className="rounded-lg p-1.5 text-on-surface-variant transition hover:bg-red-50 hover:text-red-600"
                        aria-label="Xoá test case"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div>
                      <p className="mb-1 text-xs font-medium text-on-surface-variant">
                        Input (JSON)
                      </p>
                      <CodeArea
                        rows={4}
                        value={row.inputText}
                        onChange={(e) =>
                          setRows((arr) =>
                            arr.map((x) =>
                              x._k === row._k
                                ? { ...x, inputText: e.target.value, error: undefined }
                                : x,
                            ),
                          )
                        }
                      />
                    </div>
                    <div>
                      <p className="mb-1 text-xs font-medium text-on-surface-variant">
                        Expected output (JSON)
                      </p>
                      <CodeArea
                        rows={4}
                        value={row.outputText}
                        onChange={(e) =>
                          setRows((arr) =>
                            arr.map((x) =>
                              x._k === row._k
                                ? { ...x, outputText: e.target.value, error: undefined }
                                : x,
                            ),
                          )
                        }
                      />
                    </div>
                  </div>
                  {!row.is_hidden && (
                    <div className="mt-3">
                      <p className="mb-1 text-xs font-medium text-on-surface-variant">
                        Giải thích ví dụ (tuỳ chọn — hiển thị cho thí sinh)
                      </p>
                      <Textarea
                        rows={2}
                        value={row.noteText}
                        onChange={(e) =>
                          setRows((arr) =>
                            arr.map((x) =>
                              x._k === row._k
                                ? { ...x, noteText: e.target.value }
                                : x,
                            ),
                          )
                        }
                        placeholder="VD: nums[0] + nums[1] = 2 + 7 = 9 nên trả về [0,1]."
                      />
                    </div>
                  )}
                  {row.error && (
                    <p className="mt-2 text-xs text-red-600">{row.error}</p>
                  )}
                </div>
              ))}
            </div>
          </section>

          <div className="flex justify-end gap-2">
            <Button
              variant="ghost"
              onClick={() => navigate('/admin/questions')}
            >
              Huỷ
            </Button>
            <Button type="submit" loading={submitting}>
              {mode === 'edit' ? 'Lưu thay đổi' : 'Tạo câu hỏi'}
            </Button>
          </div>
        </form>
      )}

      {aiOpen && (
        <AiGenerateModal
          onClose={() => setAiOpen(false)}
          onApply={applyGenerated}
        />
      )}
    </AdminShell>
  );
}

// ── AI generate modal ──────────────────────────────────────────────────────

function AiGenerateModal({
  onClose,
  onApply,
}: {
  onClose: () => void;
  onApply: (req: CodingQuestionRequest, warning?: string | null) => void;
}) {
  const [genMode, setGenMode] = useState<AiGenerateMode>('leetcode');
  const [leetcodeUrl, setLeetcodeUrl] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [tags, setTags] = useState<string[]>([]);
  const [optTime, setOptTime] = useState('');
  const [optSpace, setOptSpace] = useState('');
  // Raw input strings so the box can be cleared while typing — parsed in run().
  const [numTestcases, setNumTestcases] = useState('10');
  const [numVisible, setNumVisible] = useState('3');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  async function run() {
    setErr(null);
    const total = Number(numTestcases);
    const visible = Number(numVisible);
    if (
      !Number.isInteger(total) ||
      !Number.isInteger(visible) ||
      !(visible >= 1 && visible < total)
    ) {
      setErr('Cần 1 ≤ số test hiển thị < tổng số test.');
      return;
    }
    if (genMode === 'custom' && (!title.trim() || !description.trim())) {
      setErr('Chế độ custom cần tiêu đề và mô tả.');
      return;
    }
    if (genMode === 'leetcode' && !leetcodeUrl.trim()) {
      setErr('Nhập URL hoặc slug LeetCode.');
      return;
    }

    const body: AiGenerateRequest =
      genMode === 'leetcode'
        ? {
            mode: 'leetcode',
            leetcodeUrl: leetcodeUrl.trim(),
            numTestcases: total,
            numVisible: visible,
          }
        : {
            mode: 'custom',
            title: title.trim(),
            description: description.trim(),
            difficulty: difficulty || undefined,
            tags: tags.length ? tags : undefined,
            optimalTimeComplexity: optTime.trim() || undefined,
            optimalSpaceComplexity: optSpace.trim() || undefined,
            numTestcases: total,
            numVisible: visible,
          };

    setBusy(true);
    try {
      const res = await generateCoding(body);
      onApply(mapGeneratedToCodingRequest(res), res.warning);
    } catch (e) {
      setErr(
        e instanceof ApiError ? e.message : 'Sinh đề thất bại, thử lại sau.',
      );
      setBusy(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4"
      onClick={() => !busy && onClose()}
    >
      <div
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-surface-container-lowest p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="flex items-center gap-2 text-lg font-bold text-on-surface">
            <Sparkles className="h-5 w-5 text-secondary" />
            Sinh đề bằng AI
          </h3>
          <button
            type="button"
            onClick={() => !busy && onClose()}
            className="text-on-surface-variant hover:text-on-surface"
            aria-label="Đóng"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="space-y-4">
          <div className="flex gap-2">
            {(['leetcode', 'custom'] as AiGenerateMode[]).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => setGenMode(m)}
                className={`flex-1 rounded-xl border px-4 py-2 text-sm font-semibold transition ${
                  genMode === m
                    ? 'border-secondary bg-secondary text-on-secondary'
                    : 'border-outline-variant text-on-surface-variant hover:border-secondary'
                }`}
              >
                {m === 'leetcode' ? 'Từ LeetCode' : 'Tự mô tả'}
              </button>
            ))}
          </div>

          {genMode === 'leetcode' ? (
            <Field
              label="URL hoặc slug LeetCode"
              required
              hint="Ví dụ: two-sum hoặc https://leetcode.com/problems/two-sum/"
            >
              <Input
                value={leetcodeUrl}
                onChange={(e) => setLeetcodeUrl(e.target.value)}
                placeholder="two-sum"
              />
            </Field>
          ) : (
            <>
              <Field label="Tiêu đề" required>
                <Input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                />
              </Field>
              <Field label="Mô tả đề bài" required>
                <Textarea
                  rows={4}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                />
              </Field>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Độ khó">
                  <EnumSelect<Difficulty>
                    value={difficulty}
                    onChange={(v) => setDifficulty(v)}
                    options={DIFFICULTIES}
                    labels={DIFFICULTY_LABEL}
                    placeholder="— Để AI tự suy —"
                  />
                </Field>
                <Field label="Tags">
                  <TagInput value={tags} onChange={setTags} />
                </Field>
                <Field label="Time complexity">
                  <Input
                    value={optTime}
                    onChange={(e) => setOptTime(e.target.value)}
                    placeholder="O(n)"
                  />
                </Field>
                <Field label="Space complexity">
                  <Input
                    value={optSpace}
                    onChange={(e) => setOptSpace(e.target.value)}
                    placeholder="O(1)"
                  />
                </Field>
              </div>
            </>
          )}

          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Tổng số test" required>
              <Input
                type="number"
                min={2}
                value={numTestcases}
                onChange={(e) => setNumTestcases(e.target.value)}
              />
            </Field>
            <Field label="Số test hiển thị" required>
              <Input
                type="number"
                min={1}
                value={numVisible}
                onChange={(e) => setNumVisible(e.target.value)}
              />
            </Field>
          </div>

          {err && (
            <p className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">
              {err}
            </p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="ghost" onClick={onClose} disabled={busy}>
              Huỷ
            </Button>
            <Button onClick={run} loading={busy}>
              {busy ? 'Đang sinh đề…' : 'Sinh đề'}
            </Button>
          </div>
          {busy && (
            <p className="text-center text-xs text-on-surface-variant">
              Quá trình có thể mất ~30 giây, vui lòng đợi…
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
