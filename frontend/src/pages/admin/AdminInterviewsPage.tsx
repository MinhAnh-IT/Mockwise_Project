import { useCallback, useEffect, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  Search,
  Star,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { getSessionStats, listAdminSessions } from '@/api/adminInterviews';
import {
  AdminShell,
  Button,
  EmptyState,
  ErrorState,
  Input,
  Pill,
  Select,
  Spinner,
} from '@/components/admin/ui';
import {
  INTERVIEW_TYPES,
  INTERVIEW_TYPE_LABEL,
} from '@/types/blueprint';
import type {
  AdminSession,
  AdminSessionFilters,
  InterviewSessionStats,
  InterviewType,
  SessionStatus,
} from '@/types/interview';

const PAGE_SIZE = 20;

const EMPTY_FILTERS: AdminSessionFilters = {};

const STATUSES: SessionStatus[] = [
  'CREATED',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED',
  'SCORED',
];

const STATUS_LABEL: Record<SessionStatus, string> = {
  CREATED: 'Khởi tạo',
  IN_PROGRESS: 'Đang diễn ra',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã huỷ',
  SCORED: 'Đã chấm',
};

const STATUS_TONE: Record<SessionStatus, 'neutral' | 'amber' | 'secondary' | 'red' | 'emerald'> = {
  CREATED: 'neutral',
  IN_PROGRESS: 'amber',
  COMPLETED: 'secondary',
  CANCELLED: 'red',
  SCORED: 'emerald',
};

const DATE_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const fmtInt = (n: number) => new Intl.NumberFormat('vi-VN').format(n);
const fmtScore = (n: number | null | undefined) =>
  n === null || n === undefined ? '—' : n.toFixed(1);
const fmtDate = (iso: string | null) => (iso ? DATE_FMT.format(new Date(iso)) : '—');
const typeLabel = (t: InterviewType | null) =>
  t ? INTERVIEW_TYPE_LABEL[t] ?? t : '—';

export default function AdminInterviewsPage() {
  const [stats, setStats] = useState<InterviewSessionStats | null>(null);

  const [draft, setDraft] = useState<AdminSessionFilters>(EMPTY_FILTERS);
  const [filters, setFilters] = useState<AdminSessionFilters>(EMPTY_FILTERS);

  const [items, setItems] = useState<AdminSession[]>([]);
  const [page, setPage] = useState(0);
  const [totalCount, setTotalCount] = useState<number | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Stats — loaded once.
  useEffect(() => {
    let cancelled = false;
    getSessionStats()
      .then((s) => {
        if (!cancelled) setStats(s);
      })
      .catch(() => {
        if (!cancelled) setStats(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(
    (p: number) => {
      let cancelled = false;
      setLoading(true);
      setError(null);
      listAdminSessions(filters, p, PAGE_SIZE)
        .then((res) => {
          if (cancelled) return;
          setItems(res.items);
          setPage(p);
          setTotalCount(res.totalCount);
        })
        .catch((err) => {
          if (cancelled) return;
          if (err instanceof ApiError && err.status === 401) return;
          setError(
            err instanceof Error ? err.message : 'Không tải được danh sách buổi phỏng vấn.',
          );
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
      return () => {
        cancelled = true;
      };
    },
    [filters],
  );

  useEffect(() => load(0), [load]);

  const applyFilters = () => setFilters(draft);
  const clearFilters = () => {
    setDraft(EMPTY_FILTERS);
    setFilters(EMPTY_FILTERS);
  };

  const totalPages =
    totalCount === null ? 0 : Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  return (
    <AdminShell
      title="Buổi phỏng vấn"
      subtitle="Theo dõi và thống kê các buổi phỏng vấn (chỉ xem)."
      breadcrumb={[
        { label: 'Tổng quan', to: '/admin' },
        { label: 'Buổi phỏng vấn' },
      ]}
    >
      {/* Stats */}
      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="flex items-center gap-4 rounded-2xl border border-outline-variant bg-gradient-to-br from-secondary-fixed via-surface-container-lowest to-surface-container-low p-5">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-secondary/10 text-secondary">
            <ClipboardList className="h-6 w-6" />
          </div>
          <div>
            <p className="text-2xl font-extrabold leading-none text-on-surface">
              {stats ? fmtInt(stats.totalSessions) : '—'}
            </p>
            <p className="mt-1 text-sm text-on-surface-variant">tổng số buổi</p>
          </div>
        </div>

        <div className="flex items-center gap-4 rounded-2xl border border-outline-variant bg-surface-container-lowest p-5">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
            <Star className="h-6 w-6" />
          </div>
          <div>
            <p className="text-2xl font-extrabold leading-none text-on-surface">
              {stats ? fmtScore(stats.averageScore) : '—'}
            </p>
            <p className="mt-1 text-sm text-on-surface-variant">
              điểm trung bình (đã chấm)
            </p>
          </div>
        </div>

        <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-5">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
            Theo loại
          </p>
          <div className="flex flex-wrap gap-1.5">
            {stats
              ? INTERVIEW_TYPES.map((t) => (
                  <Pill key={t} tone="secondary">
                    {INTERVIEW_TYPE_LABEL[t]}: {fmtInt(stats.byType[t] ?? 0)}
                  </Pill>
                ))
              : '—'}
          </div>
        </div>
      </div>

      {/* Status breakdown */}
      {stats && (
        <div className="mb-6 flex flex-wrap gap-2">
          {STATUSES.map((s) => (
            <Pill key={s} tone={STATUS_TONE[s]}>
              {STATUS_LABEL[s]}: {fmtInt(stats.byStatus[s] ?? 0)}
            </Pill>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className="mb-6 grid grid-cols-1 gap-3 rounded-2xl border border-outline-variant bg-surface-container-lowest p-4 sm:grid-cols-2 lg:grid-cols-4">
        <Select
          value={draft.status ?? ''}
          onChange={(e) =>
            setDraft((d) => ({
              ...d,
              status: (e.target.value || undefined) as SessionStatus | undefined,
            }))
          }
        >
          <option value="">Mọi trạng thái</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {STATUS_LABEL[s]}
            </option>
          ))}
        </Select>
        <Select
          value={draft.interviewType ?? ''}
          onChange={(e) =>
            setDraft((d) => ({
              ...d,
              interviewType: (e.target.value || undefined) as InterviewType | undefined,
            }))
          }
        >
          <option value="">Mọi loại</option>
          {INTERVIEW_TYPES.map((t) => (
            <option key={t} value={t}>
              {INTERVIEW_TYPE_LABEL[t]}
            </option>
          ))}
        </Select>
        <div className="relative sm:col-span-2 lg:col-span-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
          <Input
            value={draft.userId ?? ''}
            onChange={(e) =>
              setDraft((d) => ({ ...d, userId: e.target.value || undefined }))
            }
            onKeyDown={(e) => {
              if (e.key === 'Enter') applyFilters();
            }}
            placeholder="User ID…"
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2 sm:col-span-2 lg:col-span-4">
          <Button onClick={applyFilters}>Áp dụng lọc</Button>
          <Button variant="ghost" onClick={clearFilters}>
            Xoá lọc
          </Button>
          {totalCount !== null && (
            <span className="ml-auto text-sm text-on-surface-variant">
              {fmtInt(totalCount)} buổi
            </span>
          )}
        </div>
      </div>

      {/* Table */}
      {loading ? (
        <Spinner label="Đang tải buổi phỏng vấn…" />
      ) : error ? (
        <ErrorState message={error} onRetry={() => load(page)} />
      ) : items.length === 0 ? (
        <EmptyState message="Không có buổi phỏng vấn nào khớp bộ lọc." />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-outline-variant">
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-container-low text-xs uppercase tracking-wide text-on-surface-variant">
              <tr>
                <th className="px-4 py-3 font-semibold">Ứng viên</th>
                <th className="px-4 py-3 font-semibold">Loại</th>
                <th className="hidden px-4 py-3 font-semibold md:table-cell">
                  Vị trí
                </th>
                <th className="px-4 py-3 font-semibold">Trạng thái</th>
                <th className="hidden px-4 py-3 text-right font-semibold sm:table-cell">
                  Đã trả lời
                </th>
                <th className="px-4 py-3 text-right font-semibold">Điểm</th>
                <th className="hidden px-4 py-3 text-right font-semibold lg:table-cell">
                  Tạo lúc
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {items.map((s) => (
                <tr
                  key={s.id}
                  className="bg-surface-container-lowest transition hover:bg-surface-container-low"
                >
                  <td className="px-4 py-3">
                    <span
                      className="font-mono text-xs text-on-surface-variant"
                      title={s.userId}
                    >
                      {s.userId.slice(0, 8)}…
                    </span>
                  </td>
                  <td className="px-4 py-3 text-on-surface">{typeLabel(s.interviewType)}</td>
                  <td className="hidden px-4 py-3 text-on-surface-variant md:table-cell">
                    {[s.targetRole, s.level].filter(Boolean).join(' · ') || '—'}
                  </td>
                  <td className="px-4 py-3">
                    <Pill tone={STATUS_TONE[s.status]}>{STATUS_LABEL[s.status]}</Pill>
                  </td>
                  <td
                    className="hidden px-4 py-3 text-right text-on-surface-variant sm:table-cell"
                    title="Số câu đã trả lời / tổng số câu đã hỏi (gồm cả câu hỏi đào sâu)"
                  >
                    {s.answeredQuestions}/{s.totalQuestions}
                  </td>
                  <td className="px-4 py-3 text-right font-semibold text-on-surface">
                    {fmtScore(s.finalScore)}
                  </td>
                  <td className="hidden px-4 py-3 text-right text-xs text-on-surface-variant lg:table-cell">
                    {fmtDate(s.createdAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {!loading && !error && totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-2">
          <Button variant="outline" disabled={page <= 0} onClick={() => load(page - 1)}>
            <ChevronLeft className="h-4 w-4" />
            Trước
          </Button>
          <span className="px-2 text-sm text-on-surface-variant">
            Trang {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            disabled={page + 1 >= totalPages}
            onClick={() => load(page + 1)}
          >
            Sau
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}
    </AdminShell>
  );
}
