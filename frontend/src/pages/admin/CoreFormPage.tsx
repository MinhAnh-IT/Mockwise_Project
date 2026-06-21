import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { ApiError } from '@/api/client';
import { createCore, getQuestion, updateCore } from '@/api/questionBank';
import {
  AdminShell,
  Button,
  ChipMultiSelect,
  EnumSelect,
  ErrorState,
  Field,
  Spinner,
  TagInput,
  Textarea,
  Toast,
  type ToastState,
} from '@/components/admin/ui';
import QuestionAudioRow from '@/components/admin/QuestionAudio';
import {
  DIFFICULTIES,
  DIFFICULTY_LABEL,
  DOMAINS,
  DOMAIN_LABEL,
  TARGET_ROLES,
  TARGET_ROLE_LABEL,
  type CoreQuestion,
  type CoreQuestionRequest,
  type Difficulty,
  type Domain,
  type TargetRole,
} from '@/types/questionBank';

type Errors = Partial<
  Record<
    'difficulty' | 'domain' | 'targetRoles' | 'text' | 'keyConcepts' | 'depthExpected',
    string
  >
>;

export default function CoreFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const mode: 'create' | 'edit' = id ? 'edit' : 'create';

  // Return to the list with the tab/filters the user came from (passed as `from`).
  const backTo =
    '/admin/questions' +
    ((location.state as { from?: string } | null)?.from ?? '');

  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [domain, setDomain] = useState<Domain | ''>('');
  const [targetRoles, setTargetRoles] = useState<TargetRole[]>([]);
  const [text, setText] = useState('');
  const [keyConcepts, setKeyConcepts] = useState<string[]>([]);
  const [depthExpected, setDepthExpected] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [audioKey, setAudioKey] = useState<string | null>(null);

  const [errors, setErrors] = useState<Errors>({});
  const [toast, setToast] = useState<ToastState>(null);
  const [loading, setLoading] = useState(mode === 'edit');
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function fill(q: CoreQuestion) {
    setDifficulty(q.difficulty);
    setDomain(q.domain);
    setTargetRoles(q.targetRoles ?? []);
    setText(q.text);
    setKeyConcepts(q.keyConcepts ?? []);
    setDepthExpected(q.depthExpected ?? '');
    setTags(q.tags ?? []);
    setAudioKey(q.audioKey ?? null);
  }

  useEffect(() => {
    if (mode !== 'edit' || !id) return;
    const fromState = (location.state as { record?: CoreQuestion } | null)
      ?.record;
    if (fromState && fromState.id === id) {
      fill(fromState);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    getQuestion(id)
      .then((q) => {
        if (!cancelled) fill(q as CoreQuestion);
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

  function validate(): Errors {
    const e: Errors = {};
    if (!difficulty) e.difficulty = 'Chọn độ khó.';
    if (!domain) e.domain = 'Chọn lĩnh vực.';
    if (targetRoles.length === 0)
      e.targetRoles = 'Chọn ít nhất một vị trí mục tiêu.';
    if (!text.trim()) e.text = 'Nhập nội dung câu hỏi.';
    if (keyConcepts.length === 0)
      e.keyConcepts = 'Cần ít nhất một khái niệm trọng tâm.';
    if (!depthExpected.trim())
      e.depthExpected = 'Mô tả mức độ chuyên sâu mong đợi.';
    return e;
  }

  async function onSubmit(ev: React.FormEvent) {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    const body: CoreQuestionRequest = {
      difficulty: difficulty as Difficulty,
      domain: domain as Domain,
      targetRoles,
      text: text.trim(),
      keyConcepts,
      depthExpected: depthExpected.trim(),
      tags,
    };

    setSubmitting(true);
    try {
      if (mode === 'edit' && id) await updateCore(id, body);
      else await createCore(body);
      navigate(backTo, {
        state: {
          flash:
            mode === 'edit'
              ? 'Đã cập nhật câu hỏi chuyên môn.'
              : 'Đã tạo câu hỏi chuyên môn.',
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

  return (
    <AdminShell
      title={mode === 'edit' ? 'Sửa câu hỏi Chuyên môn' : 'Tạo câu hỏi Chuyên môn'}
      breadcrumb={[
        { label: 'Ngân hàng câu hỏi', to: backTo },
        { label: mode === 'edit' ? 'Sửa câu hỏi' : 'Tạo câu hỏi' },
      ]}
      actions={
        <Button variant="outline" onClick={() => navigate(backTo)}>
          <ArrowLeft className="h-4 w-4" />
          Quay lại
        </Button>
      }
    >
      <Toast toast={toast} onClose={() => setToast(null)} />

      {loading ? (
        <Spinner label="Đang tải câu hỏi…" />
      ) : loadError ? (
        <ErrorState
          message={loadError}
          onRetry={() => navigate(backTo)}
        />
      ) : (
        <form
          onSubmit={onSubmit}
          className="space-y-5 rounded-2xl border border-outline-variant bg-surface-container-lowest p-6"
        >
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="Độ khó" required error={errors.difficulty}>
              <EnumSelect<Difficulty>
                value={difficulty}
                onChange={(v) => setDifficulty(v)}
                options={DIFFICULTIES}
                labels={DIFFICULTY_LABEL}
                placeholder="— Chọn độ khó —"
              />
            </Field>
            <Field label="Lĩnh vực" required error={errors.domain}>
              <EnumSelect<Domain>
                value={domain}
                onChange={(v) => setDomain(v)}
                options={DOMAINS}
                labels={DOMAIN_LABEL}
                placeholder="— Chọn lĩnh vực —"
              />
            </Field>
          </div>

          <Field
            label="Vị trí mục tiêu"
            required
            hint="Câu hỏi áp dụng cho những vị trí nào."
            error={errors.targetRoles}
          >
            <ChipMultiSelect<TargetRole>
              value={targetRoles}
              onChange={setTargetRoles}
              options={TARGET_ROLES}
              labels={TARGET_ROLE_LABEL}
            />
          </Field>

          <Field label="Nội dung câu hỏi" required error={errors.text}>
            <Textarea
              rows={4}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Ví dụ: Giải thích sự khác nhau giữa process và thread…"
            />
          </Field>

          <Field
            label="Khái niệm trọng tâm"
            required
            hint="Những khái niệm cốt lõi cần đề cập. Enter để thêm."
            error={errors.keyConcepts}
          >
            <TagInput
              value={keyConcepts}
              onChange={setKeyConcepts}
              placeholder="Nhập khái niệm rồi Enter…"
            />
          </Field>

          <Field
            label="Mức độ chuyên sâu mong đợi"
            required
            hint="Mô tả độ sâu của một câu trả lời đạt yêu cầu."
            error={errors.depthExpected}
          >
            <Textarea
              rows={3}
              value={depthExpected}
              onChange={(e) => setDepthExpected(e.target.value)}
              placeholder="Ví dụ: Cần nêu được cơ chế lập lịch, context switch và trade-off…"
            />
          </Field>

          <Field label="Tags" hint="Tuỳ chọn — dùng để lọc.">
            <TagInput value={tags} onChange={setTags} />
          </Field>

          {mode === 'edit' && id && (
            <Field
              label="Âm thanh (TTS)"
              hint="Nghe thử bản đọc câu hỏi. “Tạo lại” dùng nội dung đã lưu; sửa nội dung rồi Lưu sẽ tự tạo lại."
            >
              <QuestionAudioRow
                questionId={id}
                audioKey={audioKey}
                onToast={setToast}
                onUpdated={setAudioKey}
              />
            </Field>
          )}

          <div className="flex justify-end gap-2 border-t border-outline-variant pt-5">
            <Button
              variant="ghost"
              onClick={() => navigate(backTo)}
            >
              Huỷ
            </Button>
            <Button type="submit" loading={submitting}>
              {mode === 'edit' ? 'Lưu thay đổi' : 'Tạo câu hỏi'}
            </Button>
          </div>
        </form>
      )}
    </AdminShell>
  );
}
