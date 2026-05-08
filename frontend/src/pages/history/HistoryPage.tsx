import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CalendarClock,
  History as HistoryIcon,
  Loader2,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { listSessions } from '@/api/interviews';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type { InterviewType, SessionStatus, SessionSummaryView } from '@/types/interview';

const PAGE_SIZE = 10;

// BE InterviewType → FE practice URL slug. The report route lives at
// /practice/:type/session/:sid/report and the type slug must match what
// findPracticeOption knows about; MIXED isn't a separate practice option
// yet so we route it to behavioral as the closest fallback.
const TYPE_SLUG: Record<InterviewType, string> = {
  BEHAVIORAL: 'behavioral',
  CORE: 'core',
  MIXED: 'behavioral',
};

const TYPE_LABEL: Record<InterviewType, string> = {
  BEHAVIORAL: 'Hành vi',
  CORE: 'Chuyên môn',
  MIXED: 'Tổng hợp',
};

const TYPE_TONE: Record<InterviewType, string> = {
  BEHAVIORAL: 'bg-secondary-fixed text-on-secondary-fixed',
  CORE: 'bg-secondary/10 text-secondary',
  MIXED: 'bg-emerald-100 text-emerald-700',
};

const STATUS_LABEL: Record<SessionStatus, string> = {
  CREATED: 'Vừa tạo',
  IN_PROGRESS: 'Đang phỏng vấn',
  COMPLETED: 'Đang chấm điểm',
  CANCELLED: 'Đã huỷ',
  SCORED: 'Đã chấm xong',
};

const STATUS_TONE: Record<SessionStatus, string> = {
  CREATED: 'bg-surface-container text-on-surface-variant',
  IN_PROGRESS: 'bg-amber-50 text-amber-700',
  COMPLETED: 'bg-amber-100 text-amber-700',
  CANCELLED: 'bg-surface-container text-on-surface-variant',
  SCORED: 'bg-emerald-50 text-emerald-700',
};

const HIRE_LABEL: Record<string, string> = {
  strong_yes: 'Rất nên tuyển',
  yes: 'Đề xuất tuyển',
  weak_yes: 'Có thể tuyển',
  no: 'Không nên tuyển',
  strong_no: 'Rất không nên tuyển',
};

const HIRE_TONE: Record<string, string> = {
  strong_yes: 'bg-emerald-100 text-emerald-700',
  yes: 'bg-emerald-50 text-emerald-700',
  weak_yes: 'bg-amber-50 text-amber-700',
  no: 'bg-red-50 text-red-700',
  strong_no: 'bg-red-100 text-red-700',
};

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export default function HistoryPage() {
  const [items, setItems] = useState<SessionSummaryView[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listSessions(0, PAGE_SIZE)
      .then((res) => {
        if (cancelled) return;
        setItems(res.items);
        setPage(res.page);
        setHasNext(res.hasNext);
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) return;
        setError(err instanceof Error ? err.message : 'Không tải được lịch sử.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const onLoadMore = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    setError(null);
    try {
      const next = await listSessions(page + 1, PAGE_SIZE);
      setItems((prev) => [...prev, ...next.items]);
      setPage(next.page);
      setHasNext(next.hasNext);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) return;
      setError(err instanceof Error ? err.message : 'Không tải thêm được.');
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />
      <main className="flex-1 px-6 pt-24 pb-16">
        <div className="max-w-3xl mx-auto">
          <div className="mb-6">
            <p className="text-[10px] font-bold uppercase tracking-widest text-secondary mb-2">
              Lịch sử phỏng vấn
            </p>
            <h1 className="text-2xl md:text-3xl font-bold text-on-surface mb-1">
              Các phiên đã thực hiện
            </h1>
            <p className="text-sm text-on-surface-variant">
              Xem lại điểm số, đánh giá tổng quan và từng câu trả lời của các phiên trước đây.
            </p>
          </div>

          {loading && <LoadingState />}
          {!loading && error && items.length === 0 && <ErrorState message={error} />}
          {!loading && !error && items.length === 0 && <EmptyState />}

          {items.length > 0 && (
            <ul className="space-y-3">
              {items.map((s) => (
                <li key={s.sessionId}>
                  <SessionCard summary={s} />
                </li>
              ))}
            </ul>
          )}

          {hasNext && items.length > 0 && (
            <div className="mt-6 text-center">
              <button
                type="button"
                onClick={onLoadMore}
                disabled={loadingMore}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {loadingMore ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Đang tải…
                  </>
                ) : (
                  'Xem thêm'
                )}
              </button>
              {error && <p className="mt-3 text-xs text-red-600">{error}</p>}
            </div>
          )}
        </div>
      </main>
      <Footer />
    </div>
  );
}

function SessionCard({ summary }: { summary: SessionSummaryView }) {
  const slug = TYPE_SLUG[summary.interviewType] ?? 'behavioral';
  const dateText = DATE_FMT.format(
    new Date(summary.startedAt ?? summary.createdAt),
  );
  const score = summary.finalScore;

  return (
    <Link
      to={`/practice/${slug}/session/${summary.sessionId}/report`}
      className="block bg-surface-container-lowest border border-outline-variant rounded-2xl p-5 hover:border-secondary/60 hover:shadow-sm transition-all group"
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <span
              className={`inline-flex items-center text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded-md ${
                TYPE_TONE[summary.interviewType] ?? 'bg-surface-container text-on-surface-variant'
              }`}
            >
              {TYPE_LABEL[summary.interviewType] ?? summary.interviewType}
            </span>
            <span
              className={`inline-flex items-center text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded-md ${
                STATUS_TONE[summary.status] ?? 'bg-surface-container text-on-surface-variant'
              }`}
            >
              {STATUS_LABEL[summary.status] ?? summary.status}
            </span>
            {summary.hireSignal && (
              <span
                className={`inline-flex items-center text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded-md ${
                  HIRE_TONE[summary.hireSignal] ?? 'bg-surface-container text-on-surface-variant'
                }`}
              >
                {HIRE_LABEL[summary.hireSignal] ?? summary.hireSignal}
              </span>
            )}
          </div>
          <p className="text-base font-semibold text-on-surface truncate">
            {summary.targetRole} · {summary.level}
          </p>
          <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-on-surface-variant">
            <span className="inline-flex items-center gap-1">
              <CalendarClock className="w-3.5 h-3.5" />
              {dateText}
            </span>
            <span>
              {summary.questionCount} câu · {summary.timeBudgetMinutes} phút
            </span>
          </div>
        </div>

        <div className="flex flex-col items-end gap-1.5">
          {typeof score === 'number' ? (
            <div className="flex items-baseline gap-1.5">
              <span className="text-2xl font-extrabold text-on-surface leading-none">
                {score.toFixed(1)}
              </span>
              <span className="text-xs text-on-surface-variant font-medium">/10</span>
            </div>
          ) : (
            <span className="text-xs text-on-surface-variant italic">Chưa có điểm</span>
          )}
          {summary.grade && (
            <span className="px-2 py-0.5 rounded-full bg-secondary text-on-secondary text-[10px] font-bold">
              Grade {summary.grade}
            </span>
          )}
          <ArrowRight className="w-4 h-4 text-on-surface-variant opacity-0 group-hover:opacity-100 transition-opacity" />
        </div>
      </div>
    </Link>
  );
}

function LoadingState() {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-10 text-center">
      <Loader2 className="w-8 h-8 mx-auto mb-4 text-secondary animate-spin" />
      <p className="text-sm text-on-surface-variant">Đang tải lịch sử…</p>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="bg-red-50 border border-red-200 rounded-2xl p-6 text-center">
      <p className="text-sm text-red-700 font-semibold mb-1">Không tải được lịch sử</p>
      <p className="text-xs text-red-600 leading-relaxed">{message}</p>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-10 text-center">
      <div className="w-12 h-12 mx-auto mb-4 rounded-2xl bg-secondary/10 text-secondary flex items-center justify-center">
        <HistoryIcon className="w-6 h-6" />
      </div>
      <h3 className="text-base font-bold text-on-surface mb-2">Chưa có phiên nào</h3>
      <p className="text-xs text-on-surface-variant max-w-md mx-auto leading-relaxed mb-5">
        Bạn chưa thực hiện phỏng vấn nào. Bắt đầu một phiên thử để xem lại đánh giá ở đây.
      </p>
      <Link
        to="/practice"
        className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-secondary text-on-secondary text-sm font-semibold hover:opacity-90 transition-opacity"
      >
        Bắt đầu luyện tập
      </Link>
    </div>
  );
}
