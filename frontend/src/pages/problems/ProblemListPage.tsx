import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  BarChart3,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  Code2,
  Filter,
  Flame,
  History,
  Loader2,
  Search,
  Shuffle,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getStats, listProblems } from '@/api/practice';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type { ProblemStatus, ProblemSummary, UserStats } from '@/types/practice';

const PAGE_SIZE = 20;

const DIFFICULTY_OPTIONS = [
  { value: '', label: 'Tất cả', dot: '' },
  { value: 'EASY', label: 'Easy', dot: 'bg-emerald-500' },
  { value: 'MEDIUM', label: 'Medium', dot: 'bg-amber-500' },
  { value: 'HARD', label: 'Hard', dot: 'bg-rose-500' },
] as const;

const DIFFICULTY_TONE: Record<string, string> = {
  EASY: 'text-emerald-600',
  MEDIUM: 'text-amber-600',
  HARD: 'text-rose-600',
};

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: 'Easy',
  MEDIUM: 'Medium',
  HARD: 'Hard',
};

export default function ProblemListPage() {
  const navigate = useNavigate();

  const [difficulty, setDifficulty] = useState('');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState(''); // debounced applied search
  const [page, setPage] = useState(0);

  const [rows, setRows] = useState<ProblemSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [stats, setStats] = useState<UserStats | null>(null);

  // Progress strip — best-effort; never blocks the list.
  useEffect(() => {
    let cancelled = false;
    getStats()
      .then((s) => !cancelled && setStats(s))
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  // Debounce the search box → applied query.
  useEffect(() => {
    const h = setTimeout(() => {
      setQuery(search.trim());
      setPage(0);
    }, 350);
    return () => clearTimeout(h);
  }, [search]);

  // Reset to first page whenever a filter changes.
  useEffect(() => {
    setPage(0);
  }, [difficulty]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listProblems({
      difficulty: difficulty || undefined,
      q: query || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then((res) => {
        if (cancelled) return;
        setRows(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(
          err instanceof ApiError ? err.message : 'Không tải được danh sách đề.',
        );
        setRows([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [difficulty, query, page]);

  const rangeLabel = useMemo(() => {
    if (totalElements === 0) return '0 đề';
    const from = page * PAGE_SIZE + 1;
    const to = Math.min((page + 1) * PAGE_SIZE, totalElements);
    return `${from}–${to} / ${totalElements} đề`;
  }, [page, totalElements]);

  const pickRandom = () => {
    if (rows.length === 0) return;
    const p = rows[Math.floor(Math.random() * rows.length)];
    navigate(`/problems/${p.id}`, { state: { difficulty: p.difficulty } });
  };

  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <Header />
      <main className="mx-auto w-full max-w-7xl flex-1 px-6 pb-24 pt-28">
        {/* Title row */}
        <div className="mb-6 flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-emerald-100 text-emerald-700">
            <Code2 className="h-6 w-6" />
          </span>
          <div>
            <h1 className="text-2xl font-bold text-on-surface">Luyện thuật toán</h1>
            <p className="text-sm text-on-surface-variant">
              Chọn đề, viết code, chạy thử rồi nộp để chấm toàn bộ test case.
            </p>
          </div>
          <div className="ml-auto hidden gap-2 sm:flex">
            <Link
              to="/problems/submissions"
              className="inline-flex items-center gap-1.5 rounded-xl border border-outline-variant px-3.5 py-2 text-sm font-semibold text-on-surface-variant transition-colors hover:text-on-surface"
            >
              <History className="h-4 w-4" /> Tiến độ
            </Link>
            <Link
              to="/problems/stats"
              className="inline-flex items-center gap-1.5 rounded-xl border border-outline-variant px-3.5 py-2 text-sm font-semibold text-on-surface-variant transition-colors hover:text-on-surface"
            >
              <BarChart3 className="h-4 w-4" /> Xếp hạng
            </Link>
          </div>
        </div>

        {/* Progress strip — LeetCode-style solved breakdown */}
        {stats && <ProgressStrip stats={stats} />}

        {/* Toolbar — difficulty filter · search · random pick */}
        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center">
          <DifficultyFilter value={difficulty} onChange={setDifficulty} />

          <div className="flex items-center gap-2 lg:ml-auto">
            <div className="relative flex-1 lg:w-72 lg:flex-none">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Tìm theo tiêu đề…"
                className="w-full rounded-full border border-outline-variant bg-surface-container-lowest py-2 pl-9 pr-3 text-sm text-on-surface outline-none transition-colors focus:border-secondary"
              />
            </div>
            <button
              type="button"
              onClick={pickRandom}
              disabled={rows.length === 0}
              title="Chọn một đề ngẫu nhiên"
              className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-secondary px-3.5 py-2 text-sm font-semibold text-on-secondary shadow-sm shadow-secondary/20 transition-opacity hover:opacity-90 disabled:opacity-40"
            >
              <Shuffle className="h-4 w-4" />
              <span className="hidden sm:inline">Ngẫu nhiên</span>
            </button>
          </div>
        </div>

        <p className="mb-2 px-1 text-xs text-on-surface-variant">{rangeLabel}</p>

        {/* List — LeetCode-style table: status · #title · acceptance · difficulty */}
        <div className="overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-sm">
          <div className="grid grid-cols-[2.5rem_1fr_5.5rem_5rem] items-center gap-3 border-b border-outline-variant/60 bg-surface-container-low/50 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
            <span className="text-center">TT</span>
            <span>Tiêu đề</span>
            <span className="text-right">Tỉ lệ AC</span>
            <span className="text-right">Độ khó</span>
          </div>

          {loading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-on-surface-variant">
              <Loader2 className="h-4 w-4 animate-spin" /> Đang tải…
            </div>
          ) : error ? (
            <div className="py-16 text-center text-sm text-rose-600">{error}</div>
          ) : rows.length === 0 ? (
            <div className="py-16 text-center text-sm text-on-surface-variant">
              Không có đề nào khớp bộ lọc.
            </div>
          ) : (
            <ul>
              {rows.map((p, i) => {
                const num = page * PAGE_SIZE + i + 1;
                return (
                  <li
                    key={p.id}
                    className={i % 2 === 1 ? 'bg-surface-container-low/30' : ''}
                  >
                    <Link
                      to={`/problems/${p.id}`}
                      state={{ difficulty: p.difficulty }}
                      className="group grid grid-cols-[2.5rem_1fr_5.5rem_5rem] items-center gap-3 px-4 py-3.5 transition-colors hover:bg-surface-container-low"
                    >
                      <span className="flex justify-center">
                        <StatusIcon status={p.myStatus} />
                      </span>
                      <div className="min-w-0">
                        <p className="truncate text-sm text-on-surface">
                          <span className="tabular-nums text-on-surface-variant">
                            {num}.
                          </span>{' '}
                          <span className="font-medium transition-colors group-hover:text-secondary">
                            {p.title}
                          </span>
                        </p>
                        {p.tags.length > 0 && (
                          <div className="mt-1.5 flex flex-wrap gap-1">
                            {p.tags.slice(0, 4).map((tag) => (
                              <span
                                key={tag}
                                className="rounded-md bg-surface-container px-1.5 py-0.5 text-[11px] text-on-surface-variant"
                              >
                                {tag}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                      <span className="text-right text-xs tabular-nums text-on-surface-variant">
                        {p.acceptanceRate != null
                          ? `${(p.acceptanceRate * 100).toFixed(1)}%`
                          : '—'}
                      </span>
                      <span
                        className={`text-right text-xs font-semibold ${
                          p.difficulty
                            ? DIFFICULTY_TONE[p.difficulty] ?? 'text-on-surface-variant'
                            : 'text-on-surface-variant'
                        }`}
                      >
                        {p.difficulty
                          ? DIFFICULTY_LABEL[p.difficulty] ?? p.difficulty
                          : '—'}
                      </span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="mt-5 flex items-center justify-center gap-3">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="flex items-center gap-1 rounded-lg border border-outline-variant px-3 py-1.5 text-sm font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-40 disabled:hover:bg-transparent"
            >
              <ChevronLeft className="h-4 w-4" /> Trước
            </button>
            <span className="text-sm text-on-surface-variant">
              Trang {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              className="flex items-center gap-1 rounded-lg border border-outline-variant px-3 py-1.5 text-sm font-medium text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-40 disabled:hover:bg-transparent"
            >
              Sau <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}

const PROGRESS_BREAKDOWN = [
  { key: 'EASY', label: 'Easy', tone: 'text-emerald-600', bar: 'bg-emerald-500' },
  { key: 'MEDIUM', label: 'Medium', tone: 'text-amber-600', bar: 'bg-amber-500' },
  { key: 'HARD', label: 'Hard', tone: 'text-rose-600', bar: 'bg-rose-500' },
] as const;

/** LeetCode-style progress panel: a "solved / total" ring + per-difficulty bars. */
function ProgressStrip({ stats }: { stats: UserStats }) {
  const solved = stats.solvedTotal ?? 0;
  const total = stats.totalProblems ?? 0;
  const bySolved = stats.solvedByDifficulty ?? {};
  const byTotal = stats.totalByDifficulty ?? {};

  // Donut ring geometry (viewBox 88×88, stroke 7).
  const R = 36;
  const C = 2 * Math.PI * R;
  const frac = total > 0 ? Math.min(1, solved / total) : 0;

  return (
    <div className="mb-4 flex items-center gap-5 rounded-2xl border border-outline-variant bg-surface-container-lowest px-5 py-4 shadow-sm">
      {/* Solved ring */}
      <div className="relative h-24 w-24 shrink-0">
        <svg viewBox="0 0 88 88" className="h-full w-full -rotate-90">
          <circle
            cx="44" cy="44" r={R} fill="none" strokeWidth={7}
            className="stroke-surface-container-high"
          />
          <circle
            cx="44" cy="44" r={R} fill="none" strokeWidth={7} strokeLinecap="round"
            className="stroke-emerald-500 transition-[stroke-dashoffset] duration-500"
            strokeDasharray={C}
            strokeDashoffset={C * (1 - frac)}
          />
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-base font-bold leading-none tabular-nums text-on-surface">
            {solved}
            <span className="text-on-surface-variant">/{total}</span>
          </span>
          <span className="mt-1 text-[11px] font-medium text-on-surface-variant">
            Solved
          </span>
        </div>
      </div>

      {/* Per-difficulty solved / total */}
      <div className="flex-1 space-y-2.5">
        {PROGRESS_BREAKDOWN.map((b) => {
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

      {/* Streak */}
      {stats.currentStreakDays > 0 && (
        <div className="hidden shrink-0 flex-col items-center gap-0.5 border-l border-outline-variant/60 pl-5 sm:flex">
          <Flame className="h-5 w-5 text-orange-500" />
          <span className="text-lg font-bold leading-none tabular-nums text-on-surface">
            {stats.currentStreakDays}
          </span>
          <span className="text-[11px] text-on-surface-variant">ngày streak</span>
        </div>
      )}
    </div>
  );
}

/** LeetCode-style difficulty filter: a labelled button that opens a single-select menu. */
function DifficultyFilter({
  value,
  onChange,
}: {
  value: string;
  onChange: (v: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  const selected = DIFFICULTY_OPTIONS.find((o) => o.value === value && o.value !== '');

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className={`inline-flex items-center gap-2 rounded-xl border px-3.5 py-2 text-sm font-medium transition-colors ${
          selected
            ? 'border-secondary/40 bg-secondary/5 text-on-surface'
            : 'border-outline-variant text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface'
        }`}
      >
        <Filter className="h-4 w-4" />
        {selected ? (
          <span className="inline-flex items-center gap-1.5">
            <span className={`h-1.5 w-1.5 rounded-full ${selected.dot}`} />
            {selected.label}
          </span>
        ) : (
          'Độ khó'
        )}
        <ChevronDown
          className={`h-4 w-4 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div className="absolute left-0 z-20 mt-2 w-44 overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest py-1 shadow-lg">
          {DIFFICULTY_OPTIONS.map((o) => {
            const active = value === o.value;
            return (
              <button
                key={o.value}
                type="button"
                onClick={() => {
                  onChange(o.value);
                  setOpen(false);
                }}
                className={`flex w-full items-center gap-2 px-3.5 py-2 text-sm transition-colors hover:bg-surface-container-low ${
                  active ? 'font-semibold text-on-surface' : 'text-on-surface-variant'
                }`}
              >
                <span className={`h-1.5 w-1.5 rounded-full ${o.dot || 'bg-transparent'}`} />
                {o.label}
                {active && <Check className="ml-auto h-4 w-4 text-secondary" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

function StatusIcon({ status }: { status: ProblemStatus }) {
  if (status === 'SOLVED') {
    return <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-500" aria-label="Đã giải" />;
  }
  if (status === 'ATTEMPTED') {
    return <CircleDot className="h-5 w-5 shrink-0 text-amber-500" aria-label="Đang thử" />;
  }
  return <span className="mx-auto h-4 w-4 shrink-0 rounded-full border border-outline-variant" aria-label="Chưa làm" />;
}
