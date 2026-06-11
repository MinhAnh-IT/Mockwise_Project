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

        {/* Filters */}
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tìm theo tiêu đề…"
              className="w-full rounded-xl border border-outline-variant bg-surface-container-lowest py-2.5 pl-9 pr-3 text-sm text-on-surface outline-none focus:border-primary"
            />
          </div>
          <select
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value)}
            className="rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2.5 text-sm font-medium text-on-surface outline-none focus:border-primary"
          >
            {DIFFICULTY_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>

        {/* List */}
        <div className="overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest">
          <div className="flex items-center justify-between border-b border-outline-variant/60 px-4 py-2.5 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
            <span>{rangeLabel}</span>
            <span>Độ khó</span>
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
              {rows.map((p) => (
                <li key={p.id}>
                  <Link
                    to={`/problems/${p.id}`}
                    state={{ difficulty: p.difficulty }}
                    className="flex items-center gap-3 border-b border-outline-variant/40 px-4 py-3.5 transition-colors last:border-b-0 hover:bg-surface-container-low"
                  >
                    <StatusIcon status={p.myStatus} />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-on-surface">
                        {p.title}
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
                    <span
                      className={`shrink-0 text-xs font-semibold ${
                        p.difficulty ? DIFFICULTY_TONE[p.difficulty] ?? 'text-on-surface-variant' : 'text-on-surface-variant'
                      }`}
                    >
                      {p.difficulty ? DIFFICULTY_LABEL[p.difficulty] ?? p.difficulty : '—'}
                    </span>
                  </Link>
                </li>
              ))}
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
