import { useState } from 'react';
import { AlertTriangle, ArrowLeft, CheckCircle2, Clock, Loader2, ListChecks } from 'lucide-react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '@/api/client';
import { startSession } from '@/api/interviews';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import { findPracticeOption, type PracticeOption } from '@/data/practice';

/**
 * Readiness screen for a practice type. Shown after the user picks a type on
 * /practice and before the live session begins. Length and question count
 * come from the catalog today; once the interview-orchestrator service exists
 * they'll come from the session-create response.
 */
export default function PracticeIntroPage() {
  const { type } = useParams<{ type: string }>();
  const option = type ? findPracticeOption(type) : undefined;
  const navigate = useNavigate();
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!option) {
    return <Navigate to="/practice" replace />;
  }

  const handleStart = async () => {
    if (!option.interviewType) return;
    setStarting(true);
    setError(null);
    try {
      const output = await startSession({ interviewType: option.interviewType });
      navigate(`/practice/${option.id}/session/${output.sessionId}`, {
        state: { bootstrap: output },
      });
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : 'Không bắt đầu được phiên phỏng vấn.';
      setError(msg);
      setStarting(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />

      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-2xl mx-auto">
          <Link
            to="/practice"
            className="inline-flex items-center gap-1 text-sm font-medium text-on-surface-variant hover:text-on-surface mb-4 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Quay lại
          </Link>

          <Heading option={option} />

          <SessionMeta option={option} />

          <Checklist items={option.readiness.checklist} />

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-4 mb-4 flex items-start gap-3">
              <AlertTriangle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
              <p className="text-xs text-red-700 leading-relaxed">{error}</p>
            </div>
          )}

          <StartCta
            disabled={option.interviewType === null}
            loading={starting}
            onStart={handleStart}
          />
        </div>
      </main>

      <Footer />
    </div>
  );
}

function Heading({ option }: { option: PracticeOption }) {
  return (
    <div className="flex items-start gap-4 mb-8">
      <div
        className={`w-14 h-14 rounded-2xl flex items-center justify-center flex-shrink-0 ${option.accentClassName}`}
      >
        <option.Icon className="w-7 h-7" />
      </div>
      <div>
        <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-1">{option.title}</h1>
        <p className="text-on-surface-variant text-sm leading-relaxed">
          {option.readiness.longDescription}
        </p>
      </div>
    </div>
  );
}

function SessionMeta({ option }: { option: PracticeOption }) {
  return (
    <div className="grid grid-cols-2 gap-3 mb-8">
      <MetaCard
        icon={<Clock className="w-4 h-4" />}
        label="Thời lượng dự kiến"
        value={option.readiness.estimatedMinutes}
      />
      <MetaCard
        icon={<ListChecks className="w-4 h-4" />}
        label="Số câu hỏi"
        value={option.readiness.questionCount}
      />
    </div>
  );
}

function MetaCard({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-4">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-on-surface-variant mb-1">
        {icon}
        {label}
      </div>
      <p className="text-base font-bold text-on-surface">{value}</p>
    </div>
  );
}

function Checklist({ items }: { items: string[] }) {
  return (
    <section
      aria-labelledby="readiness-heading"
      className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-6 mb-6"
    >
      <h2 id="readiness-heading" className="text-sm font-bold text-on-surface mb-4">
        Trước khi bắt đầu
      </h2>
      <ul className="space-y-3">
        {items.map((item) => (
          <li key={item} className="flex items-start gap-3 text-sm text-on-surface">
            <CheckCircle2 className="w-5 h-5 text-secondary flex-shrink-0 mt-0.5" />
            {item}
          </li>
        ))}
      </ul>
    </section>
  );
}

function StartCta({
  disabled,
  loading,
  onStart,
}: {
  disabled: boolean;
  loading: boolean;
  onStart: () => void;
}) {
  if (disabled) {
    return (
      <button
        type="button"
        disabled
        className="w-full py-3 rounded-xl bg-secondary/40 text-on-secondary font-semibold cursor-not-allowed"
        title="Loại phỏng vấn này đang được phát triển"
      >
        Bắt đầu (sắp có)
      </button>
    );
  }
  return (
    <button
      type="button"
      onClick={onStart}
      disabled={loading}
      className="w-full py-3 rounded-xl bg-secondary text-on-secondary font-semibold hover:bg-secondary-container transition-colors inline-flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed"
    >
      {loading && <Loader2 className="w-4 h-4 animate-spin" />}
      {loading ? 'Đang khởi tạo phiên…' : 'Bắt đầu phỏng vấn'}
    </button>
  );
}
