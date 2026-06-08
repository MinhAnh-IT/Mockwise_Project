import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  createBehavioral,
  getQuestion,
  updateBehavioral,
} from '@/api/questionBank';
import {
  AdminShell,
  Button,
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
  COMPETENCIES,
  COMPETENCY_LABEL,
  DIFFICULTIES,
  DIFFICULTY_LABEL,
  type BehavioralQuestion,
  type BehavioralQuestionRequest,
  type Competency,
  type Difficulty,
} from '@/types/questionBank';

type Errors = Partial<
  Record<'difficulty' | 'competency' | 'text' | 'expectedSignals', string>
>;

export default function BehavioralFormPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const mode: 'create' | 'edit' = id ? 'edit' : 'create';

  const [difficulty, setDifficulty] = useState<Difficulty | ''>('');
  const [competency, setCompetency] = useState<Competency | ''>('');
  const [text, setText] = useState('');
  const [expectedSignals, setExpectedSignals] = useState<string[]>([]);
  const [tags, setTags] = useState<string[]>([]);
  const [audioKey, setAudioKey] = useState<string | null>(null);

  const [errors, setErrors] = useState<Errors>({});
  const [toast, setToast] = useState<ToastState>(null);
  const [loading, setLoading] = useState(mode === 'edit');
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function fill(q: BehavioralQuestion) {
    setDifficulty(q.difficulty);
    setCompetency(q.competency);
    setText(q.text);
    setExpectedSignals(q.expectedSignals ?? []);
    setTags(q.tags ?? []);
    setAudioKey(q.audioKey ?? null);
  }

  useEffect(() => {
    if (mode !== 'edit' || !id) return;
    const fromState = (location.state as { record?: BehavioralQuestion } | null)
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
        if (!cancelled) fill(q as BehavioralQuestion);
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
    // location.state is intentionally read once for the deep-link prefill.
  }, [id, mode, location.state]);

  function validate(): Errors {
    const e: Errors = {};
    if (!difficulty) e.difficulty = 'Chọn độ khó.';
    if (!competency) e.competency = 'Chọn năng lực.';
    if (!text.trim()) e.text = 'Nhập nội dung câu hỏi.';
    if (expectedSignals.length === 0)
      e.expectedSignals = 'Cần ít nhất một tín hiệu kỳ vọng.';
    return e;
  }

  async function onSubmit(ev: React.FormEvent) {
    ev.preventDefault();
    const e = validate();
    setErrors(e);
    if (Object.keys(e).length > 0) return;

    const body: BehavioralQuestionRequest = {
      difficulty: difficulty as Difficulty,
      competency: competency as Competency,
      text: text.trim(),
      expectedSignals,
      tags,
    };

    setSubmitting(true);
    try {
      if (mode === 'edit' && id) await updateBehavioral(id, body);
      else await createBehavioral(body);
      navigate('/admin/questions', {
        state: {
          flash:
            mode === 'edit'
              ? 'Đã cập nhật câu hỏi hành vi.'
              : 'Đã tạo câu hỏi hành vi.',
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
      title={mode === 'edit' ? 'Sửa câu hỏi Hành vi' : 'Tạo câu hỏi Hành vi'}
      breadcrumb={[
        { label: 'Ngân hàng câu hỏi', to: '/admin/questions' },
        { label: mode === 'edit' ? 'Sửa câu hỏi' : 'Tạo câu hỏi' },
      ]}
      actions={
        <Button variant="outline" onClick={() => navigate('/admin/questions')}>
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
          onRetry={() => navigate('/admin/questions')}
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
            <Field label="Năng lực" required error={errors.competency}>
              <EnumSelect<Competency>
                value={competency}
                onChange={(v) => setCompetency(v)}
                options={COMPETENCIES}
                labels={COMPETENCY_LABEL}
                placeholder="— Chọn năng lực —"
              />
            </Field>
          </div>

          <Field label="Nội dung câu hỏi" required error={errors.text}>
            <Textarea
              rows={4}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Ví dụ: Kể về một lần bạn phải xử lý xung đột trong nhóm…"
            />
          </Field>

          <Field
            label="Tín hiệu kỳ vọng"
            required
            hint="Các điểm bạn mong nghe được trong câu trả lời. Enter để thêm."
            error={errors.expectedSignals}
          >
            <TagInput
              value={expectedSignals}
              onChange={setExpectedSignals}
              placeholder="Nhập tín hiệu rồi Enter…"
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
    </AdminShell>
  );
}
