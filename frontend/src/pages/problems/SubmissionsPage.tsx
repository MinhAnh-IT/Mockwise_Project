import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import {
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Clock,
  Code2,
  Flame,
  Layers,
  Loader2,
  Search,
  Target,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  getStats,
  getSubmission,
  listProblemGroups,
  listSubmissions,
} from '@/api/practice';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type {
  ProblemSubmissionGroup,
  SubmissionDetail,
  SubmissionSummary,
  UserStats,
} from '@/types/practice';

const VERDICT_LABEL: Record<string, string> = {
  AC: 'Accepted',
  WA: 'Wrong Answer',
  TLE: 'Time Limit',
  MLE: 'Memory Limit',
  RE: 'Runtime Error',
  CE: 'Compile Error',
};

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: 'Easy',
  MEDIUM: 'Medium',
  HARD: 'Hard',
};

const DIFFICULTY_TONE: Record<string, string> = {
  EASY: 'text-emerald-600',
  MEDIUM: 'text-amber-600',
  HARD: 'text-rose-600',
};

const LANG_LABEL: Record<string, string> = {
  python: 'Python',
  java: 'Java',
  javascript: 'JavaScript',
  typescript: 'TypeScript',
  cpp: 'C++',
  c: 'C',
  csharp: 'C#',
  go: 'Go',
  rust: 'Rust',
  kotlin: 'Kotlin',
};

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const langLabel = (l: string) => LANG_LABEL[l] ?? l.charAt(0).toUpperCase() + l.slice(1);

function verdictTone(verdict: string | null, status: string): string {
  if (status === 'FAILED') return 'text-rose-600';
  if (status !== 'DONE') return 'text-amber-600';
  if (verdict === 'AC') return 'text-emerald-600';
  return 'text-rose-600';
}

function verdictText(s: SubmissionSummary): string {
  if (s.status === 'FAILED') return 'Lỗi hệ thống';
  if (s.status === 'PENDING' || s.status === 'JUDGING') return 'Đang chấm…';
  return s.verdict ? VERDICT_LABEL[s.verdict] ?? s.verdict : '—';
}

type GroupFilter = 'ALL' | 'SOLVED' | 'ATTEMPTED';
const GROUP_PAGE_SIZE = 15;

export default function SubmissionsPage() {
  const [stats, setStats] = useState<UserStats | null>(null);
  const [groups, setGroups] = useState<ProblemSubmissionGroup[]>([]);
  const [recent, setRecent] = useState<SubmissionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [filter, setFilter] = useState<GroupFilter>('ALL');
  const [search, setSearch] = useState('');
  const [visible, setVisible] = useState(GROUP_PAGE_SIZE);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all([
      listProblemGroups(),
      listSubmissions({ size: 8 }),
      getStats().catch(() => null), // best-effort; never blocks the page
    ])
      .then(([g, r, s]) => {
        if (cancelled) return;
        setGroups(g);
        setRecent(r.content);
        setStats(s);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof ApiError ? err.message : 'Không tải được tiến độ.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return groups.filter((g) => {
      if (filter === 'SOLVED' && !g.solved) return false;
      if (filter === 'ATTEMPTED' && g.solved) return false;
      if (q && !(g.problemTitle ?? '').toLowerCase().includes(q)) return false;
      return true;
    });
  }, [groups, filter, search]);

  useEffect(() => {
    setVisible(GROUP_PAGE_SIZE);
  }, [filter, search]);

  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <Header />
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 sm:px-6 pb-24 pt-28">
        <div className="mb-6 flex items-center gap-3">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-secondary/10 text-secondary">
            <Target className="h-6 w-6" />
          </span>
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-on-surface">Tiến độ của tôi</h1>
            <p className="text-sm text-on-surface-variant">
              Tổng quan kết quả, nhóm theo bài và lịch sử nộp.
            </p>
          </div>
          <Link
            to="/problems"
            className="ml-auto shrink-0 rounded-xl border border-outline-variant px-3.5 py-2 text-sm font-semibold text-on-surface-variant transition-colors hover:text-on-surface"
          >
            Danh sách đề
          </Link>
        </div>

        {loading ? (
          <div className="flex items-center justify-center gap-2 py-24 text-sm text-on-surface-variant">
            <Loader2 className="h-4 w-4 animate-spin" /> Đang tải…
          </div>
        ) : error ? (
          <div className="py-24 text-center text-sm text-rose-600">{error}</div>
        ) : (
          <div className="space-y-6">
            {stats && <ProgressHero stats={stats} />}

            {stats && (
              <div className="grid gap-6 md:grid-cols-2">
                <BreakdownCard
                  title="Ngôn ngữ"
                  icon={<Code2 className="h-4 w-4" />}
                  data={stats.solvedByLanguage}
                  labelFn={langLabel}
                  emptyText="Chưa giải bài nào."
                />
                <BreakdownCard
                  title="Kỹ năng / Chủ đề"
                  icon={<Layers className="h-4 w-4" />}
                  data={stats.solvedByTag}
                  labelFn={(t) => t}
                  emptyText="Chưa có chủ đề nào được giải."
                  limit={12}
                />
              </div>
            )}

            {/* Grouped by problem */}
            <section>
              <div className="mb-3 flex flex-col gap-3 sm:flex-row sm:items-center">
                <h2 className="text-lg font-bold text-on-surface">
                  Bài đã làm{' '}
                  <span className="text-sm font-medium text-on-surface-variant">
                    ({filtered.length})
                  </span>
                </h2>
                <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
                  <div className="inline-flex rounded-xl border border-outline-variant p-0.5">
                    {(['ALL', 'SOLVED', 'ATTEMPTED'] as GroupFilter[]).map((f) => (
                      <button
                        key={f}
                        type="button"
                        onClick={() => setFilter(f)}
                        className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                          filter === f
                            ? 'bg-on-surface text-surface'
                            : 'text-on-surface-variant hover:text-on-surface'
                        }`}
                      >
                        {f === 'ALL' ? 'Tất cả' : f === 'SOLVED' ? 'Đã giải' : 'Đang thử'}
                      </button>
                    ))}
                  </div>
                  <div className="relative w-full sm:w-44">
                    <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
                    <input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder="Tìm bài…"
                      className="w-full rounded-full border border-outline-variant bg-surface-container-lowest py-1.5 pl-8 pr-3 text-sm text-on-surface outline-none focus:border-secondary"
                    />
                  </div>
                </div>
              </div>

              <div className="overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-sm">
                {filtered.length === 0 ? (
                  <div className="py-16 text-center text-sm text-on-surface-variant">
                    Chưa có bài nào khớp. Hãy chọn một đề và bắt đầu!
                  </div>
                ) : (
                  <ul>
                    {filtered.slice(0, visible).map((g) => (
                      <ProblemGroupRow key={g.problemId} group={g} />
                    ))}
                  </ul>
                )}
              </div>

              {visible < filtered.length && (
                <div className="mt-4 text-center">
                  <button
                    type="button"
                    onClick={() => setVisible((v) => v + GROUP_PAGE_SIZE)}
                    className="rounded-xl border border-outline-variant px-4 py-2 text-sm font-semibold text-on-surface-variant transition-colors hover:bg-surface-container-low hover:text-on-surface"
                  >
                    Xem thêm
                  </button>
                </div>
              )}
            </section>

            {/* Recent submissions */}
            {recent.length > 0 && (
              <section>
                <h2 className="mb-3 text-lg font-bold text-on-surface">Nộp gần đây</h2>
                <div className="overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-sm">
                  <ul>
                    {recent.map((s) => (
                      <li
                        key={s.id}
                        className="flex items-center gap-3 border-b border-outline-variant/40 px-4 py-3 last:border-b-0"
                      >
                        <CheckCircle2
                          className={`h-4 w-4 shrink-0 ${
                            s.verdict === 'AC' ? 'text-emerald-500' : 'text-outline-variant'
                          }`}
                        />
                        <Link
                          to={`/problems/${s.problemId}`}
                          className="min-w-0 flex-1 truncate text-sm font-medium text-on-surface hover:text-secondary"
                        >
                          {s.problemTitle ?? s.problemId}
                        </Link>
                        <span className="hidden shrink-0 text-xs text-on-surface-variant sm:inline">
                          {langLabel(s.language)}
                        </span>
                        <span className="shrink-0 text-xs text-on-surface-variant">
                          {DATE_FMT.format(new Date(s.createdAt))}
                        </span>
                        <span
                          className={`w-24 shrink-0 text-right text-xs font-semibold ${verdictTone(
                            s.verdict,
                            s.status,
                          )}`}
                        >
                          {verdictText(s)}
                        </span>
                      </li>
                    ))}
                  </ul>
                </div>
              </section>
            )}
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}

// ── Progress hero (ring + difficulty + acceptance + streak) ────────────────

const HERO_BREAKDOWN = [
  { key: 'EASY', label: 'Easy', tone: 'text-emerald-600', bar: 'bg-emerald-500' },
  { key: 'MEDIUM', label: 'Medium', tone: 'text-amber-600', bar: 'bg-amber-500' },
  { key: 'HARD', label: 'Hard', tone: 'text-rose-600', bar: 'bg-rose-500' },
] as const;

function ProgressHero({ stats }: { stats: UserStats }) {
  const solved = stats.solvedTotal ?? 0;
  const total = stats.totalProblems ?? 0;
  const bySolved = stats.solvedByDifficulty ?? {};
  const byTotal = stats.totalByDifficulty ?? {};

  const R = 40;
  const C = 2 * Math.PI * R;
  const frac = total > 0 ? Math.min(1, solved / total) : 0;

  return (
    <div className="flex flex-col items-center gap-6 rounded-2xl border border-outline-variant bg-surface-container-lowest px-6 py-5 shadow-sm sm:flex-row">
      <div className="relative h-28 w-28 shrink-0">
        <svg viewBox="0 0 96 96" className="h-full w-full -rotate-90">
          <circle cx="48" cy="48" r={R} fill="none" strokeWidth={8} className="stroke-surface-container-high" />
          <circle
            cx="48" cy="48" r={R} fill="none" strokeWidth={8} strokeLinecap="round"
            className="stroke-emerald-500 transition-[stroke-dashoffset] duration-500"
            strokeDasharray={C}
            strokeDashoffset={C * (1 - frac)}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-xl font-bold leading-none tabular-nums text-on-surface">
            {solved}
            <span className="text-on-surface-variant">/{total}</span>
          </span>
          <span className="mt-1 text-[11px] font-medium text-on-surface-variant">Solved</span>
        </div>
      </div>

      <div className="w-full flex-1 space-y-2.5">
        {HERO_BREAKDOWN.map((b) => {
          const s = bySolved[b.key] ?? 0;
          const t = byTotal[b.key] ?? 0;
          const pct = t > 0 ? (s / t) * 100 : 0;
          return (
            <div key={b.key} className="flex items-center gap-3">
              <span className={`w-16 text-xs font-semibold ${b.tone}`}>{b.label}</span>
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-container-high">
                <div
                  className={`h-full rounded-full ${b.bar} transition-[width] duration-500`}
                  style={{ width: `${pct}%` }}
                />
              </div>
              <span className="w-14 text-right text-xs tabular-nums text-on-surface-variant">
                {s}<span className="opacity-60">/{t}</span>
              </span>
            </div>
          );
        })}
      </div>

      <div className="flex w-full justify-around gap-4 border-t border-outline-variant/60 pt-4 sm:w-auto sm:flex-col sm:justify-center sm:gap-3 sm:border-l sm:border-t-0 sm:pl-6 sm:pt-0">
        <div className="text-center sm:text-left">
          <p className="text-[11px] font-medium uppercase tracking-wide text-on-surface-variant">
            Tỉ lệ AC
          </p>
          <p className="text-lg font-bold tabular-nums text-on-surface">
            {stats.acceptanceRate != null ? `${(stats.acceptanceRate * 100).toFixed(1)}%` : '—'}
          </p>
        </div>
        <div className="text-center sm:text-left">
          <p className="text-[11px] font-medium uppercase tracking-wide text-on-surface-variant">
            Chuỗi ngày
          </p>
          <p className="inline-flex items-center gap-1 text-lg font-bold tabular-nums text-on-surface">
            <Flame className="h-4 w-4 text-orange-500" />
            {stats.currentStreakDays}
          </p>
        </div>
      </div>
    </div>
  );
}

// ── Breakdown card (languages / tags) ──────────────────────────────────────

function BreakdownCard({
  title,
  icon,
  data,
  labelFn,
  emptyText,
  limit,
}: {
  title: string;
  icon: ReactNode;
  data: Record<string, number>;
  labelFn: (k: string) => string;
  emptyText: string;
  limit?: number;
}) {
  const entries = Object.entries(data ?? {}).sort((a, b) => b[1] - a[1]);
  const shown = limit ? entries.slice(0, limit) : entries;
  const max = entries.length ? entries[0][1] : 0;

  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
      <div className="mb-4 flex items-center gap-2 text-sm font-bold text-on-surface">
        <span className="text-on-surface-variant">{icon}</span>
        {title}
      </div>
      {shown.length === 0 ? (
        <p className="py-6 text-center text-sm text-on-surface-variant">{emptyText}</p>
      ) : (
        <ul className="space-y-2.5">
          {shown.map(([key, count]) => (
            <li key={key} className="flex items-center gap-3">
              <span className="w-28 shrink-0 truncate text-xs font-medium text-on-surface" title={labelFn(key)}>
                {labelFn(key)}
              </span>
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-container-high">
                <div
                  className="h-full rounded-full bg-secondary transition-[width] duration-500"
                  style={{ width: `${max > 0 ? (count / max) * 100 : 0}%` }}
                />
              </div>
              <span className="w-8 shrink-0 text-right text-xs font-semibold tabular-nums text-on-surface-variant">
                {count}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

// ── Grouped-by-problem row (expand → that problem's submissions) ────────────

function ProblemGroupRow({ group }: { group: ProblemSubmissionGroup }) {
  const [open, setOpen] = useState(false);
  const [subs, setSubs] = useState<SubmissionSummary[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [openSub, setOpenSub] = useState<string | null>(null);

  useEffect(() => {
    if (!open || subs) return;
    setLoading(true);
    listSubmissions({ problemId: group.problemId, size: 50 })
      .then((r) => setSubs(r.content))
      .catch(() => setSubs([]))
      .finally(() => setLoading(false));
  }, [open, subs, group.problemId]);

  const diff = group.difficulty;

  return (
    <li className="border-b border-outline-variant/40 last:border-b-0">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-surface-container-low"
      >
        {group.solved ? (
          <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-500" aria-label="Đã giải" />
        ) : (
          <span className="h-4 w-4 shrink-0 rounded-full border border-outline-variant" aria-label="Đang thử" />
        )}
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-on-surface">
            {group.problemTitle ?? group.problemId}
          </p>
          <p className="mt-0.5 flex items-center gap-2 text-xs text-on-surface-variant">
            <span>Nộp gần nhất {DATE_FMT.format(new Date(group.lastSubmittedAt))}</span>
            {group.bestRuntimeMs != null && (
              <span className="inline-flex items-center gap-1">
                <Clock className="h-3 w-3" /> {group.bestRuntimeMs} ms
              </span>
            )}
          </p>
        </div>
        <span className="hidden shrink-0 rounded-md bg-surface-container px-2 py-0.5 text-xs font-medium tabular-nums text-on-surface-variant sm:inline">
          {group.acceptedCount}/{group.submissionCount} lần
        </span>
        {diff && (
          <span className={`w-16 shrink-0 text-right text-xs font-semibold ${DIFFICULTY_TONE[diff] ?? 'text-on-surface-variant'}`}>
            {DIFFICULTY_LABEL[diff] ?? diff}
          </span>
        )}
        {open ? (
          <ChevronUp className="h-4 w-4 shrink-0 text-on-surface-variant" />
        ) : (
          <ChevronDown className="h-4 w-4 shrink-0 text-on-surface-variant" />
        )}
      </button>

      {open && (
        <div className="bg-surface-container-low/50 px-4 py-3">
          {loading ? (
            <div className="flex items-center gap-2 py-3 text-sm text-on-surface-variant">
              <Loader2 className="h-4 w-4 animate-spin" /> Đang tải lần nộp…
            </div>
          ) : !subs || subs.length === 0 ? (
            <p className="py-3 text-sm text-on-surface-variant">Không có lần nộp.</p>
          ) : (
            <ul className="divide-y divide-outline-variant/40">
              {subs.map((s) => (
                <li key={s.id}>
                  <button
                    type="button"
                    onClick={() => setOpenSub((cur) => (cur === s.id ? null : s.id))}
                    className="flex w-full items-center gap-3 py-2.5 text-left"
                  >
                    <span className={`w-24 shrink-0 text-xs font-semibold ${verdictTone(s.verdict, s.status)}`}>
                      {verdictText(s)}
                    </span>
                    <span className="text-xs text-on-surface-variant">{langLabel(s.language)}</span>
                    <span className="ml-auto text-xs tabular-nums text-on-surface-variant">
                      {s.passedCases}/{s.totalCases}
                    </span>
                    <span className="hidden shrink-0 text-xs text-on-surface-variant sm:inline">
                      {DATE_FMT.format(new Date(s.createdAt))}
                    </span>
                    {openSub === s.id ? (
                      <ChevronUp className="h-4 w-4 shrink-0 text-on-surface-variant" />
                    ) : (
                      <ChevronDown className="h-4 w-4 shrink-0 text-on-surface-variant" />
                    )}
                  </button>
                  {openSub === s.id && <SubmissionDetailPanel id={s.id} />}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </li>
  );
}

// ── Submission detail (source + per-case) ──────────────────────────────────

const CASE_TONE: Record<string, string> = {
  AC: 'text-emerald-600',
  WA: 'text-rose-600',
  TLE: 'text-amber-600',
  MLE: 'text-amber-600',
  RE: 'text-rose-600',
  CE: 'text-rose-600',
};

// Compact per-case grid cell background. Keeps 100+ test cases scannable
// instead of one wide text chip each.
const CASE_CELL_TONE: Record<string, string> = {
  AC: 'bg-emerald-100 text-emerald-700',
  WA: 'bg-rose-100 text-rose-700',
  TLE: 'bg-amber-100 text-amber-700',
  MLE: 'bg-amber-100 text-amber-700',
  RE: 'bg-rose-100 text-rose-700',
  CE: 'bg-rose-100 text-rose-700',
};

function SubmissionDetailPanel({ id }: { id: string }) {
  const [detail, setDetail] = useState<SubmissionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCases, setShowCases] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setError(null);
    getSubmission(id)
      .then((d) => !cancelled && setDetail(d))
      .catch((err) =>
        !cancelled && setError(err instanceof ApiError ? err.message : 'Không tải được chi tiết.'),
      );
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (error) {
    return <div className="px-1 pb-3 text-sm text-rose-600">{error}</div>;
  }
  if (!detail) {
    return (
      <div className="flex items-center gap-2 px-1 pb-3 text-sm text-on-surface-variant">
        <Loader2 className="h-4 w-4 animate-spin" /> Đang tải chi tiết…
      </div>
    );
  }

  const total = detail.cases.length;
  const passed = detail.cases.filter(
    (c) => (c.status ?? '').toUpperCase() === 'AC',
  ).length;
  // Tally cases by status for the compact summary line.
  const tally = detail.cases.reduce<Record<string, number>>((acc, c) => {
    const k = (c.status ?? '?').toUpperCase();
    acc[k] = (acc[k] ?? 0) + 1;
    return acc;
  }, {});

  return (
    <div className="space-y-3 px-1 pb-3">
      {total > 0 && (
        <div className="rounded-lg border border-outline-variant/60 bg-surface">
          <button
            type="button"
            onClick={() => setShowCases((v) => !v)}
            className="flex w-full items-center gap-2 px-3 py-2 text-left"
          >
            <span className="text-xs font-semibold text-on-surface">
              Test case{' '}
              <span
                className={passed === total ? 'text-emerald-600' : 'text-on-surface-variant'}
              >
                {passed}/{total}
              </span>
            </span>
            <span className="ml-auto flex flex-wrap items-center justify-end gap-x-2 text-[11px] font-semibold tabular-nums">
              {Object.entries(tally).map(([st, n]) => (
                <span key={st} className={CASE_TONE[st] ?? 'text-on-surface-variant'}>
                  {st} {n}
                </span>
              ))}
            </span>
            {showCases ? (
              <ChevronUp className="h-4 w-4 shrink-0 text-on-surface-variant" />
            ) : (
              <ChevronDown className="h-4 w-4 shrink-0 text-on-surface-variant" />
            )}
          </button>
          {showCases && (
            <div className="flex flex-wrap gap-1 border-t border-outline-variant/60 px-3 py-2.5">
              {detail.cases.map((c) => (
                <span
                  key={c.orderIndex}
                  title={`#${c.orderIndex + 1} ${c.status}${c.hidden ? ' (ẩn)' : ''}`}
                  className={`grid h-6 w-6 place-items-center rounded text-[10px] font-bold tabular-nums ${
                    CASE_CELL_TONE[(c.status ?? '').toUpperCase()] ??
                    'bg-surface-container text-on-surface-variant'
                  }`}
                >
                  {c.orderIndex + 1}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
      <div>
        <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
          Code đã nộp
        </p>
        <pre className="max-h-64 overflow-auto rounded-lg border border-outline-variant bg-surface p-3 text-[12px] leading-relaxed text-on-surface">
          <code>{detail.sourceCode}</code>
        </pre>
      </div>
    </div>
  );
}
