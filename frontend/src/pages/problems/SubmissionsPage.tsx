import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  Loader2,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getSubmission, listSubmissions } from '@/api/practice';
import Footer from '@/components/layout/Footer';
import Header from '@/components/layout/Header';
import type {
  SubmissionDetail,
  SubmissionMode,
  SubmissionSummary,
} from '@/types/practice';

const PAGE_SIZE = 20;

const MODE_OPTIONS: { value: '' | SubmissionMode; label: string }[] = [
  { value: '', label: 'Run & Submit' },
  { value: 'SUBMIT', label: 'Chỉ Submit' },
  { value: 'RUN', label: 'Chỉ Run' },
];

const VERDICT_LABEL: Record<string, string> = {
  AC: 'Accepted',
  WA: 'Sai kết quả',
  TLE: 'Quá thời gian',
  MLE: 'Quá bộ nhớ',
  RE: 'Lỗi thực thi',
  CE: 'Lỗi biên dịch',
};

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

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

export default function SubmissionsPage() {
  const [mode, setMode] = useState<'' | SubmissionMode>('');
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<SubmissionSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);

  useEffect(() => {
    setPage(0);
  }, [mode]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listSubmissions({ mode: mode || undefined, page, size: PAGE_SIZE })
      .then((res) => {
        if (cancelled) return;
        setRows(res.content);
        setTotalPages(res.totalPages);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof ApiError ? err.message : 'Không tải được lịch sử.');
        setRows([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [mode, page]);

  const toggle = useCallback((id: string) => {
    setExpanded((cur) => (cur === id ? null : id));
  }, []);

  return (
    <div className="min-h-screen bg-surface">
      <Header />
      <main className="mx-auto max-w-5xl px-6 pb-24 pt-28">
        <div className="mb-6 flex items-center gap-3">
          <h1 className="text-2xl font-bold text-on-surface">Lịch sử nộp</h1>
          <div className="ml-auto flex items-center gap-2">
            <select
              value={mode}
              onChange={(e) => setMode(e.target.value as '' | SubmissionMode)}
              className="rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm font-medium text-on-surface outline-none focus:border-primary"
            >
              {MODE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            <Link
              to="/problems"
              className="rounded-xl border border-outline-variant px-3.5 py-2 text-sm font-semibold text-on-surface-variant hover:text-on-surface transition-colors"
            >
              Danh sách đề
            </Link>
          </div>
        </div>

        <div className="overflow-hidden rounded-2xl border border-outline-variant bg-surface-container-lowest">
          {loading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-sm text-on-surface-variant">
              <Loader2 className="h-4 w-4 animate-spin" /> Đang tải…
            </div>
          ) : error ? (
            <div className="py-16 text-center text-sm text-rose-600">{error}</div>
          ) : rows.length === 0 ? (
            <div className="py-16 text-center text-sm text-on-surface-variant">
              Chưa có lần nộp nào. Hãy chọn một đề và bắt đầu!
            </div>
          ) : (
            <ul>
              {rows.map((s) => (
                <li key={s.id} className="border-b border-outline-variant/40 last:border-b-0">
                  <button
                    type="button"
                    onClick={() => toggle(s.id)}
                    className="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-surface-container-low"
                  >
                    <span className="w-14 shrink-0 text-[11px] font-semibold uppercase text-on-surface-variant">
                      {s.mode}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-on-surface">
                        {s.problemTitle ?? s.problemId}
                      </p>
                      <p className="text-xs text-on-surface-variant">
                        {s.language} · {DATE_FMT.format(new Date(s.createdAt))}
                      </p>
                    </div>
                    <span className="shrink-0 text-xs text-on-surface-variant">
                      {s.passedCases}/{s.totalCases}
                    </span>
                    <span className={`w-28 shrink-0 text-right text-xs font-semibold ${verdictTone(s.verdict, s.status)}`}>
                      {verdictText(s)}
                    </span>
                    {expanded === s.id ? (
                      <ChevronUp className="h-4 w-4 shrink-0 text-on-surface-variant" />
                    ) : (
                      <ChevronDown className="h-4 w-4 shrink-0 text-on-surface-variant" />
                    )}
                  </button>
                  {expanded === s.id && <SubmissionDetailPanel id={s.id} />}
                </li>
              ))}
            </ul>
          )}
        </div>

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

const CASE_TONE: Record<string, string> = {
  AC: 'text-emerald-600',
  WA: 'text-rose-600',
  TLE: 'text-amber-600',
  MLE: 'text-amber-600',
  RE: 'text-rose-600',
  CE: 'text-rose-600',
};

function SubmissionDetailPanel({ id }: { id: string }) {
  const [detail, setDetail] = useState<SubmissionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

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
    return <div className="bg-surface-container-low px-4 py-3 text-sm text-rose-600">{error}</div>;
  }
  if (!detail) {
    return (
      <div className="flex items-center gap-2 bg-surface-container-low px-4 py-3 text-sm text-on-surface-variant">
        <Loader2 className="h-4 w-4 animate-spin" /> Đang tải chi tiết…
      </div>
    );
  }

  return (
    <div className="space-y-3 bg-surface-container-low px-4 py-3">
      {detail.cases.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {detail.cases.map((c) => (
            <span
              key={c.orderIndex}
              title={c.hidden ? 'Test case ẩn' : undefined}
              className={`rounded-md border border-outline-variant/60 bg-surface px-2 py-1 text-[11px] font-semibold ${
                CASE_TONE[c.status?.toUpperCase()] ?? 'text-on-surface-variant'
              }`}
            >
              #{c.orderIndex + 1} {c.status}
              {c.hidden ? ' 🔒' : ''}
            </span>
          ))}
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
