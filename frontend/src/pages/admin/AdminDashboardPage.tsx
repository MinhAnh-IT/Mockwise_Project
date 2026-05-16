import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowRight,
  BookOpen,
  Code2,
  Database,
  MessagesSquare,
  Plus,
  type LucideIcon,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  listBehavioral,
  listCoding,
  listCore,
  type ListPage,
} from '@/api/questionBank';
import {
  AdminShell,
  Button,
  ErrorState,
  Spinner,
} from '@/components/admin/ui';
import type { QuestionKind, QuestionStatus } from '@/types/questionBank';

type KindStat = {
  total: number | null;
  active: number | null;
  draft: number | null;
  inactive: number | null;
};

type StatFetcher = (
  filters: { status?: QuestionStatus },
  page: number,
  size: number,
) => Promise<ListPage<{ id: string }>>;

async function loadKind(fetcher: StatFetcher): Promise<KindStat> {
  const [all, active, draft] = await Promise.all([
    fetcher({}, 0, 1),
    fetcher({ status: 'ACTIVE' }, 0, 1),
    fetcher({ status: 'DRAFT' }, 0, 1),
  ]);
  const total = all.totalCount;
  const a = active.totalCount;
  const d = draft.totalCount;
  const inactive =
    total !== null && a !== null && d !== null
      ? Math.max(0, total - a - d)
      : null;
  return { total, active: a, draft: d, inactive };
}

type KindMeta = {
  kind: QuestionKind;
  label: string;
  icon: LucideIcon;
  fetcher: StatFetcher;
  accent: string;
};

const KINDS: KindMeta[] = [
  {
    kind: 'behavioral',
    label: 'Hành vi',
    icon: MessagesSquare,
    fetcher: (f, p, s) => listBehavioral(f, p, s),
    accent: 'bg-secondary/10 text-secondary',
  },
  {
    kind: 'core',
    label: 'Chuyên môn',
    icon: BookOpen,
    fetcher: (f, p, s) => listCore(f, p, s),
    accent: 'bg-amber-100 text-amber-700',
  },
  {
    kind: 'coding',
    label: 'Lập trình',
    icon: Code2,
    fetcher: (f, p, s) => listCoding(f, p, s),
    accent: 'bg-emerald-100 text-emerald-700',
  },
];

const fmt = (n: number | null) =>
  n === null ? '—' : new Intl.NumberFormat('vi-VN').format(n);

export default function AdminDashboardPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<Record<QuestionKind, KindStat> | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    Promise.all(KINDS.map((k) => loadKind(k.fetcher)))
      .then(([behavioral, core, coding]) => {
        if (cancelled) return;
        setStats({ behavioral, core, coding });
      })
      .catch((err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 401) return;
        setError(
          err instanceof Error ? err.message : 'Không tải được thống kê.',
        );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => load(), [load]);

  const grandTotal = stats
    ? KINDS.reduce<number | null>((acc, k) => {
        const t = stats[k.kind].total;
        return acc === null || t === null ? null : acc + t;
      }, 0)
    : null;

  return (
    <AdminShell
      title="Tổng quan"
      subtitle="Bảng điều khiển quản trị ngân hàng câu hỏi."
    >
      {loading ? (
        <Spinner label="Đang tải thống kê…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : (
        stats && (
          <div className="space-y-8">
            {/* Hero band */}
            <div className="flex flex-col gap-4 rounded-2xl border border-outline-variant bg-gradient-to-br from-secondary-fixed via-surface-container-lowest to-surface-container-low p-6 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex items-center gap-4">
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-secondary/10 text-secondary">
                  <Database className="h-7 w-7" />
                </div>
                <div>
                  <p className="text-3xl font-extrabold leading-none text-on-surface">
                    {fmt(grandTotal)}
                  </p>
                  <p className="mt-1 text-sm font-medium text-on-surface-variant">
                    tổng số câu hỏi trong ngân hàng
                  </p>
                </div>
              </div>
              <Button onClick={() => navigate('/admin/questions')}>
                Vào ngân hàng câu hỏi
                <ArrowRight className="h-4 w-4" />
              </Button>
            </div>

            {/* Per-kind cards */}
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {KINDS.map(({ kind, label, icon: Icon, accent }) => {
                const s = stats[kind];
                return (
                  <div
                    key={kind}
                    className="flex flex-col rounded-2xl border border-outline-variant bg-surface-container-lowest p-5"
                  >
                    <div className="flex items-center justify-between">
                      <div
                        className={`flex h-11 w-11 items-center justify-center rounded-xl ${accent}`}
                      >
                        <Icon className="h-5 w-5" />
                      </div>
                      <span className="text-xs font-semibold uppercase tracking-wider text-on-surface-variant">
                        {label}
                      </span>
                    </div>

                    <p className="mt-4 text-4xl font-extrabold leading-none text-on-surface">
                      {fmt(s.total)}
                    </p>
                    <p className="mt-1 text-xs text-on-surface-variant">
                      câu hỏi
                    </p>

                    <dl className="mt-4 grid grid-cols-3 gap-2 border-t border-outline-variant pt-4 text-center">
                      <div>
                        <dt className="text-[11px] text-on-surface-variant">
                          Đang dùng
                        </dt>
                        <dd className="text-sm font-bold text-emerald-700">
                          {fmt(s.active)}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-[11px] text-on-surface-variant">
                          Nháp
                        </dt>
                        <dd className="text-sm font-bold text-on-surface">
                          {fmt(s.draft)}
                        </dd>
                      </div>
                      <div>
                        <dt className="text-[11px] text-on-surface-variant">
                          Tạm ẩn
                        </dt>
                        <dd className="text-sm font-bold text-amber-700">
                          {fmt(s.inactive)}
                        </dd>
                      </div>
                    </dl>

                    <div className="mt-5 flex gap-2">
                      <Button
                        variant="outline"
                        className="flex-1"
                        onClick={() =>
                          navigate(`/admin/questions/new/${kind}`)
                        }
                      >
                        <Plus className="h-4 w-4" />
                        Tạo mới
                      </Button>
                      <Button
                        variant="ghost"
                        className="flex-1"
                        onClick={() =>
                          navigate('/admin/questions', { state: { kind } })
                        }
                      >
                        Quản lý
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Quick actions */}
            <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-6">
              <h2 className="text-sm font-bold uppercase tracking-wide text-on-surface-variant">
                Thao tác nhanh
              </h2>
              <div className="mt-4 flex flex-wrap gap-2">
                {KINDS.map(({ kind, label }) => (
                  <Button
                    key={kind}
                    variant="outline"
                    onClick={() => navigate(`/admin/questions/new/${kind}`)}
                  >
                    <Plus className="h-4 w-4" />
                    Câu hỏi {label}
                  </Button>
                ))}
                <Button
                  variant="ghost"
                  onClick={() => navigate('/admin/questions')}
                >
                  Xem toàn bộ ngân hàng câu hỏi
                  <ArrowRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>
        )
      )}
    </AdminShell>
  );
}
