import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  Code2,
  Loader2,
  Search,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { listProblems } from '@/api/practice';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type { ProblemStatus, ProblemSummary } from '@/types/practice';

const PAGE_SIZE = 20;

const DIFFICULTY_OPTIONS = [
  { value: '', label: 'Mọi độ khó' },
  { value: 'EASY', label: 'Dễ' },
  { value: 'MEDIUM', label: 'Trung bình' },
  { value: 'HARD', label: 'Khó' },
] as const;

const DIFFICULTY_TONE: Record<string, string> = {
  EASY: 'text-emerald-600',
  MEDIUM: 'text-amber-600',
  HARD: 'text-rose-600',
};

/** Active state for the difficulty filter pills (LeetCode-style colours). */
const DIFFICULTY_PILL_ACTIVE: Record<string, string> = {
  '': 'bg-on-surface text-surface',
  EASY: 'bg-emerald-100 text-emerald-700',
  MEDIUM: 'bg-amber-100 text-amber-700',
  HARD: 'bg-rose-100 text-rose-700',
};

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: 'Dễ',
  MEDIUM: 'Trung bình',
  HARD: 'Khó',
};

export default function ProblemListPage() {
  const [difficulty, setDifficulty] = useState('');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState(''); // debounced applied search
  const [page, setPage] = useState(0);

  const [rows, setRows] = useState<ProblemSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

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

  return (
    <div className="min-h-screen bg-surface">
      <Header />
      <main className="mx-auto max-w-5xl px-6 pb-24 pt-28">
        <div className="mb-8 flex items-center gap-3">
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-emerald-100 text-emerald-700">
            <Code2 className="h-6 w-6" />
          </span>
          <div>
            <h1 className="text-2xl font-bold text-on-surface">Luyện đề thuật toán</h1>
            <p className="text-sm text-on-surface-variant">
              Chọn đề, viết code, chạy thử rồi nộp để chấm toàn bộ test case.
            </p>
          </div>
          <div className="ml-auto flex gap-2">
            <Link
              to="/problems/submissions"
              className="rounded-xl border border-outline-variant px-3.5 py-2 text-sm font-semibold text-on-surface-variant hover:text-on-surface transition-colors"
            >
              Lịch sử nộp
            </Link>
            <Link
              to="/problems/stats"
              className="rounded-xl border border-outline-variant px-3.5 py-2 text-sm font-semibold text-on-surface-variant hover:text-on-surface transition-colors"
            >
              Thống kê
            </Link>
          </div>
        </div>

        {/* Filters — difficulty pills + search (LeetCode-style toolbar) */}
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="flex flex-wrap gap-2">
            {DIFFICULTY_OPTIONS.map((o) => {
              const active = difficulty === o.value;
              return (
                <button
                  key={o.value}
                  type="button"
                  onClick={() => setDifficulty(o.value)}
                  className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
                    active
                      ? DIFFICULTY_PILL_ACTIVE[o.value] ?? 'bg-on-surface text-surface'
                      : 'border border-outline-variant text-on-surface-variant hover:text-on-surface'
                  }`}
                >
                  {o.label}
                </button>
              );
            })}
          </div>
          <div className="relative sm:ml-auto sm:w-72">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm theo tiêu đề…"
              className="w-full rounded-full border border-outline-variant bg-surface-container-lowest py-2 pl-9 pr-3 text-sm text-on-surface outline-none focus:border-primary"
            />
          </div>
        </div>

        <p className="mb-2 px-1 text-xs text-on-surface-variant">{rangeLabel}</p>

        {/* List — LeetCode-style table: status · #title · acceptance · difficulty */}
        <div className="overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest">
          <div className="grid grid-cols-[2rem_1fr_5.5rem_5rem] items-center gap-3 border-b border-outline-variant/60 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
            <span aria-hidden />
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
                    className={i % 2 === 1 ? 'bg-surface-container-low/40' : ''}
                  >
                    <Link
                      to={`/problems/${p.id}`}
                      state={{ difficulty: p.difficulty }}
                      className="grid grid-cols-[2rem_1fr_5.5rem_5rem] items-center gap-3 px-4 py-3 transition-colors hover:bg-surface-container-low"
                    >
                      <span className="flex justify-center">
                        <StatusIcon status={p.myStatus} />
                      </span>
                      <div className="min-w-0">
                        <p className="truncate text-sm text-on-surface">
                          <span className="tabular-nums text-on-surface-variant">
                            {num}.
                          </span>{' '}
                          <span className="font-medium hover:text-primary">
                            {p.title}
                          </span>
                        </p>
                        {p.tags.length > 0 && (
                          <div className="mt-1 flex flex-wrap gap-1">
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
              className="flex items-center gap-1 rounded-lg border border-outline-variant px-3 py-1.5 text-sm font-medium text-on-surface disabled:opacity-40"
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
              className="flex items-center gap-1 rounded-lg border border-outline-variant px-3 py-1.5 text-sm font-medium text-on-surface disabled:opacity-40"
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

function StatusIcon({ status }: { status: ProblemStatus }) {
  if (status === 'SOLVED') {
    return <CheckCircle2 className="h-5 w-5 shrink-0 text-emerald-500" aria-label="Đã giải" />;
  }
  if (status === 'ATTEMPTED') {
    return <CircleDot className="h-5 w-5 shrink-0 text-amber-500" aria-label="Đang thử" />;
  }
  return <span className="h-5 w-5 shrink-0 rounded-full border border-outline-variant" aria-label="Chưa làm" />;
}
