import { useCallback, useEffect, useRef, useState } from 'react';
import { Activity, RefreshCw, ScrollText, Search, X } from 'lucide-react';
import { ApiError } from '@/api/client';
import { getAuditLogs } from '@/api/adminAudit';
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
import type { AuditLog, AuditLogPage } from '@/types/audit';

const PAGE_SIZE = 30;

const CATEGORY_OPTIONS = ['AUTH', 'USER', 'CONTENT'] as const;
const OUTCOME_OPTIONS = ['SUCCESS', 'FAILURE'] as const;

const DATETIME_FMT = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

const CATEGORY_TONE: Record<string, 'emerald' | 'amber' | 'secondary' | 'neutral'> = {
  AUTH: 'secondary',
  USER: 'amber',
  CONTENT: 'emerald',
};

function fmtTime(iso: string): string {
  return DATETIME_FMT.format(new Date(iso));
}

/** datetime-local value (local, no tz) → ISO-8601 instant the backend can parse. */
function toInstant(local: string): string | undefined {
  if (!local) return undefined;
  const d = new Date(local);
  return Number.isNaN(d.getTime()) ? undefined : d.toISOString();
}

type DraftFilters = {
  category: string;
  outcome: string;
  action: string;
  actorId: string;
  from: string;
  to: string;
};

const EMPTY_FILTERS: DraftFilters = {
  category: '',
  outcome: '',
  action: '',
  actorId: '',
  from: '',
  to: '',
};

export default function AdminAuditPage() {
  const [logs, setLogs] = useState<AuditLogPage | null>(null);
  const [draft, setDraft] = useState<DraftFilters>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<DraftFilters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);

  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<AuditLog | null>(null);
  const firstLoad = useRef(true);

  const load = useCallback(async () => {
    if (firstLoad.current) setLoading(true);
    else setRefreshing(true);
    try {
      const res = await getAuditLogs({
        category: applied.category || undefined,
        outcome: applied.outcome || undefined,
        action: applied.action.trim() || undefined,
        actorId: applied.actorId.trim() || undefined,
        from: toInstant(applied.from),
        to: toInstant(applied.to),
        page,
        size: PAGE_SIZE,
      });
      setLogs(res);
      setError(null);
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) {
        setError(err instanceof Error ? err.message : 'Không tải được nhật ký.');
      }
    } finally {
      firstLoad.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, [applied, page]);

  useEffect(() => {
    load();
  }, [load]);

  const applyFilters = () => {
    setPage(0);
    setApplied(draft);
  };

  const resetFilters = () => {
    setPage(0);
    setDraft(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
  };

  return (
    <AdminShell
      title="Nhật ký kiểm toán"
      subtitle="Truy vết ai làm gì: đăng nhập, chặn/bỏ chặn, sửa câu hỏi & blueprint."
      breadcrumb={[{ label: 'Tổng quan', to: '/admin' }, { label: 'Nhật ký kiểm toán' }]}
      actions={
        <Button variant="outline" loading={refreshing} onClick={load}>
          <RefreshCw className="h-4 w-4" />
          Làm mới
        </Button>
      }
    >
      {loading ? (
        <Spinner label="Đang tải nhật ký…" />
      ) : error && !logs ? (
        <ErrorState message={error} onRetry={load} />
      ) : (
        <div className="space-y-6">
          {error && (
            <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800">
              Lần tải gần nhất lỗi: {error}. Đang hiển thị dữ liệu cũ.
            </p>
          )}

          {/* Filters */}
          <section className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-4">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <LabeledSelect
                label="Nhóm"
                value={draft.category}
                onChange={(v) => setDraft((d) => ({ ...d, category: v }))}
                options={CATEGORY_OPTIONS}
                anyLabel="Mọi nhóm"
              />
              <LabeledSelect
                label="Kết quả"
                value={draft.outcome}
                onChange={(v) => setDraft((d) => ({ ...d, outcome: v }))}
                options={OUTCOME_OPTIONS}
                anyLabel="Mọi kết quả"
              />
              <Labeled label="Hành động">
                <Input
                  value={draft.action}
                  onChange={(e) => setDraft((d) => ({ ...d, action: e.target.value }))}
                  placeholder="VD: USER_BLOCK"
                />
              </Labeled>
              <Labeled label="Actor ID">
                <Input
                  value={draft.actorId}
                  onChange={(e) => setDraft((d) => ({ ...d, actorId: e.target.value }))}
                  placeholder="UUID người thực hiện"
                />
              </Labeled>
              <Labeled label="Từ">
                <Input
                  type="datetime-local"
                  value={draft.from}
                  onChange={(e) => setDraft((d) => ({ ...d, from: e.target.value }))}
                />
              </Labeled>
              <Labeled label="Đến">
                <Input
                  type="datetime-local"
                  value={draft.to}
                  onChange={(e) => setDraft((d) => ({ ...d, to: e.target.value }))}
                />
              </Labeled>
            </div>
            <div className="mt-3 flex items-center gap-2">
              <Button onClick={applyFilters}>
                <Search className="h-4 w-4" />
                Lọc
              </Button>
              <Button variant="outline" onClick={resetFilters}>
                Xoá lọc
              </Button>
            </div>
          </section>

          {/* Table */}
          <section>
            <h2 className="mb-3 flex items-center gap-2 text-sm font-bold uppercase tracking-wide text-on-surface-variant">
              <ScrollText className="h-4 w-4" />
              Bản ghi
              {logs && <span className="font-normal opacity-70">({logs.totalElements})</span>}
            </h2>
            {logs && logs.content.length > 0 ? (
              <>
                <AuditTable logs={logs} onOpen={setSelected} />
                <Pagination
                  page={logs.page}
                  totalPages={logs.totalPages}
                  onPrev={() => setPage((p) => Math.max(0, p - 1))}
                  onNext={() => setPage((p) => Math.min(logs.totalPages - 1, p + 1))}
                />
              </>
            ) : (
              <EmptyState message="Không có bản ghi nào khớp bộ lọc." />
            )}
          </section>

          {logs && (
            <p className="flex items-center gap-1.5 text-xs text-on-surface-variant">
              <Activity className="h-3.5 w-3.5" />
              {logs.totalElements} bản ghi · trang {logs.page + 1}/{Math.max(logs.totalPages, 1)}
              {refreshing && ' · đang làm mới…'}
            </p>
          )}
        </div>
      )}

      {selected && <AuditDetailModal log={selected} onClose={() => setSelected(null)} />}
    </AdminShell>
  );
}

// ── Sub-components ───────────────────────────────────────────────────────────

function Labeled({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-xs font-semibold text-on-surface-variant">{label}</span>
      {children}
    </label>
  );
}

function LabeledSelect({
  label,
  value,
  onChange,
  options,
  anyLabel,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: readonly string[];
  anyLabel: string;
}) {
  return (
    <Labeled label={label}>
      <Select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{anyLabel}</option>
        {options.map((o) => (
          <option key={o} value={o}>
            {o}
          </option>
        ))}
      </Select>
    </Labeled>
  );
}

function OutcomeBadge({ outcome, status }: { outcome: string; status: number | null }) {
  return (
    <Pill tone={outcome === 'SUCCESS' ? 'emerald' : 'red'}>
      {outcome}
      {status != null && <span className="ml-1 opacity-70">{status}</span>}
    </Pill>
  );
}

function AuditTable({
  logs,
  onOpen,
}: {
  logs: AuditLogPage;
  onOpen: (log: AuditLog) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-outline-variant">
      <table className="w-full text-sm">
        <thead className="bg-surface-container text-left text-xs uppercase tracking-wide text-on-surface-variant">
          <tr>
            <th className="px-3 py-2">Thời gian</th>
            <th className="px-3 py-2">Hành động</th>
            <th className="px-3 py-2">Actor</th>
            <th className="px-3 py-2">Đối tượng</th>
            <th className="px-3 py-2">Kết quả</th>
            <th className="px-3 py-2">Nguồn</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant">
          {logs.content.map((a) => (
            <tr
              key={a.id}
              onClick={() => onOpen(a)}
              className="cursor-pointer transition hover:bg-surface-container/60"
            >
              <td className="whitespace-nowrap px-3 py-2 text-xs text-on-surface-variant">
                {fmtTime(a.occurredAt)}
              </td>
              <td className="px-3 py-2">
                <span className="font-mono text-xs font-semibold text-on-surface">{a.action}</span>
                <Pill tone={CATEGORY_TONE[a.category] ?? 'neutral'}>{a.category}</Pill>
              </td>
              <td className="px-3 py-2 text-xs">
                {a.actorEmail ? (
                  <span title={a.actorId ?? ''}>{a.actorEmail}</span>
                ) : a.actorId ? (
                  <span className="font-mono">{a.actorId.slice(0, 8)}…</span>
                ) : (
                  '—'
                )}
                {a.actorRole && <span className="ml-1 text-on-surface-variant">({a.actorRole})</span>}
              </td>
              <td className="px-3 py-2 text-xs">
                {a.targetType ? (
                  <span>
                    {a.targetType}
                    {a.targetId && (
                      <span className="ml-1 font-mono text-on-surface-variant" title={a.targetId}>
                        {a.targetId.length > 10 ? `${a.targetId.slice(0, 8)}…` : a.targetId}
                      </span>
                    )}
                  </span>
                ) : (
                  '—'
                )}
              </td>
              <td className="px-3 py-2">
                <OutcomeBadge outcome={a.outcome} status={a.statusCode} />
              </td>
              <td className="px-3 py-2 text-xs text-on-surface-variant">{a.source}</td>
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

function AuditDetailModal({ log, onClose }: { log: AuditLog; onClose: () => void }) {
  const rows: Array<[string, string]> = [
    ['Thời gian', fmtTime(log.occurredAt)],
    ['Hành động', log.action],
    ['Nhóm', log.category],
    ['Kết quả', `${log.outcome}${log.statusCode != null ? ` (${log.statusCode})` : ''}`],
    ['Actor', log.actorEmail ?? log.actorId ?? '—'],
    ['Actor ID', log.actorId ?? '—'],
    ['Vai trò', log.actorRole ?? '—'],
    ['Đối tượng', log.targetType ?? '—'],
    ['Target ID', log.targetId ?? '—'],
    ['HTTP', [log.httpMethod, log.path].filter(Boolean).join(' ') || '—'],
    ['IP', log.ip ?? '—'],
    ['Độ trễ', log.latencyMs != null ? `${log.latencyMs} ms` : '—'],
    ['Nguồn', log.source],
    ['User-Agent', log.userAgent ?? '—'],
  ];
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div
        className="max-h-[85vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-surface p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-bold text-on-surface">Chi tiết bản ghi #{log.id}</h3>
          <button onClick={onClose} className="rounded-lg p-1 hover:bg-surface-container" aria-label="Đóng">
            <X className="h-5 w-5" />
          </button>
        </div>
        <dl className="grid grid-cols-1 gap-x-6 gap-y-2 sm:grid-cols-2">
          {rows.map(([label, value]) => (
            <div key={label} className="min-w-0">
              <dt className="text-xs text-on-surface-variant">{label}</dt>
              <dd className="break-words text-sm font-medium text-on-surface">{value}</dd>
            </div>
          ))}
        </dl>
        {log.detail && (
          <div className="mt-4">
            <p className="mb-1 text-xs text-on-surface-variant">Chi tiết</p>
            <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-all rounded-xl bg-surface-container p-3 text-xs text-on-surface">
              {log.detail}
            </pre>
          </div>
        )}
      </div>
    </div>
  );
}
