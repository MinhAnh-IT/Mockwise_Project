import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  Boxes,
  Clock,
  Cpu,
  Gauge,
  Inbox,
  RefreshCw,
  RotateCcw,
  Server,
  X,
} from 'lucide-react';
import { ApiError } from '@/api/client';
import { useUrlState } from '@/lib/useUrlState';
import {
  getJudge0Health,
  getJudgeDlq,
  getJudgeJobDetail,
  getJudgeJobs,
  getJudgeStats,
} from '@/api/adminJudge';
import {
  AdminShell,
  Button,
  EmptyState,
  ErrorState,
  Pill,
  Select,
  Spinner,
} from '@/components/admin/ui';
import type {
  DlqOverview,
  Judge0Health,
  JudgeJobDetail,
  JudgeJobPage,
  JudgeStats,
} from '@/types/judge';

const REFRESH_MS = 15_000;
const AUTO_REFRESH_KEY = 'admin.judge.autoRefresh';
const PAGE_SIZE = 20;

const WINDOWS = ['1h', '24h', '7d'] as const;
const STATUS_OPTIONS = ['PENDING', 'RUNNING', 'DONE', 'FAILED'] as const;
const VERDICT_OPTIONS = ['AC', 'WA', 'RE', 'CE', 'TLE', 'MLE'] as const;

// Window/filters/page persisted to the URL so they survive a refresh.
const JUDGE_URL_DEFAULTS = { window: '24h', status: '', verdict: '', page: 0 };

const TIME_FMT = new Intl.DateTimeFormat('vi-VN', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});
const DATETIME_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

function fmtMs(ms: number | null): string {
  if (ms == null) return '—';
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

const STATUS_TONE: Record<string, 'emerald' | 'amber' | 'red' | 'secondary' | 'neutral'> = {
  DONE: 'emerald',
  RUNNING: 'secondary',
  PENDING: 'amber',
  FAILED: 'red',
};

const VERDICT_TONE: Record<string, 'emerald' | 'amber' | 'red' | 'neutral'> = {
  AC: 'emerald',
  WA: 'amber',
  TLE: 'amber',
  MLE: 'amber',
  RE: 'red',
  CE: 'red',
};

export default function AdminJudgePage() {
  const [stats, setStats] = useState<JudgeStats | null>(null);
  const [health, setHealth] = useState<Judge0Health | null>(null);
  const [dlq, setDlq] = useState<DlqOverview | null>(null);
  const [jobs, setJobs] = useState<JudgeJobPage | null>(null);

  // Window + filters + page index persisted to the URL so they survive a refresh.
  const [urlState, setUrlState] = useUrlState(JUDGE_URL_DEFAULTS);
  const window_ = urlState.window;
  const statusFilter = urlState.status;
  const verdictFilter = urlState.verdict;
  const page = urlState.page;

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<JudgeJobDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [auto, setAuto] = useState(
    () => localStorage.getItem(AUTO_REFRESH_KEY) !== 'false',
  );
  const firstLoad = useRef(true);

  const toggleAuto = (next: boolean) => {
    setAuto(next);
    localStorage.setItem(AUTO_REFRESH_KEY, String(next));
  };

  const load = useCallback(async () => {
    if (firstLoad.current) setLoading(true);
    else setRefreshing(true);
    try {
      const [s, h, d, j] = await Promise.all([
        getJudgeStats(window_),
        getJudge0Health(),
        getJudgeDlq(50),
        getJudgeJobs({
          status: statusFilter || undefined,
          verdict: verdictFilter || undefined,
          page,
          size: PAGE_SIZE,
        }),
      ]);
      setStats(s);
      setHealth(h);
      setDlq(d);
      setJobs(j);
      setError(null);
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) {
        setError(err instanceof Error ? err.message : 'Không tải được dữ liệu judge.');
      }
    } finally {
      firstLoad.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, [window_, statusFilter, verdictFilter, page]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!auto) return;
    const t = globalThis.setInterval(load, REFRESH_MS);
    return () => globalThis.clearInterval(t);
  }, [auto, load]);

  const openDetail = async (submissionId: string) => {
    setDetailLoading(true);
    try {
      setDetail(await getJudgeJobDetail(submissionId));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không tải được chi tiết job.');
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <AdminShell
      title="Giám sát Judge"
      subtitle="Throughput chấm bài, hàng đợi, dead-letter và sức khỏe Judge0."
      breadcrumb={[{ label: 'Tổng quan', to: '/admin' }, { label: 'Giám sát Judge' }]}
      actions={
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-1.5 text-xs text-on-surface-variant">
            <input
              type="checkbox"
              checked={auto}
              onChange={(e) => toggleAuto(e.target.checked)}
              className="h-3.5 w-3.5 accent-secondary"
            />
            Tự động (15s)
          </label>
          <Button variant="outline" loading={refreshing} onClick={load}>
            <RefreshCw className="h-4 w-4" />
            Làm mới
          </Button>
        </div>
      }
    >
      {loading ? (
        <Spinner label="Đang thu thập số liệu judge…" />
      ) : error && !stats ? (
        <ErrorState message={error} onRetry={load} />
      ) : (
        <div className="space-y-8">
          {error && (
            <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800">
              Lần làm mới gần nhất lỗi: {error}. Đang hiển thị dữ liệu cũ.
            </p>
          )}

          {/* Window selector + key metrics */}
          <section>
            <div className="mb-3 flex items-center justify-between gap-2">
              <SectionTitle icon={Gauge} title="Throughput" />
              <div className="flex items-center gap-1 rounded-xl border border-outline-variant p-0.5">
                {WINDOWS.map((w) => (
                  <button
                    key={w}
                    onClick={() => setUrlState({ window: w, page: 0 })}
                    className={`rounded-lg px-3 py-1 text-xs font-semibold transition ${
                      window_ === w
                        ? 'bg-secondary text-white'
                        : 'text-on-surface-variant hover:bg-surface-container'
                    }`}
                  >
                    {w}
                  </button>
                ))}
              </div>
            </div>
            {stats && (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <MetricCard icon={Boxes} label="Tổng job" value={String(stats.total)} sub={`cửa sổ ${stats.window}`} />
                <MetricCard
                  icon={Inbox}
                  label="Đang chờ xử lý"
                  value={String(stats.backlog)}
                  sub="PENDING + RUNNING (hiện tại)"
                  tone={stats.backlog > 50 ? 'red' : stats.backlog > 10 ? 'amber' : undefined}
                />
                <MetricCard icon={Clock} label="Độ trễ TB" value={fmtMs(stats.avgLatencyMs)} sub={`p95 ${fmtMs(stats.p95LatencyMs)}`} />
                <MetricCard
                  icon={RotateCcw}
                  label="Job phải retry"
                  value={String(stats.retriedJobs)}
                  sub={`retry tối đa ${stats.maxRetryCount}`}
                  tone={stats.retriedJobs > 0 ? 'amber' : undefined}
                />
              </div>
            )}
          </section>

          {/* Status + verdict breakdown */}
          {stats && (
            <section className="grid gap-6 lg:grid-cols-2">
              <Breakdown title="Trạng thái job" data={stats.byStatus} toneFor={(k) => STATUS_TONE[k] ?? 'neutral'} />
              <Breakdown title="Kết quả chấm (verdict)" data={stats.byVerdict} toneFor={(k) => VERDICT_TONE[k] ?? 'neutral'} />
            </section>
          )}

          {/* Judge0 + DLQ */}
          <section className="grid gap-6 lg:grid-cols-2">
            {health && <Judge0Card h={health} />}
            {dlq && <DlqCard dlq={dlq} />}
          </section>

          {/* Jobs table */}
          <section>
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <SectionTitle icon={Server} title="Danh sách job" count={jobs?.totalElements} />
              <div className="flex items-center gap-2">
                <Select
                  value={statusFilter}
                  onChange={(e) =>
                    setUrlState({ status: e.target.value, page: 0 })
                  }
                  className="!w-auto"
                >
                  <option value="">Mọi trạng thái</option>
                  {STATUS_OPTIONS.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </Select>
                <Select
                  value={verdictFilter}
                  onChange={(e) =>
                    setUrlState({ verdict: e.target.value, page: 0 })
                  }
                  className="!w-auto"
                >
                  <option value="">Mọi verdict</option>
                  {VERDICT_OPTIONS.map((v) => (
                    <option key={v} value={v}>
                      {v}
                    </option>
                  ))}
                </Select>
              </div>
            </div>
            {jobs && jobs.content.length > 0 ? (
              <>
                <JobsTable jobs={jobs} onOpen={openDetail} />
                <Pagination
                  page={jobs.page}
                  totalPages={jobs.totalPages}
                  onPrev={() => setUrlState({ page: Math.max(0, page - 1) })}
                  onNext={() =>
                    setUrlState({ page: Math.min(jobs.totalPages - 1, page + 1) })
                  }
                />
              </>
            ) : (
              <EmptyState message="Không có job nào khớp bộ lọc." />
            )}
          </section>

          {stats && (
            <p className="flex items-center gap-1.5 text-xs text-on-surface-variant">
              <Activity className="h-3.5 w-3.5" />
              Cập nhật lúc {TIME_FMT.format(new Date(stats.generatedAt))}
              {refreshing && ' · đang làm mới…'}
            </p>
          )}
        </div>
      )}

      {(detail || detailLoading) && (
        <JobDetailModal
          detail={detail}
          loading={detailLoading}
          onClose={() => setDetail(null)}
        />
      )}
    </AdminShell>
  );
}

// ── Sub-components ───────────────────────────────────────────────────────────

function SectionTitle({
  icon: Icon,
  title,
  count,
}: {
  icon: typeof Server;
  title: string;
  count?: number;
}) {
  return (
    <h2 className="flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-on-surface-variant">
      <Icon className="h-4 w-4" />
      {title}
      {count != null && <span className="font-normal opacity-70">({count})</span>}
    </h2>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
  sub,
  tone,
}: {
  icon: typeof Server;
  label: string;
  value: string;
  sub?: string;
  tone?: 'amber' | 'red';
}) {
  const valTone =
    tone === 'red' ? 'text-red-600' : tone === 'amber' ? 'text-amber-600' : 'text-on-surface';
  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
        <Icon className="h-4 w-4" />
        {label}
      </div>
      <p className={`mt-2 text-2xl font-extrabold leading-tight ${valTone}`}>{value}</p>
      {sub && <p className="text-xs text-on-surface-variant">{sub}</p>}
    </div>
  );
}

function Breakdown({
  title,
  data,
  toneFor,
}: {
  title: string;
  data: Record<string, number>;
  toneFor: (key: string) => 'emerald' | 'amber' | 'red' | 'secondary' | 'neutral';
}) {
  const entries = Object.entries(data).filter(([, v]) => v > 0);
  const total = entries.reduce((acc, [, v]) => acc + v, 0);
  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
      <h3 className="mb-3 text-sm font-bold text-on-surface">{title}</h3>
      {entries.length === 0 ? (
        <p className="text-xs text-on-surface-variant">Chưa có dữ liệu.</p>
      ) : (
        <ul className="space-y-2">
          {entries
            .sort((a, b) => b[1] - a[1])
            .map(([key, count]) => (
              <li key={key} className="flex items-center gap-3">
                <span className="w-20 shrink-0">
                  <Pill tone={toneFor(key)}>{key}</Pill>
                </span>
                <div className="h-2 flex-1 overflow-hidden rounded-full bg-surface-container">
                  <div
                    className="h-full rounded-full bg-secondary/60"
                    style={{ width: `${total ? (count / total) * 100 : 0}%` }}
                  />
                </div>
                <span className="w-12 shrink-0 text-right text-sm font-semibold text-on-surface">
                  {count}
                </span>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}

function Judge0Card({ h }: { h: Judge0Health }) {
  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-bold text-on-surface">
          <Cpu className="h-4 w-4" />
          Judge0
        </h3>
        <Pill tone={h.reachable ? 'emerald' : 'red'}>
          {h.reachable ? 'Hoạt động' : 'Mất kết nối'}
        </Pill>
      </div>
      {h.reachable ? (
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <Stat label="Hàng đợi" value={h.queueSize ?? '—'} alert={(h.queueSize ?? 0) > 0} />
          <Stat label="Worker tổng" value={h.workersTotal ?? '—'} />
          <Stat label="Worker rảnh" value={h.workersIdle ?? '—'} />
          <Stat label="Đang chạy" value={h.workersWorking ?? '—'} />
          <Stat label="Độ trễ probe" value={fmtMs(h.latencyMs)} />
          <Stat label="Phiên bản" value={h.version ?? '—'} />
        </dl>
      ) : (
        <p className="text-xs text-red-600">{h.detail ?? 'Không probe được Judge0.'}</p>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  alert,
}: {
  label: string;
  value: string | number;
  alert?: boolean;
}) {
  return (
    <div>
      <dt className="text-xs text-on-surface-variant">{label}</dt>
      <dd className={`font-bold ${alert ? 'text-amber-600' : 'text-on-surface'}`}>{value}</dd>
    </div>
  );
}

function DlqCard({ dlq }: { dlq: DlqOverview }) {
  return (
    <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-bold text-on-surface">
          <AlertTriangle className="h-4 w-4" />
          Dead-letter (DLQ)
        </h3>
        <Pill tone={!dlq.reachable ? 'red' : dlq.total > 0 ? 'amber' : 'emerald'}>
          {!dlq.reachable ? 'Lỗi đọc' : `${dlq.total} message`}
        </Pill>
      </div>
      <p className="mb-2 truncate text-xs text-on-surface-variant" title={dlq.topic}>
        topic: {dlq.topic}
      </p>
      {dlq.note && <p className="text-xs text-on-surface-variant">{dlq.note}</p>}
      {dlq.messages.length > 0 && (
        <ul className="mt-2 max-h-64 space-y-2 overflow-y-auto">
          {dlq.messages.map((m) => (
            <li
              key={`${m.partition}-${m.offset}`}
              className="rounded-xl border border-outline-variant bg-surface-container p-2 text-xs"
            >
              <div className="flex items-center justify-between gap-2 text-on-surface-variant">
                <span className="font-mono">
                  p{m.partition}@{m.offset}
                </span>
                <span>{m.timestamp ? DATETIME_FMT.format(new Date(m.timestamp)) : '—'}</span>
              </div>
              {m.exceptionMessage && (
                <p className="mt-1 break-words font-medium text-red-600" title={m.exceptionClass ?? ''}>
                  {m.exceptionMessage}
                </p>
              )}
              {m.payloadPreview && (
                <pre className="mt-1 max-h-24 overflow-auto whitespace-pre-wrap break-all rounded bg-surface-container-lowest p-1.5 text-[11px] text-on-surface-variant">
                  {m.payloadPreview}
                </pre>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function JobsTable({
  jobs,
  onOpen,
}: {
  jobs: JudgeJobPage;
  onOpen: (submissionId: string) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-outline-variant">
      <table className="w-full text-sm">
        <thead className="bg-surface-container text-left text-xs uppercase tracking-wide text-on-surface-variant">
          <tr>
            <th className="px-3 py-2">Submission</th>
            <th className="px-3 py-2">Trạng thái</th>
            <th className="px-3 py-2">Verdict</th>
            <th className="px-3 py-2">Ngôn ngữ</th>
            <th className="px-3 py-2">Cases</th>
            <th className="px-3 py-2">Retry</th>
            <th className="px-3 py-2">Độ trễ</th>
            <th className="px-3 py-2">Tạo lúc</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant">
          {jobs.content.map((j) => (
            <tr
              key={j.id}
              onClick={() => onOpen(j.submissionId)}
              className="cursor-pointer transition hover:bg-surface-container/60"
            >
              <td className="px-3 py-2 font-mono text-xs" title={j.submissionId}>
                {j.submissionId.slice(0, 8)}…
                {j.origin && <span className="ml-1 text-on-surface-variant">({j.origin})</span>}
              </td>
              <td className="px-3 py-2">
                <Pill tone={STATUS_TONE[j.status] ?? 'neutral'}>{j.status}</Pill>
              </td>
              <td className="px-3 py-2">
                {j.verdict ? (
                  <Pill tone={VERDICT_TONE[j.verdict] ?? 'neutral'}>{j.verdict}</Pill>
                ) : (
                  '—'
                )}
              </td>
              <td className="px-3 py-2">{j.language}</td>
              <td className="px-3 py-2">
                {j.doneCases}/{j.totalCases}
              </td>
              <td className="px-3 py-2">
                {j.retryCount > 0 ? (
                  <span className="font-semibold text-amber-600">{j.retryCount}</span>
                ) : (
                  '0'
                )}
              </td>
              <td className="px-3 py-2">{fmtMs(j.latencyMs)}</td>
              <td className="px-3 py-2 text-xs text-on-surface-variant">
                {j.createdAt ? DATETIME_FMT.format(new Date(j.createdAt)) : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Pagination({
  page,
  totalPages,
  onPrev,
  onNext,
}: {
  page: number;
  totalPages: number;
  onPrev: () => void;
  onNext: () => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-3 flex items-center justify-end gap-2 text-sm">
      <Button variant="outline" disabled={page <= 0} onClick={onPrev}>
        Trước
      </Button>
      <span className="text-on-surface-variant">
        Trang {page + 1}/{totalPages}
      </span>
      <Button variant="outline" disabled={page >= totalPages - 1} onClick={onNext}>
        Sau
      </Button>
    </div>
  );
}

function JobDetailModal({
  detail,
  loading,
  onClose,
}: {
  detail: JudgeJobDetail | null;
  loading: boolean;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="max-h-[85vh] w-full max-w-3xl overflow-y-auto rounded-2xl bg-surface p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-bold text-on-surface">Chi tiết job</h3>
          <button onClick={onClose} className="rounded-lg p-1 hover:bg-surface-container" aria-label="Đóng">
            <X className="h-5 w-5" />
          </button>
        </div>
        {loading || !detail ? (
          <Spinner label="Đang tải chi tiết…" />
        ) : (
          <div className="space-y-4">
            <dl className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
              <Stat label="Submission" value={detail.job.submissionId.slice(0, 12) + '…'} />
              <Stat label="Trạng thái" value={detail.job.status} />
              <Stat label="Verdict" value={detail.job.verdict ?? '—'} />
              <Stat label="Ngôn ngữ" value={detail.job.language} />
              <Stat label="Cases" value={`${detail.job.doneCases}/${detail.job.totalCases}`} />
              <Stat label="Retry" value={detail.job.retryCount} />
              <Stat label="Độ trễ" value={fmtMs(detail.job.latencyMs)} />
              <Stat label="Judge0 token" value={detail.judge0Token?.slice(0, 10) ?? '—'} />
            </dl>
            <div className="overflow-x-auto rounded-xl border border-outline-variant">
              <table className="w-full text-sm">
                <thead className="bg-surface-container text-left text-xs uppercase text-on-surface-variant">
                  <tr>
                    <th className="px-3 py-2">#</th>
                    <th className="px-3 py-2">Status</th>
                    <th className="px-3 py-2">Runtime</th>
                    <th className="px-3 py-2">Mem</th>
                    <th className="px-3 py-2">stderr</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant">
                  {detail.results.map((t) => (
                    <tr key={t.testCaseId}>
                      <td className="px-3 py-2">{t.orderIndex}</td>
                      <td className="px-3 py-2">
                        <Pill tone={VERDICT_TONE[t.status] ?? 'neutral'}>{t.status}</Pill>
                      </td>
                      <td className="px-3 py-2">{t.runtimeMs == null ? '—' : `${t.runtimeMs} ms`}</td>
                      <td className="px-3 py-2">{t.memoryKb == null ? '—' : `${t.memoryKb} KB`}</td>
                      <td className="max-w-[240px] truncate px-3 py-2 text-xs text-red-600" title={t.stderr ?? ''}>
                        {t.stderr || '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
