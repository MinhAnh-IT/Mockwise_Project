import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowDown, ArrowUp, Plus, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  createBlueprint,
  getBlueprint,
  updateBlueprint,
} from '@/api/blueprint';
import {
  AdminShell,
  Button,
  EnumSelect,
  ErrorState,
  Field,
  Input,
  Select,
  Spinner,
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import {
  BLUEPRINT_ROLES,
  BLUEPRINT_ROLE_LABEL,
  IMPORTANCES,
  IMPORTANCE_LABEL,
  INTERVIEW_TYPES,
  INTERVIEW_TYPE_LABEL,
  LEVELS,
  LEVEL_LABEL,
  topicOptionsFor,
  type Blueprint,
  type BlueprintRequest,
  type BlueprintRole,
  type Importance,
  type InterviewType,
  type Level,
} from '@/types/blueprint';
import {
  DIFFICULTIES,
  DIFFICULTY_LABEL,
  type Difficulty,
} from '@/types/questionBank';

/** Working row in the topic builder. */
type TopicRow = {
  topicValue: string;
  importance: Importance;
  targetDifficulty: Difficulty;
};

type Errors = Partial<
  Record<'targetRole' | 'level' | 'interviewType' | 'topics' | 'budget' | 'time', string>
>;

const newRow = (): TopicRow => ({
  topicValue: '',
  importance: 'MED',
  targetDifficulty: 'MEDIUM',
});

export default function BlueprintFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const mode: 'create' | 'edit' = id ? 'edit' : 'create';

  const [targetRole, setTargetRole] = useState<BlueprintRole | ''>('');
  const [level, setLevel] = useState<Level | ''>('');
  const [interviewType, setInterviewType] = useState<InterviewType | ''>('');
  const [topics, setTopics] = useState<TopicRow[]>([]);
  const [questionBudget, setQuestionBudget] = useState(8);
  const [timeBudgetMinutes, setTimeBudgetMinutes] = useState(45);
  const [maxFollowUpsPerTopic, setMaxFollowUpsPerTopic] = useState(2);
  const [maxFollowUpsPerSession, setMaxFollowUpsPerSession] = useState(4);
  const [useAiSelector, setUseAiSelector] = useState(false);
  const [isDefault, setIsDefault] = useState(false);

  const [errors, setErrors] = useState<Errors>({});
  const [toast, setToast] = useState<ToastState>(null);
  const [loading, setLoading] = useState(mode === 'edit');
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const cfg = interviewType ? topicOptionsFor(interviewType) : null;
  const isCoding = interviewType === 'CODING';

  function fill(b: Blueprint) {
    setTargetRole(b.targetRole as BlueprintRole);
    setLevel(b.level as Level);
    setInterviewType(b.interviewType);
    setTopics(
      [...b.topics]
        .sort((x, y) => x.orderHint - y.orderHint)
        .map((t) => ({
          topicValue: t.topicValue ?? '',
          importance: t.importance,
          targetDifficulty: t.targetDifficulty,
        })),
    );
    setQuestionBudget(b.questionBudget);
    setTimeBudgetMinutes(b.timeBudgetMinutes);
    setMaxFollowUpsPerTopic(b.maxFollowUpsPerTopic);
    setMaxFollowUpsPerSession(b.maxFollowUpsPerSession);
    setUseAiSelector(b.useAiSelector);
    setIsDefault(b.isDefault);
  }

  useEffect(() => {
    if (mode !== 'edit' || !id) return;
    const fromState = (location.state as { record?: Blueprint } | null)?.record;
    if (fromState && fromState.id === id) {
      fill(fromState);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    getBlueprint(id)
      .then((b) => {
        if (!cancelled) fill(b);
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(err instanceof Error ? err.message : 'Không tải được blueprint.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // location.state is intentionally read once for the deep-link prefill.
  }, [id, mode, location.state]);

  function changeType(next: InterviewType) {
    if (next === interviewType) return;
    if (topics.length > 0 && !window.confirm('Đổi loại sẽ xoá danh sách chủ đề hiện tại?')) {
      return;
    }
    setInterviewType(next);
    setTopics([]);
    // Sensible follow-up defaults per type.
    if (next === 'CODING') {
      setMaxFollowUpsPerTopic(0);
      setMaxFollowUpsPerSession(0);
    } else {
      setMaxFollowUpsPerTopic(2);
      setMaxFollowUpsPerSession(4);
    }
  }

  const addTopic = () => setTopics((ts) => [...ts, newRow()]);
  const removeTopic = (i: number) => setTopics((ts) => ts.filter((_, idx) => idx !== i));
  const updateTopic = (i: number, patch: Partial<TopicRow>) =>
    setTopics((ts) => ts.map((t, idx) => (idx === i ? { ...t, ...patch } : t)));
  const moveTopic = (i: number, dir: -1 | 1) =>
    setTopics((ts) => {
      const j = i + dir;
      if (j < 0 || j >= ts.length) return ts;
      const copy = [...ts];
      [copy[i], copy[j]] = [copy[j], copy[i]];
      return copy;
    });

  function validate(): Errors {
    const e: Errors = {};
    if (!targetRole) e.targetRole = 'Chọn vị trí.';
    if (!level) e.level = 'Chọn cấp.';
    if (!interviewType) e.interviewType = 'Chọn loại phỏng vấn.';
    if (topics.length === 0) {
      e.topics = isCoding ? 'Cần ít nhất một slot.' : 'Cần ít nhất một chủ đề.';
    } else if (cfg?.structured && topics.some((t) => !t.topicValue)) {
      e.topics = 'Mỗi chủ đề phải chọn năng lực/lĩnh vực.';
    }
    if (!isCoding && questionBudget < 1) e.budget = 'Ngân sách câu hỏi phải ≥ 1.';
    if (timeBudgetMinutes < 1) e.time = 'Thời lượng phải ≥ 1 phút.';
    return e;
  }

  function buildBody(): BlueprintRequest {
    return {
      targetRole: targetRole as string,
      level: level as string,
      interviewType: interviewType as InterviewType,
      topics: topics.map((t, i) => ({
        kind: cfg?.kind ?? null,
        topicValue: isCoding ? null : t.topicValue,
        importance: t.importance,
        targetDifficulty: t.targetDifficulty,
        orderHint: i + 1,
      })),
      questionBudget: isCoding ? topics.length : questionBudget,
      timeBudgetMinutes,
      maxFollowUpsPerTopic: isCoding ? 0 : maxFollowUpsPerTopic,
      maxFollowUpsPerSession: isCoding ? 0 : maxFollowUpsPerSession,
      useAiSelector,
      isDefault,
    };
  }

  async function onSubmit(ev: React.FormEvent) {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    setSubmitting(true);
    try {
      if (mode === 'edit' && id) await updateBlueprint(id, buildBody());
      else await createBlueprint(buildBody());
      navigate('/admin/blueprints', {
        state: {
          flash: mode === 'edit' ? 'Đã cập nhật blueprint.' : 'Đã tạo blueprint.',
        },
      });
    } catch (err) {
      setToast({
        kind: 'error',
        text: err instanceof ApiError ? err.message : 'Lưu thất bại, vui lòng thử lại.',
      });
      setSubmitting(false);
    }
  }

  return (
    <AdminShell
      title={mode === 'edit' ? 'Sửa blueprint' : 'Tạo blueprint'}
      breadcrumb={[
        { label: 'Blueprint phỏng vấn', to: '/admin/blueprints' },
        { label: mode === 'edit' ? 'Sửa blueprint' : 'Tạo blueprint' },
      ]}
      actions={
        <Button variant="outline" onClick={() => navigate('/admin/blueprints')}>
          <ArrowLeft className="h-4 w-4" />
          Quay lại
        </Button>
      }
    >
      <Toast toast={toast} onClose={() => setToast(null)} />

      {loading ? (
        <Spinner label="Đang tải blueprint…" />
      ) : loadError ? (
        <ErrorState message={loadError} onRetry={() => navigate('/admin/blueprints')} />
      ) : (
        <form
          onSubmit={onSubmit}
          className="space-y-6 rounded-2xl border border-outline-variant bg-surface-container-lowest p-6"
        >
          {/* Identity */}
          <div className="grid gap-5 sm:grid-cols-3">
            <Field label="Vị trí" required error={errors.targetRole}>
              <EnumSelect<BlueprintRole>
                value={targetRole}
                onChange={(v) => setTargetRole(v)}
                options={[...BLUEPRINT_ROLES]}
                labels={BLUEPRINT_ROLE_LABEL}
                placeholder="— Chọn vị trí —"
              />
            </Field>
            <Field label="Cấp" required error={errors.level}>
              <EnumSelect<Level>
                value={level}
                onChange={(v) => setLevel(v)}
                options={LEVELS}
                labels={LEVEL_LABEL}
                placeholder="— Chọn cấp —"
              />
            </Field>
            <Field label="Loại phỏng vấn" required error={errors.interviewType}>
              <EnumSelect<InterviewType>
                value={interviewType}
                onChange={(v) => v && changeType(v)}
                options={INTERVIEW_TYPES}
                labels={INTERVIEW_TYPE_LABEL}
                placeholder="— Chọn loại —"
              />
            </Field>
          </div>

          {/* Topics / slots */}
          {interviewType && cfg && (
            <div>
              <div className="mb-2 flex items-center justify-between">
                <label className="text-sm font-semibold text-on-surface">
                  {isCoding ? 'Slot độ khó' : 'Chủ đề'}
                  <span className="ml-0.5 text-red-600">*</span>
                </label>
                <Button variant="outline" onClick={addTopic} className="!py-1.5 text-xs">
                  <Plus className="h-3.5 w-3.5" />
                  Thêm {cfg.noun}
                </Button>
              </div>
              {errors.topics && (
                <p className="mb-2 text-xs text-red-600">{errors.topics}</p>
              )}

              <div className="space-y-2">
                {topics.map((t, i) => (
                  <div
                    key={i}
                    className="flex flex-wrap items-center gap-2 rounded-xl border border-outline-variant bg-surface-container-low/40 p-2.5"
                  >
                    <span className="w-6 shrink-0 text-center text-xs font-semibold text-on-surface-variant">
                      #{i + 1}
                    </span>

                    {cfg.structured && (
                      <Select
                        value={t.topicValue}
                        onChange={(e) => updateTopic(i, { topicValue: e.target.value })}
                        className="!w-auto min-w-[12rem] flex-1"
                        aria-label="Năng lực / lĩnh vực"
                      >
                        <option value="">— Chọn —</option>
                        {cfg.options.map((o) => (
                          <option key={o} value={o}>
                            {cfg.labels[o]}
                          </option>
                        ))}
                      </Select>
                    )}

                    {!isCoding && (
                      <div className="w-32">
                        <EnumSelect<Importance>
                          value={t.importance}
                          onChange={(v) => v && updateTopic(i, { importance: v })}
                          options={IMPORTANCES}
                          labels={IMPORTANCE_LABEL}
                        />
                      </div>
                    )}

                    <div className="w-32">
                      <EnumSelect<Difficulty>
                        value={t.targetDifficulty}
                        onChange={(v) => v && updateTopic(i, { targetDifficulty: v })}
                        options={DIFFICULTIES}
                        labels={DIFFICULTY_LABEL}
                      />
                    </div>

                    <div className="ml-auto flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => moveTopic(i, -1)}
                        disabled={i === 0}
                        className="rounded-lg p-1.5 text-on-surface-variant transition hover:bg-surface-container hover:text-secondary disabled:opacity-30"
                        aria-label="Lên"
                      >
                        <ArrowUp className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => moveTopic(i, 1)}
                        disabled={i === topics.length - 1}
                        className="rounded-lg p-1.5 text-on-surface-variant transition hover:bg-surface-container hover:text-secondary disabled:opacity-30"
                        aria-label="Xuống"
                      >
                        <ArrowDown className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => removeTopic(i)}
                        className="rounded-lg p-1.5 text-on-surface-variant transition hover:bg-red-50 hover:text-red-600"
                        aria-label="Xoá"
                      >
                        <X className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
                {topics.length === 0 && (
                  <p className="rounded-xl border border-dashed border-outline-variant px-3 py-4 text-center text-xs text-on-surface-variant">
                    Chưa có {cfg.noun} nào. Bấm “Thêm {cfg.noun}”.
                  </p>
                )}
              </div>
            </div>
          )}

          {/* Budgets */}
          {interviewType && (
            <div className="grid gap-5 sm:grid-cols-2">
              <Field
                label="Ngân sách câu hỏi"
                error={errors.budget}
                hint={isCoding ? 'CODING: tự bằng số slot.' : undefined}
              >
                <Input
                  type="number"
                  min={1}
                  value={isCoding ? topics.length : questionBudget}
                  disabled={isCoding}
                  onChange={(e) => setQuestionBudget(Number(e.target.value))}
                />
              </Field>
              <Field label="Thời lượng (phút)" required error={errors.time}>
                <Input
                  type="number"
                  min={1}
                  value={timeBudgetMinutes}
                  onChange={(e) => setTimeBudgetMinutes(Number(e.target.value))}
                />
              </Field>
              {!isCoding && (
                <>
                  <Field label="Follow-up / chủ đề">
                    <Input
                      type="number"
                      min={0}
                      value={maxFollowUpsPerTopic}
                      onChange={(e) => setMaxFollowUpsPerTopic(Number(e.target.value))}
                    />
                  </Field>
                  <Field label="Follow-up / phiên">
                    <Input
                      type="number"
                      min={0}
                      value={maxFollowUpsPerSession}
                      onChange={(e) => setMaxFollowUpsPerSession(Number(e.target.value))}
                    />
                  </Field>
                </>
              )}
            </div>
          )}

          {/* Flags */}
          {interviewType && (
            <div className="space-y-3 border-t border-outline-variant pt-5">
              {!isCoding && (
                <label className="flex items-center gap-2.5 text-sm text-on-surface">
                  <input
                    type="checkbox"
                    checked={useAiSelector}
                    onChange={(e) => setUseAiSelector(e.target.checked)}
                    className="h-4 w-4 rounded border-outline-variant text-secondary focus:ring-secondary/30"
                  />
                  Dùng AI chọn câu kế tiếp
                </label>
              )}
              <label className="flex items-center gap-2.5 text-sm text-on-surface">
                <input
                  type="checkbox"
                  checked={isDefault}
                  onChange={(e) => setIsDefault(e.target.checked)}
                  className="h-4 w-4 rounded border-outline-variant text-secondary focus:ring-secondary/30"
                />
                Đặt làm mặc định cho (vị trí, cấp, loại) này
              </label>
              <p className="text-xs text-on-surface-variant">
                ⓘ Nếu bật, các blueprint mặc định khác cùng bộ ba sẽ tự bị bỏ cờ.
              </p>
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-outline-variant pt-5">
            <Button variant="ghost" onClick={() => navigate('/admin/blueprints')}>
              Huỷ
            </Button>
            <Button type="submit" loading={submitting}>
              {mode === 'edit' ? 'Lưu thay đổi' : 'Tạo blueprint'}
            </Button>
          </div>
        </form>
      )}
    </AdminShell>
  );
}
