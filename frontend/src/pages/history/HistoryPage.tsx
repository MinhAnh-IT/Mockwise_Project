import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  CalendarClock,
  ChevronRight,
  History as HistoryIcon,
  Loader2,
  PlayCircle,
  Sparkles,
  Timer,
  TrendingUp,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { listSessions } from '@/api/interviews';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type { InterviewType, SessionStatus, SessionSummaryView } from '@/types/interview';
import { parseServerDate } from '@/lib/datetime';

const PAGE_SIZE = 10;

// BE InterviewType → FE practice URL slug. The report route lives at
// /practice/:type/session/:sid/report and the type slug must match what
// findPracticeOption knows about.
const TYPE_SLUG: Record<InterviewType, string> = {
  BEHAVIORAL: 'behavioral',
  CORE: 'core',
  CODING: 'coding',
};

const TYPE_LABEL: Record<InterviewType, string> = {
  BEHAVIORAL: 'Hành vi',
  CORE: 'Chuyên môn',
  CODING: 'Lập trình',
};

const TYPE_TONE: Record<InterviewType, string> = {
  BEHAVIORAL: 'bg-secondary-fixed text-on-secondary-fixed',
  CORE: 'bg-secondary/10 text-secondary',
  CODING: 'bg-emerald-100 text-emerald-700',
};

const STATUS_LABEL: Record<SessionStatus, string> = {
  CREATED: 'Vừa tạo',
  IN_PROGRESS: 'Đang phỏng vấn',
  COMPLETED: 'Đang chấm điểm',
  CANCELLED: 'Đã huỷ',
  SCORED: 'Đã chấm xong',
};

const STATUS_DOT: Record<SessionStatus, string> = {
  CREATED: 'bg-on-surface-variant/40',
  IN_PROGRESS: 'bg-amber-500 animate-pulse',
  COMPLETED: 'bg-amber-500 animate-pulse',
  CANCELLED: 'bg-on-surface-variant/40',
  SCORED: 'bg-emerald-500',
};

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

type TypeFilter = 'ALL' | InterviewType;

const TYPE_FILTER_OPTIONS: { value: TypeFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'BEHAVIORAL', label: 'Hành vi' },
  { value: 'CORE', label: 'Chuyên môn' },
  { value: 'CODING', label: 'Lập trình' },
];

export default function HistoryPage() {
  const [items, setItems] = useState<SessionSummaryView[]>([]);
  const [page, setPage] = useState(0);
  // totalCount comes back on every page; the FE derives hasNext from the
  // running offset (items already on screen) vs that total so we don't lean
  // on a server-supplied hasNext flag.
  const [totalCount, setTotalCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('ALL');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listSessions(0, PAGE_SIZE)
      .then((res) => {
        if (cancelled) return;
        setItems(res.items);
        setPage(0);
        setTotalCount(res.totalCount);
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

  const hasNext = totalCount !== null && items.length < totalCount;

  const onLoadMore = async () => {
    if (loadingMore || !hasNext) return;
    setLoadingMore(true);
    setError(null);
    try {
      const next = await listSessions(page + 1, PAGE_SIZE);
      setItems((prev) => [...prev, ...next.items]);
      setPage((prev) => prev + 1);
      setTotalCount(next.totalCount);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) return;
      setError(err instanceof Error ? err.message : 'Không tải thêm được.');
    } finally {
      setLoadingMore(false);
    }
  };

  // Derived stats over the *loaded* items. We can't aggregate the full
  // server-side history client-side, so these reflect "what you can see now."
  const stats = useMemo(() => {
    const scored = items.filter((i) => typeof i.finalScore === 'number');
    const avg =
      scored.length > 0
        ? scored.reduce((acc, x) => acc + (x.finalScore ?? 0), 0) / scored.length
        : null;
    const best =
      scored.length > 0
        ? Math.max(...scored.map((x) => x.finalScore as number))
        : null;
    return { scoredCount: scored.length, avg, best };
  }, [items]);

  const visibleItems = useMemo(
    () =>
      typeFilter === 'ALL'
        ? items
        : items.filter((i) => i.interviewType === typeFilter),
    [items, typeFilter],
  );

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <Header />
      <main className="flex-1 px-4 md:px-8 pt-24 pb-16">
        <div className="max-w-6xl mx-auto">
          <PageHero totalCount={totalCount} loading={loading} />

          <div className="grid lg:grid-cols-[280px_1fr] gap-6">
            <aside className="lg:sticky lg:top-24 lg:self-start space-y-4">
              <StatsCard
                totalCount={totalCount}
                loadedCount={items.length}
                scoredCount={stats.scoredCount}
                avg={stats.avg}
                best={stats.best}
                loading={loading}
              />
              <FilterCard value={typeFilter} onChange={setTypeFilter} />
              <Link
                to="/practice"
                className="flex items-center justify-between gap-3 bg-secondary text-on-secondary rounded-2xl px-5 py-4 font-semibold hover:opacity-90 transition-opacity shadow-sm"
              >
                <span className="flex items-center gap-2">
                  <PlayCircle className="w-5 h-5" />
                  Bắt đầu phiên mới
                </span>
                <ChevronRight className="w-4 h-4" />
              </Link>
            </aside>

            <section>
              {loading && <LoadingSkeleton />}
              {!loading && error && items.length === 0 && (
                <ErrorState message={error} />
              )}
              {!loading && !error && items.length === 0 && <EmptyState />}
              {!loading &&
                items.length > 0 &&
                visibleItems.length === 0 && (
                  <FilteredEmptyState
                    onReset={() => setTypeFilter('ALL')}
                  />
                )}

              {visibleItems.length > 0 && (
                <ul className="space-y-3">
                  {visibleItems.map((s) => (
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
                    className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl border border-outline-variant bg-surface-container-lowest text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
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
            </section>
          </div>
        </div>
      </main>
      <Footer />
    </div>
  );
}

function PageHero({
  totalCount,
  loading,
}: {
  totalCount: number | null;
  loading: boolean;
}) {
  return (
    <div className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-secondary-fixed via-surface-container-lowest to-surface-container-low border border-outline-variant/60 px-6 md:px-10 py-8 md:py-10 mb-6">
      <div className="absolute -right-12 -top-12 w-56 h-56 rounded-full bg-secondary/10 blur-3xl pointer-events-none" />
      <div className="absolute -left-16 -bottom-20 w-72 h-72 rounded-full bg-on-secondary-fixed/5 blur-3xl pointer-events-none" />

      <div className="relative flex flex-col md:flex-row md:items-end md:justify-between gap-5">
        <div>
          <div className="inline-flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-widest text-on-secondary-fixed/80 bg-on-secondary-fixed/5 border border-on-secondary-fixed/10 rounded-full px-2.5 py-1 mb-3">
            <HistoryIcon className="w-3 h-3" />
            Lịch sử phỏng vấn
          </div>
          <h1 className="text-2xl md:text-4xl font-extrabold text-on-surface tracking-tight mb-2">
            Các phiên bạn đã thực hiện
          </h1>
          <p className="text-sm md:text-base text-on-surface-variant max-w-xl leading-relaxed">
            Xem lại điểm số, đánh giá tổng quan và từng câu trả lời — theo dõi
            tiến bộ qua từng phiên luyện tập.
          </p>
        </div>
        <div className="shrink-0">
          {!loading && typeof totalCount === 'number' && (
            <div className="bg-surface-container-lowest/80 backdrop-blur border border-outline-variant rounded-2xl px-5 py-4 flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-secondary/10 text-secondary flex items-center justify-center">
                <Sparkles className="w-5 h-5" />
              </div>
              <div>
                <p className="text-2xl font-extrabold text-on-surface leading-none">
                  {totalCount}
                </p>
                <p className="text-[11px] text-on-surface-variant font-medium mt-1">
                  phiên đã tạo
                </p>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function StatsCard({
  totalCount,
  loadedCount,
  scoredCount,
  avg,
  best,
  loading,
}: {
  totalCount: number | null;
  loadedCount: number;
  scoredCount: number;
  avg: number | null;
  best: number | null;
  loading: boolean;
}) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-5">
      <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-3">
        Thống kê nhanh
      </p>
      <dl className="space-y-3">
        <StatRow
          icon={<HistoryIcon className="w-4 h-4" />}
          label="Tổng số phiên"
          value={loading ? '—' : (totalCount ?? loadedCount).toString()}
        />
        <StatRow
          icon={<TrendingUp className="w-4 h-4" />}
          label="Đã chấm"
          value={loading ? '—' : scoredCount.toString()}
          hint={scoredCount > 0 ? 'trong số đã tải' : undefined}
        />
        <StatRow
          icon={<Sparkles className="w-4 h-4" />}
          label="Điểm TB"
          value={avg !== null ? avg.toFixed(1) : '—'}
        />
        <StatRow
          icon={<Timer className="w-4 h-4" />}
          label="Điểm cao nhất"
          value={best !== null ? best.toFixed(1) : '—'}
        />
      </dl>
    </div>
  );
}

function StatRow({
  icon,
  label,
  value,
  hint,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  hint?: string;
}) {
  return (
    <div className="flex items-center gap-3">
      <div className="w-8 h-8 rounded-lg bg-surface-container text-on-surface-variant flex items-center justify-center shrink-0">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[11px] text-on-surface-variant font-medium leading-none">
          {label}
        </p>
        <p className="text-base font-bold text-on-surface mt-1 tabular-nums">
          {value}
          {hint && (
            <span className="text-[10px] text-on-surface-variant font-medium ml-1.5">
              {hint}
            </span>
          )}
        </p>
      </div>
    </div>
  );
}

function FilterCard({
  value,
  onChange,
}: {
  value: TypeFilter;
  onChange: (v: TypeFilter) => void;
}) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-5">
      <p className="text-[10px] font-bold uppercase tracking-widest text-on-surface-variant mb-3">
        Lọc theo loại
      </p>
      <div className="flex flex-wrap gap-1.5">
        {TYPE_FILTER_OPTIONS.map((opt) => {
          const active = opt.value === value;
          return (
            <button
              key={opt.value}
              type="button"
              onClick={() => onChange(opt.value)}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                active
                  ? 'bg-secondary text-on-secondary'
                  : 'bg-surface-container text-on-surface-variant hover:bg-surface-container-high'
              }`}
            >
              {opt.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function SessionCard({ summary }: { summary: SessionSummaryView }) {
  const slug = TYPE_SLUG[summary.interviewType] ?? 'behavioral';
  const dateText = DATE_FMT.format(
    parseServerDate(summary.startedAt ?? summary.createdAt),
  );
  const score = summary.finalScore;

  return (
    <Link
      to={`/practice/${slug}/session/${summary.sessionId}/report`}
      className="group block bg-surface-container-lowest border border-outline-variant rounded-2xl p-5 hover:border-secondary/60 hover:shadow-md hover:-translate-y-0.5 transition-all"
    >
      <div className="flex items-center gap-4 md:gap-5">
        <ScoreRing score={score} size={64} stroke={6} />

        <div className="flex-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2 mb-1.5">
            <span
              className={`inline-flex items-center text-[10px] font-bold uppercase tracking-widest px-2 py-1 rounded-md ${
                TYPE_TONE[summary.interviewType] ??
                'bg-surface-container text-on-surface-variant'
              }`}
            >
              {TYPE_LABEL[summary.interviewType] ?? summary.interviewType}
            </span>
            <span className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-on-surface-variant">
              <span
                className={`w-1.5 h-1.5 rounded-full ${
                  STATUS_DOT[summary.status] ?? 'bg-on-surface-variant/40'
                }`}
              />
              {STATUS_LABEL[summary.status] ?? summary.status}
            </span>
          </div>
          <p className="text-base md:text-lg font-bold text-on-surface truncate">
            {summary.targetRole}
            <span className="text-on-surface-variant font-medium">
              {' · '}
              {summary.level}
            </span>
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-on-surface-variant">
            <span className="inline-flex items-center gap-1">
              <CalendarClock className="w-3.5 h-3.5" />
              {dateText}
            </span>
            <span className="inline-flex items-center gap-1">
              <Sparkles className="w-3.5 h-3.5" />
              {summary.questionCount} câu
            </span>
            <span className="inline-flex items-center gap-1">
              <Timer className="w-3.5 h-3.5" />
              {summary.timeBudgetMinutes} phút
            </span>
          </div>
        </div>

        <div className="hidden md:flex flex-col items-end gap-1.5 shrink-0">
          {summary.grade && (
            <span className="px-2.5 py-1 rounded-full bg-secondary text-on-secondary text-[10px] font-bold tracking-wider">
              Grade {summary.grade}
            </span>
          )}
          {summary.hireSignal && (
            <span className="text-[10px] font-semibold text-on-surface-variant uppercase tracking-wider">
              {summary.hireSignal.replace(/_/g, ' ')}
            </span>
          )}
        </div>

        <ArrowRight className="hidden md:block w-5 h-5 text-on-surface-variant group-hover:text-secondary group-hover:translate-x-0.5 transition-all shrink-0" />
      </div>
    </Link>
  );
}

function ScoreRing({
  score,
  size,
  stroke,
}: {
  score: number | null;
  size: number;
  stroke: number;
}) {
  const radius = (size - stroke) / 2;
  const circ = 2 * Math.PI * radius;
  const pct =
    typeof score === 'number' ? Math.max(0, Math.min(1, score / 10)) : 0;
  const tone = scoreTone(score);
  return (
    <div
      className="relative shrink-0"
      style={{ width: size, height: size }}
      aria-label={
        typeof score === 'number' ? `Điểm ${score.toFixed(1)} / 10` : 'Chưa có điểm'
      }
    >
      <svg viewBox={`0 0 ${size} ${size}`} className="w-full h-full -rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="currentColor"
          className="text-surface-container"
          strokeWidth={stroke}
        />
        {typeof score === 'number' && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke="currentColor"
            className={tone.ring}
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={circ}
            strokeDashoffset={circ * (1 - pct)}
          />
        )}
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        {typeof score === 'number' ? (
          <>
            <span className={`text-base font-extrabold leading-none ${tone.text}`}>
              {score.toFixed(1)}
            </span>
            <span className="text-[9px] text-on-surface-variant font-semibold mt-0.5">
              / 10
            </span>
          </>
        ) : (
          <span className="text-[10px] text-on-surface-variant font-semibold leading-tight text-center px-1">
            chưa<br />chấm
          </span>
        )}
      </div>
    </div>
  );
}

function scoreTone(score: number | null) {
  if (typeof score !== 'number') {
    return { ring: 'text-on-surface-variant/30', text: 'text-on-surface-variant' };
  }
  if (score >= 8) return { ring: 'text-emerald-500', text: 'text-emerald-700' };
  if (score >= 6.5) return { ring: 'text-secondary', text: 'text-secondary' };
  if (score >= 5) return { ring: 'text-amber-500', text: 'text-amber-700' };
  return { ring: 'text-red-500', text: 'text-red-700' };
}

function LoadingSkeleton() {
  return (
    <ul className="space-y-3" aria-busy="true">
      {[0, 1, 2].map((i) => (
        <li
          key={i}
          className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-5"
        >
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-full bg-surface-container animate-pulse shrink-0" />
            <div className="flex-1 space-y-2">
              <div className="flex gap-2">
                <div className="h-5 w-16 rounded-md bg-surface-container animate-pulse" />
                <div className="h-5 w-20 rounded-md bg-surface-container animate-pulse" />
              </div>
              <div className="h-5 w-2/3 rounded bg-surface-container animate-pulse" />
              <div className="h-3 w-1/2 rounded bg-surface-container animate-pulse" />
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div className="bg-red-50 border border-red-200 rounded-2xl p-6 text-center">
      <p className="text-sm text-red-700 font-semibold mb-1">
        Không tải được lịch sử
      </p>
      <p className="text-xs text-red-600 leading-relaxed">{message}</p>
    </div>
  );
}

function EmptyState() {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-10 md:p-14 text-center">
      <div className="w-14 h-14 mx-auto mb-4 rounded-2xl bg-secondary/10 text-secondary flex items-center justify-center">
        <HistoryIcon className="w-7 h-7" />
      </div>
      <h3 className="text-lg font-bold text-on-surface mb-2">
        Chưa có phiên nào
      </h3>
      <p className="text-sm text-on-surface-variant max-w-md mx-auto leading-relaxed mb-6">
        Bạn chưa thực hiện phỏng vấn nào. Bắt đầu một phiên thử để xem lại
        đánh giá ở đây.
      </p>
      <Link
        to="/practice"
        className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-secondary text-on-secondary text-sm font-semibold hover:opacity-90 transition-opacity"
      >
        <PlayCircle className="w-4 h-4" />
        Bắt đầu luyện tập
      </Link>
    </div>
  );
}

function FilteredEmptyState({ onReset }: { onReset: () => void }) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl p-8 text-center">
      <p className="text-sm font-semibold text-on-surface mb-1">
        Không có phiên phù hợp với bộ lọc
      </p>
      <p className="text-xs text-on-surface-variant mb-4">
        Thử bỏ lọc để xem lại tất cả các phiên đã tải.
      </p>
      <button
        type="button"
        onClick={onReset}
        className="text-xs font-semibold text-secondary hover:underline"
      >
        Bỏ lọc
      </button>
    </div>
  );
}
